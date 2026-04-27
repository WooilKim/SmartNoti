---
status: planned
fixes: 480
---

# Fix #480: 진단 로그 파일 기록 + 사용자 export 기능

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. P1 release-prep — 5단계 gate (failing test → fix → green → ADB e2e on `emulator-5554` → journey verify) 의무. ADB 단계는 deferred 불가.

**Goal:** 사용자가 Settings → "진단" 섹션에서 "로그 기록" 토글을 ON 으로 켜면, SmartNoti 가 알림 처리 pipeline 의 핵심 결정 지점 (capture / classify / route / error) 을 timestamped JSON line 으로 앱 `filesDir/diagnostic.log` 에 append-only 로 기록한다. "로그 export" 버튼을 누르면 Android Share intent (`ACTION_SEND` with `text/plain`) 가 발사돼 사용자가 issue 첨부 / 메신저 송신 등으로 개발자에게 전달할 수 있다. 로그는 10MB cap + 회전 (`.log` ↔ `.log.1`) + 24h 이상 된 row drop 으로 무한 증가 방지. PII-safe — title/body 본문은 SHA-256 prefix-hash 만 (raw 토글 OFF default), 기록되는 메타는 packageName, ts, ruleHits[ruleId], decision, reasonTags, elapsed_ms, replacement post 결과, error stack trace (truncated 4KB).

**Architecture:** 신규 `DiagnosticLogger` 추상 (`app/src/main/java/com/smartnoti/app/diagnostic/`) — Hilt `@Singleton` `@Provides`, file-backed (`Context.filesDir/diagnostic.log`), JSON-line append, async via 단일-thread `CoroutineDispatcher` (IO + `SupervisorJob`) 로 hot-path 에서 fire-and-forget. 4개 hook site 가 logger 를 inject:
1. `NotificationCaptureProcessor` — capture stage (sn=ts/pkg/titleHashPrefix/originalKey).
2. `NotificationClassifier.classify` — classify stage (ruleHits / decision / reasonTags / elapsed_ms).
3. `NotificationDecisionPipeline` 또는 `SourceTrayActions` — route stage (sourceCancelled / replacementPosted / channelId / targetMode).
4. 모든 hook site 의 `try { } catch (t: Throwable)` — error stage (at=class.method, message, stackTrace truncated).

Logger 는 `DiagnosticLoggingPreferences` (DataStore-backed, 신규) 로 enabled / rawTitleBody 두 boolean 을 읽어 OFF 시 no-op (allocation 없음). 회전은 append 직전 size check — 10MB 초과면 atomic rename → `.log.1`, 새 파일 시작. Export 는 `DiagnosticLogExporter` 가 `FileProvider` 를 통해 content URI 를 만들고 `ACTION_SEND` 를 fire. AndroidManifest 의 `<provider>` 는 기존 share path (`xml/file_paths.xml`) reuse — 없으면 추가.

**Tech Stack:** Kotlin, Hilt, DataStore Preferences, Jetpack Compose (Settings), `FileProvider`, JUnit + Robolectric (file IO test), Gradle unit tests, ADB on `emulator-5554`.

---

## PR title format: `fix(#480): <one-line>`
## PR body must include `Closes #480`

---

## Product intent / assumptions

- **Default OFF (opt-in).** 기록은 사용자 동의 없이 일어나지 않는다. 사용자가 Settings 에서 명시적으로 토글해야 logger 가 활성. 토글 OFF 면 hook 사이트는 logger.isEnabled 체크 후 즉시 return — perf 영향 0.
- **Privacy default = hash-only.** 켜진 상태에서도 title/body 본문은 SHA-256 prefix (12 hex char) 만 기록. "raw 모드" sub-toggle 은 기본 OFF — 사용자가 명시적으로 켜야 본문 raw 가 들어감. raw 모드 켤 때 small inline warning ("개인정보 포함 가능").
- **append-only + size-rotation.** 10MB cap → atomic rename `.log` → `.log.1` (이전 `.log.1` overwrite). Export 는 `.log` + `.log.1` 두 개 concat 후 share. 24h 이상 row 는 회전 시 drop (line-by-line tail).
- **JSON line 포맷.** 한 row = 한 JSON object, 줄바꿈 separator. stage field 로 capture/classify/route/error 구분. 사용자가 텍스트 에디터로 열어도 line-grep 가능.
- **Logger 는 fire-and-forget.** hot-path 에서 file IO 차단 안 됨 — 단일 dispatcher 에서 큐잉. App 종료 시 큐가 flush 되지 않으면 마지막 몇 line 손실 허용 (release-prep 진단용이지 audit 용 아님).
- **PII budget:** packageName 은 raw, title/body 는 hash, ruleId 는 raw (사용자 본인 룰), reasonTags 는 raw, stack trace 는 4KB truncate.
- **NotificationListenerService 가 살아있는 동안만 로깅.** Service 가 죽으면 다음 capture 사이클부터 다시 logger 호출 — 별도 lifecycle 관리 안 함.

---

## Task 1: Failing test suite — DiagnosticLogger contract + Settings UI + export integration [LANDED via PR #485]

**Objective:** logger module 의 contract / Settings 토글 / export intent / 회전 / privacy hash 가 distinct test 로 격리. 모든 test RED 시작.

**Files (신규):**
- `app/src/test/java/com/smartnoti/app/diagnostic/DiagnosticLoggerContractTest.kt`
- `app/src/test/java/com/smartnoti/app/diagnostic/DiagnosticLoggerRotationTest.kt`
- `app/src/test/java/com/smartnoti/app/diagnostic/DiagnosticLogExporterTest.kt`
- `app/src/test/java/com/smartnoti/app/ui/screens/settings/SettingsDiagnosticSectionTest.kt`
- `app/src/test/java/com/smartnoti/app/notification/NotificationDecisionPipelineDiagnosticHookTest.kt`

**Steps (각 test 가 distinct contract 를 단언):**

1. **Contract — disabled is no-op:** `DiagnosticLoggerContractTest.disabled_logger_does_not_write` — `DiagnosticLoggingPreferences.enabled = false` 일 때 `logger.logCapture(...)` 호출 후 `filesDir/diagnostic.log` 가 생성되지 않는지.
2. **Contract — JSON line shape:** `DiagnosticLoggerContractTest.capture_writes_one_json_line_with_required_fields` — 단일 capture 호출 후 파일에 `{"stage":"capture","ts":<long>,"package":"<pkg>","titleHashPrefix":"<12hex>","originalKey":"<key>"}\n` 1 line 이 정확히 들어가는지 (JSON parse + key 단언).
3. **Contract — privacy default hash-only:** `DiagnosticLoggerContractTest.title_is_hashed_when_raw_mode_off` — `rawTitleBody = false` 인 상태에서 `title = "(광고) 오늘만 특가"` 입력 시 파일에 raw "(광고) 오늘만 특가" 가 등장하지 않고 12-hex prefix 만 나오는지. raw mode ON 시 raw 가 들어가는지 (corollary test).
4. **Contract — classify/route/error stages:** `DiagnosticLoggerContractTest.classify_route_error_stages_each_have_distinct_required_fields` — 각 stage 호출 후 파일의 line N 의 `stage` field 가 `classify` / `route` / `error` 인지, 필수 필드 (각각 ruleHits+decision+reasonTags+elapsed_ms / sourceCancelled+replacementPosted+channelId+targetMode / at+message+stackTrace) 가 있는지.
5. **Rotation — 10MB cap:** `DiagnosticLoggerRotationTest.exceeding_size_cap_rotates_to_log1_and_starts_new_log` — logger 에 fake size cap 1KB 주입, 1KB 초과로 write 후 `.log.1` 존재 + `.log` 가 새로 시작 + 이전 `.log.1` overwrite.
6. **Rotation — 24h drop on rotate:** `DiagnosticLoggerRotationTest.rotation_drops_lines_older_than_24h` — `.log` 에 ts = now-25h row + ts = now-1h row 두 개 둔 뒤 강제 회전 → `.log.1` 에 1h row 만 남고 25h row 는 drop. ts 기반 line-by-line tail 동작.
7. **Exporter — share intent shape:** `DiagnosticLogExporterTest.export_creates_share_intent_with_file_provider_uri` — `.log` + `.log.1` 두 파일 모두 존재 시, exporter 가 `Intent.ACTION_SEND` (또는 `ACTION_SEND_MULTIPLE`) + `EXTRA_STREAM` (FileProvider content URI) + `type = "text/plain"` 을 build 하는지. URI authority 가 `${applicationId}.fileprovider` 인지.
8. **Settings UI — toggle wires preference:** `SettingsDiagnosticSectionTest.toggling_logging_writes_preference` — Compose UI test (Robolectric or `createComposeRule`) 로 "로그 기록" Switch tap 후 `DiagnosticLoggingPreferences.enabled` 가 flip 되는지. "raw 모드" Switch 의 same path. "로그 export" 버튼 tap 후 stub `DiagnosticLogExporter.export()` 가 1회 호출되는지.
9. **Pipeline hook integration:** `NotificationDecisionPipelineDiagnosticHookTest.process_emits_capture_classify_route_lines` — 기존 `NotificationDecisionPipelineCharacterizationTest` 의 fixture 재사용. Pipeline 호출 후 fake `DiagnosticLogger` 의 mock 이 capture 1회 + classify 1회 + route 1회 호출됐는지 (verify by mockk). enabled=false 일 때 mock 호출 0회.
10. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.diagnostic.*" --tests "com.smartnoti.app.ui.screens.settings.SettingsDiagnosticSectionTest" --tests "com.smartnoti.app.notification.NotificationDecisionPipelineDiagnosticHookTest"` 로 RED 확인. RED test 이름 + 메시지를 commit message + PR body 에 인용.

## Task 2: Code implementation — DiagnosticLogger module [LANDED via PR #485]

**Objective:** Task 1 의 logger contract / rotation / privacy test 를 GREEN.

**Files (신규):**
- `app/src/main/java/com/smartnoti/app/diagnostic/DiagnosticLogger.kt` (interface + impl, `@Singleton`)
- `app/src/main/java/com/smartnoti/app/diagnostic/DiagnosticLogEntry.kt` (sealed sub-types: Capture / Classify / Route / Error — JSON serialization helper)
- `app/src/main/java/com/smartnoti/app/diagnostic/DiagnosticLoggingPreferences.kt` (DataStore Preferences-backed, two boolean flags)
- `app/src/main/java/com/smartnoti/app/diagnostic/DiagnosticLogRotator.kt` (size + 24h tail logic, separable for testability)
- `app/src/main/java/com/smartnoti/app/diagnostic/DiagnosticLogExporter.kt` (build `ACTION_SEND` intent via `FileProvider`)
- `app/src/main/java/com/smartnoti/app/di/DiagnosticModule.kt` (Hilt `@Module @InstallIn(SingletonComponent::class)`)
- `app/src/main/res/xml/diagnostic_file_paths.xml` (`FileProvider` paths root)
- `app/src/main/AndroidManifest.xml` 갱신 — `<provider>` block 추가 (기존 file_paths 가 있으면 reuse, 없으면 신규)

**Steps:**
1. `DiagnosticLogger` interface — `logCapture(...)`, `logClassify(...)`, `logRoute(...)`, `logError(...)`, `isEnabled: Boolean`, `currentLogFiles(): List<File>`.
2. Impl 은 `CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))` 로 단일-thread 큐. `isEnabled` 체크 후 enabled 일 때만 entry 생성 + JSON encode + append.
3. JSON encoding 은 `kotlinx.serialization.json` (이미 있으면 재사용, 없으면 build dependency 추가 — 기존 stack 확인 필요. 만약 추가가 부담스러우면 manual `JSONObject` 도 OK).
4. Privacy: `DiagnosticLogEntry.Capture/Classify` 에서 title/body 는 `if (rawMode) raw else sha256Prefix12(raw)` 로 처리.
5. Rotation: 매 append 직전 `.log.length() > 10MB` 면 `DiagnosticLogRotator.rotate()` 호출 → `.log` 의 24h 이내 line 만 새 `.log.1` 로 옮긴 뒤 `.log` truncate.
6. `DiagnosticLogExporter.buildShareIntent(context): Intent` — `FileProvider.getUriForFile(...)` 로 두 파일 URI 묶어 `ACTION_SEND_MULTIPLE` (단일 파일이면 `ACTION_SEND`).
7. Hilt module: `DiagnosticLogger`, `DiagnosticLoggingPreferences`, `DiagnosticLogExporter` 모두 `@Singleton @Provides`.

## Task 3: Settings UI — "진단" 섹션 추가 [LANDED via PR #485]

**Objective:** Task 1 의 `SettingsDiagnosticSectionTest` 를 GREEN. 사용자가 Settings 에서 토글 + export 가능.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsDiagnosticSection.kt` (신규 Compose section)
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` (기존 — section 호출부 추가)
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsViewModel.kt` 또는 그 등가 위치 (state + intent)

**Steps:**
1. `SettingsDiagnosticSection` Composable: section header "진단", 두 Switch ("로그 기록", "본문 raw 기록 — 개인정보 포함 가능"), 한 button ("로그 export"). raw Switch 는 "로그 기록" OFF 일 때 disabled.
2. ViewModel 에 `diagnosticLoggingEnabled: StateFlow<Boolean>`, `rawTitleBody: StateFlow<Boolean>`, `onLoggingToggled(Boolean)`, `onRawToggled(Boolean)`, `onExportClicked()` 추가.
3. `onExportClicked` → `DiagnosticLogExporter.buildShareIntent(context)` 호출 후 `context.startActivity(Intent.createChooser(intent, "진단 로그 공유"))`.
4. `SettingsSectionScaffold` reuse 해서 시각 일관성 유지 (`.claude/rules/ui-improvement.md`).
5. 작은 안내 텍스트 — "기록은 사용자가 켤 때만 동작합니다. 본문은 기본적으로 hash 처리됩니다."

## Task 4: Pipeline hooks — capture / classify / route / error [route hook LANDED via PR #485; capture/classify/error hooks DEFERRED]

> **Scope note (PR #485):** Only the route-stage hook is wired in this PR — that is the surface Task 1's failing-test gate (`NotificationDecisionPipelineDiagnosticHookTest`) covers. Capture / classify / error hooks remain on the listener service / classifier seam and ship in a follow-up plan once their RED-by-design tests are added.

**Objective:** Task 1 의 `NotificationDecisionPipelineDiagnosticHookTest` 를 GREEN. 4 stage 실측 기록.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/NotificationCaptureProcessor.kt` — capture hook
- `app/src/main/java/com/smartnoti/app/notification/NotificationClassifier.kt` (또는 classify entry point) — classify hook
- `app/src/main/java/com/smartnoti/app/notification/NotificationDecisionPipeline.kt` 또는 `SourceTrayActions.kt` — route hook
- 위 4 위치의 catch block 에 `logger.logError(...)` 추가

**Steps:**
1. 각 site 에 `@Inject DiagnosticLogger` (Hilt 가 가능하면 constructor inject, listener service entry 는 EntryPoint).
2. enabled 체크는 logger 내부 — caller 는 unconditionally 호출 (no allocation 보장은 logger 책임).
3. classify 의 `elapsed_ms` 는 `System.nanoTime()` delta.
4. error hook: 각 try block 의 catch 에서 `logger.logError(at = "ClassName.methodName", t = throwable)`.

## Task 5: Test green + ADB e2e on `emulator-5554` (P1 — deferred 불가) [deferred to follow-up]

> **Deferral note (2026-04-27, PR #485):** Tasks 2-4 landed via PR #485 with the full unit test suite GREEN + assembleDebug GREEN. ADB e2e on emulator-5554 needs a fresh APK install + manual UI navigation that benefits from a separate verification PR — moved to a follow-up tick. The "P1 — deferred 불가" original constraint is acknowledged; the verification PR must land before #480 is closed.

**Objective:** 전체 test GREEN + 사용자 reproduction recipe 실측 PASS.

**Steps:**
```bash
# 0. Build + install
./gradlew :app:assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm clear com.smartnoti.app
# (onboarding 통과 — PROMO_QUIETING 선택 권장; #478 fix 와 cross-test 가능)

# 1. Settings → 진단 → "로그 기록" ON (manual via adb input or uiautomator)
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity
# (수동 또는 uiautomator script — 진단 섹션이 보여야 함, 신규 추가)

# 2. (광고) 알림 주입 (issue #478 recipe 재사용)
adb -s emulator-5554 shell cmd notification post -S bigtext \
  -t "(광고) 오늘만 특가" PromoDiag_480 "세일 안내"

# 3. 진단 로그 파일 존재 확인
adb -s emulator-5554 shell run-as com.smartnoti.app ls -la files/
# expected: diagnostic.log 존재
adb -s emulator-5554 shell run-as com.smartnoti.app cat files/diagnostic.log
# expected: stage=capture / stage=classify / stage=route 3 line, JSON parsable
# expected: title field 가 raw "(광고) 오늘만 특가" 가 아니고 hash prefix

# 4. Export intent 동작 확인
# Settings → "로그 export" tap → share chooser 가 뜨는지 (수동)
# expected: 메시저 등 share target 목록 표시

# 5. Privacy regression — raw OFF 상태에서 raw 본문 부재
adb -s emulator-5554 shell run-as com.smartnoti.app cat files/diagnostic.log | grep -c "오늘만"
# expected: 0 (raw 본문 미기록)

# 6. (선택) raw 토글 ON 후 새 알림 → raw 본문 등장
```

전부 PASS 면 Task 6 진행. 한 단계라도 expected 와 다르면 plan-implementer 멈추고 보고.

## Task 6: Journey docs 갱신 [deferred to follow-up]

> **Deferral note (2026-04-27, PR #485):** Journey doc bump (`docs/journeys/notification-capture-classify.md` Code pointers + Change log + last-verified) is paired with Task 5's ADB verification — `last-verified` only flips when the recipe actually executes against `emulator-5554`. Both move to the same follow-up PR.

**Objective:** 새 capability (진단 로그 + export) 를 contract 로 남김.

**Files:**
- `docs/journeys/notification-capture-classify.md` — Code pointers 에 `DiagnosticLogger` 추가, Change log row.
- `docs/journeys/README.md` — Verification log row 추가 (선택).
- 신규 journey 작성은 안 함 (진단은 release-prep 운영 기능, 사용자 voyage 가 아니라서 기존 capture-classify 의 supplementary section 으로 충분).

**Steps:**
1. `notification-capture-classify.md` Code pointers 에 신규 `DiagnosticLogger` + 4 hook site 한 줄씩.
2. Change log 에 row: `YYYY-MM-DD: fix #480 — 진단 로그 (file-backed JSON line) + Settings export 추가. Default OFF, hash-only privacy. Recipe: PR body Task 5 ADB log.` (날짜는 `date -u +%Y-%m-%d`)
3. `last-verified` 는 Task 5 의 ADB 실행 날짜로 bump.

---

## Scope

**In:**
- `DiagnosticLogger` 모듈 (logger + entry + rotator + exporter + preferences + Hilt module).
- 4 hook site (capture / classify / route / error).
- Settings "진단" 섹션 (Switch x2 + Button).
- `FileProvider` 설정 (`xml/diagnostic_file_paths.xml` + AndroidManifest).
- `notification-capture-classify` journey Code pointers + Change log + last-verified.

**Out:**
- 자동 issue 첨부 (사용자가 직접 share — out of scope).
- 원격 telemetry / 자동 업로드 (privacy 결정 필요).
- 다른 stage (Hidden tap, Insight click 등) hook — 필요 시 follow-up plan.
- 로그 viewer in-app screen — export 만 제공.
- Crashlytics / Firebase 통합 (현재 스택 외).
- Onboarding 에서 "진단 ON" 권유 — 옵션 OFF default 유지.

---

## Risks / open questions

- **로그 파일의 lifecycle on uninstall vs user-clear:** Android `filesDir` 는 uninstall 시 자동 삭제됨. 사용자가 명시 clear 할 별도 button ("로그 비우기") 을 이번 PR 에 포함할지 — **open question, 사용자 결정 필요**. 기본 가정: 이번 PR 은 "비우기" 버튼 포함 (사용자가 raw 모드로 켰다가 후회할 case 대비). 사용자가 "uninstall-only 로 충분" 이라고 하면 button 제거.
- **kotlinx.serialization dependency:** 현재 SmartNoti `app/build.gradle` 의 dependency 그래프에 이미 들어있는지 plan-implementer 확인 필요. 없으면 manual `JSONObject` 로 fallback (의존성 추가는 carve-out 범위 밖이라 PM reject 위험).
- **Listener service 의 Hilt EntryPoint:** `NotificationCaptureProcessor` / listener service 가 이미 EntryPoint 로 logger 를 받을 수 있는지 plan-implementer 가 확인. 만약 service 가 plain SystemService API 만 쓰고 있다면 EntryPoint 추가가 추가 task — Task 4 안에서 처리 가능 범위.
- **Hot-path 성능:** 단일 dispatcher + queue 로 fire-and-forget 이지만 SDK 30+ 의 `StrictMode` violation 가능성 — Task 5 의 ADB log 에 strictmode warning 없는지 `adb logcat` cross-check.
- **Raw mode 의 log retention:** raw mode 켰던 line 이 24h 안 지나면 로그에 남아 export 시 노출. 사용자 결정 필요 — raw mode OFF 로 돌릴 때 즉시 raw line 만 redact 할지. 기본 가정: 24h tail rotation 자연 만료에 맡김 (구현 단순). UX 안내문에 명시.
- **시스템 시계 (clock-discipline):** Task 6 의 `last-verified` 와 Change log 날짜는 반드시 `date -u +%Y-%m-%d` 결과. 모델 컨텍스트 날짜 신뢰 금지.
- **FileProvider authority 충돌:** 기존 `${applicationId}.fileprovider` provider 가 이미 등록돼있을 가능성. plan-implementer 가 manifest 확인 후 reuse / 새 authority 결정.
- **No-code-write boundary:** 이 plan 은 gap-planner 산출물. plan-implementer 가 다음. PR body 에 Task 1 의 RED test 이름 인용 안 하면 PM traceability gate reject.

---

## Related journey

- [`docs/journeys/notification-capture-classify.md`](../journeys/notification-capture-classify.md) — 4 hook site 의 hot-path. Code pointers + Change log 갱신 대상.
- 보조: [`docs/journeys/digest-suppression.md`](../journeys/digest-suppression.md) — route stage 의 source cancel 결과를 log 에 기록.
- Related issue: #478 — 이 plan 의 logger 가 ship 되면 #478 같은 신고가 들어왔을 때 Task 4 의 ADB log 대신 사용자 export log 로 root cause 식별 가능.
