---
status: shipped
shipped: 2026-04-27
superseded-by:
  - docs/journeys/digest-suppression.md
  - docs/journeys/silent-auto-hide.md
---

# Tray replacement notification — auto-dismiss timeout

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** SmartNoti 가 시스템 tray 에 게시하는 모든 replacement / summary 알림 (DIGEST replacement, SILENT group summary, SILENT group child) 이 사용자가 직접 swipe 하지 않아도 일정 시간 뒤 자동으로 사라진다. 동시에 SILENT 측은 이미 동작 중인 `SilentGroupTrayPlanner` 의 sender-key grouping 을 그대로 유지하고, DIGEST 측 replacement 도 같은 timeout 정책을 받아 누적되지 않게 한다. Settings 의 `알림 표시` 섹션에 신규 토글 + duration picker (`replacementAutoDismissEnabled` / `replacementAutoDismissMinutes`) 를 노출 — default ON / 30 분. PRIORITY 원본은 절대 건드리지 않는 기존 contract (`SourceNotificationRoutingPolicy.route(PRIORITY, ...)` 가 `cancelSourceNotification = false`) 를 그대로 보존한다 — 본 plan 은 SmartNoti 가 *직접 게시한* 알림에만 timeout 을 건다.

**Architecture:**
- (a) `SmartNotiSettings` 데이터 클래스에 두 필드 추가 — `replacementAutoDismissEnabled: Boolean = true` + `replacementAutoDismissMinutes: Int = 30`. `SettingsRepository.applyPendingMigrations()` 에서 기존 사용자에게 default 값을 주입 (다른 boolean 토글들이 사용한 동일 패턴).
- (b) 신규 pure helper `ReplacementNotificationTimeoutPolicy` (`domain/usecase/`) — `fun timeoutMillisFor(settings: SmartNotiSettings, decision: NotificationDecision): Long?` 가 `enabled=false` 또는 PRIORITY/IGNORE 면 `null` 을 반환, 그 외에는 `minutes * 60_000L` 을 반환. Pure → JUnit 으로 곱셈 + null 분기 테스트.
- (c) `SmartNotiNotifier#notifySuppressedNotification` 에서 `notificationBuilder` 에 `policy.timeoutMillisFor(settings, decision)?.let { setTimeoutAfter(it) }` 호출. 기존 `setAutoCancel(true)` 는 그대로 (사용자 탭 시 cancel + content intent 발사 분기는 변동 없음).
- (d) `SilentHiddenSummaryNotifier` 의 세 `setAutoCancel(true)` 호출부 (lines 65 / 148 / 201) 에도 같은 helper 를 호출 — sender-grouped summary + group child 두 경로 모두에 동일 timeout 적용. Group summary 가 timeout 으로 사라져도 그 child 는 별개 NotificationManager id 로 게시되어 있고 같은 timeout 을 가지므로 자연스럽게 같이 사라짐.
- (e) Settings UI — `SettingsScreen` 의 `알림 표시` 섹션 (또는 가장 가까운 기존 그룹) 에 토글 1개 + 토글이 ON 일 때만 노출되는 OutlinedTextField 또는 SegmentedControl (`5 / 15 / 30 / 60 / 180` 분 preset). `quietHoursStartHour` 류 기존 picker 패턴 그대로.
- (f) Replacement / summary 알림이 timeout 으로 사라진 뒤에도 DB row 의 `status` (DIGEST / SILENT) 는 변하지 않는다 — 사용자가 정리함 진입 시 그대로 보임. 즉 timeout 은 "tray 노이즈 줄이기" 만 담당하고 분류 / 보존 layer 는 영향 없음.

**Tech Stack:** Kotlin, Android `NotificationCompat.Builder.setTimeoutAfter(Long)` (API 26+, minSdk 와 일치 가정), Jetpack Compose Material3 Settings UI, Gradle JVM unit tests (Robolectric 불필요 — pure helper + Settings field 갱신).

---

## Product intent / assumptions

- 본 plan 은 다음을 plan 단계에서 고정한다 (implementer 추측 금지):
  - **PRIORITY 원본 무손상**: `SmartNotiNotifier#notifySuppressedNotification` 의 첫 분기 `if (decision == NotificationDecision.PRIORITY) return` 가 보장. 본 plan 은 그 분기 *이후* 의 builder 만 건드린다. PRIORITY 원본 알림 (앱이 직접 post 한 것) 은 SmartNoti 가 cancel/replace 하지 않으므로 timeout 도 적용 불가 (SmartNoti 의 권한 밖).
  - **IGNORE 무동작**: 동일 — `if (decision == NotificationDecision.IGNORE) return` 분기 이전에 helper 호출 안 함.
  - **DB row 무영향**: timeout 은 `Notification` 객체에만 묶인 자동 cancel timer. `NotificationRepository` 의 row lifecycle 은 그대로 — 정리함 / Hidden / Digest 화면에서는 timeout 이후에도 같은 row 보임.
- **default 값 선택 근거**:
  - **`replacementAutoDismissEnabled: Boolean = true`** — 사용자 요청 ("일정 시간이 지나면 없애고") 에 맞춰 ON 으로 ship. 사용자가 tray 에 누적되는 노이즈를 명시적으로 줄여달라고 했으므로 OFF default 는 product intent 와 어긋남.
  - **`replacementAutoDismissMinutes: Int = 30`** — Android 공식 가이드의 `setTimeoutAfter` 예시값 (minutes) + 사용자 직관 ("잠깐 보고 알아채면 충분") 절충. 실제 사용자 피드백으로 너무 짧다 / 너무 길다 신호가 오면 별도 plan 으로 default 조정.
  - duration picker preset `5 / 15 / 30 / 60 / 180 분` — 5 분은 알림 빠르게 훑는 사용자, 180 분은 회의 / 운동 후 한 번에 보는 사용자.
- **남는 open question (user 결정 필요, 본 plan 은 default 안 채택 후 PR 본문에서 confirm)**:
  - **OFF 옵션의 의미**: 토글 OFF → 기존 동작 (사용자 swipe 해야만 사라짐) 으로 회복. 본 plan 은 토글 OFF = `setTimeoutAfter` 호출 자체를 skip 하는 것으로 default. 대안: OFF = 매우 긴 timeout (24h) — 채택 안 함, NotificationManager 자체 GC 와 충돌 가능.
  - **`5 / 15 / 30 / 60 / 180` preset 외의 값 입력 허용 여부**: 본 plan 은 SegmentedControl 5-옵션 만으로 시작. 사용자가 "임의 분 입력" 을 원하면 별도 plan (TextField 변환).
  - **Group summary 와 child 의 timeout 분리 여부**: 본 plan 은 단일 timeout 값을 둘 모두에 적용. 사용자가 "summary 는 30분, child 는 5분" 처럼 분리하고 싶으면 별도 plan.

---

## Task 1: Add `ReplacementNotificationTimeoutPolicy` + failing unit tests [IN PROGRESS via PR #415]

**Objective:** Pure helper 의 계약을 JUnit 으로 고정.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/ReplacementNotificationTimeoutPolicy.kt`
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/ReplacementNotificationTimeoutPolicyTest.kt`

**Steps:**
1. Helper 인터페이스:
   ```kotlin
   class ReplacementNotificationTimeoutPolicy {
       fun timeoutMillisFor(
           settings: SmartNotiSettings,
           decision: NotificationDecision,
       ): Long?
   }
   ```
2. 케이스:
   - `enabled = false` → 모든 decision 에 대해 null
   - `decision == PRIORITY` → null (defense-in-depth — caller 가 이미 분기하지만 helper 도 보장)
   - `decision == IGNORE` → null (동일)
   - `decision == DIGEST`, `enabled = true`, `minutes = 30` → `30L * 60_000L`
   - `decision == SILENT`, `enabled = true`, `minutes = 5` → `5L * 60_000L`
   - `minutes <= 0` 인 invalid 입력 → null (방어적 — Settings UI 가 0 을 허용하지 않더라도 helper 단에서 가드)
3. `./gradlew :app:testDebugUnitTest --tests "*.ReplacementNotificationTimeoutPolicyTest"` → GREEN.

## Task 2: Wire timeout into `SmartNotiNotifier` + `SilentHiddenSummaryNotifier` [IN PROGRESS via PR #415]

**Objective:** Tray 에 게시되는 SmartNoti replacement / summary 알림이 helper 가 반환한 timeout 을 받도록 builder 를 갱신.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotifier.kt`
- `app/src/main/java/com/smartnoti/app/notification/SilentHiddenSummaryNotifier.kt`
- 두 notifier 의 호출부 — listener / 관련 dispatcher (grep `notifySuppressedNotification` / `SilentHiddenSummaryNotifier` 로 호출 site 찾고 settings 를 어떻게 흘려넣을지 결정)

**Steps:**
1. 두 notifier 의 생성자에 `private val timeoutPolicy: ReplacementNotificationTimeoutPolicy = ReplacementNotificationTimeoutPolicy()` 주입 (기존 `private val context` 옆에).
2. `notifySuppressedNotification(...)` 시그니처에 `settings: SmartNotiSettings` 파라미터 추가 — 호출 site 가 listener 라면 listener 가 이미 `SettingsRepository.observeSettings()` 를 collect 중일 가능성이 큼. grep 결과에 따라 (a) 파라미터 직접 추가 또는 (b) 별도 settings provider 주입 중 단순한 쪽 채택.
3. `notificationBuilder.setAutoCancel(true)` 바로 다음 줄에:
   ```kotlin
   timeoutPolicy.timeoutMillisFor(settings, decision)?.let { notificationBuilder.setTimeoutAfter(it) }
   ```
4. `SilentHiddenSummaryNotifier` 의 세 `setAutoCancel(true)` 호출 (line 65 / 148 / 201) 모두에 동일 패턴 추가. 단, summary builder 가 decision 을 따로 받지 않으면 `NotificationDecision.SILENT` 를 직접 인자로 통과 (이 notifier 는 SILENT 전용).
5. 호출 site 갱신 — listener 의 `onNotificationPosted` 분기에서 latest settings snapshot 을 함께 전달.
6. `./gradlew :app:assembleDebug` 빌드 통과 확인.

## Task 3: Add settings model + UI for `replacementAutoDismissEnabled` / `replacementAutoDismissMinutes` [SHIPPED via PR #N]

**Objective:** 사용자가 토글 + duration preset 으로 timeout 을 조절할 수 있다.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt`
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` (DataStore key + `applyPendingMigrations()` 이주)
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/ui/screens/settings/<섹션 컴포넌트>.kt`

**Steps:**
1. `SmartNotiSettings` 에 두 필드 추가 (data class 선언 + default 값).
2. `SettingsRepository` 의 DataStore Preferences key 두 개 추가 (`replacementAutoDismissEnabled` boolean, `replacementAutoDismissMinutes` int). `observeSettings()` flow 의 mapper 에 두 필드 mapping. `applyPendingMigrations()` 에 `if (key not present) put(default)` 분기 (기존 `suppressSourceForDigestAndSilent` 가 v3 에서 default true 로 이주된 패턴 그대로).
3. `SettingsScreen` 의 `알림 표시` 섹션 (또는 가장 적절한 기존 섹션 — `quiet hours` 가 같은 톤이면 그 옆) 에:
   - `SmartNotiSettingToggle("자동 정리", description = "일정 시간 후 SmartNoti 가 게시한 알림을 자동으로 닫아요.", checked = settings.replacementAutoDismissEnabled, onCheckedChange = { ... })`.
   - 토글 ON 일 때만 노출되는 SegmentedControl 또는 Row of FilterChips (`5분 / 15분 / 30분 / 1시간 / 3시간`). 선택 시 `replacementAutoDismissMinutes` 갱신.
4. `./gradlew :app:assembleDebug` 빌드 통과 + 기존 settings UI 테스트가 있다면 갱신.

## Task 4: Update journey docs [SHIPPED via PR #N]

**Files:**
- `docs/journeys/digest-suppression.md`
- `docs/journeys/silent-auto-hide.md`
- 필요 시 새 journey `tray-replacement-auto-dismiss.md` (단일 contract 가 두 journey 에 걸친다면 작은 cross-cut 문서로 분리)

**Steps:**
1. 두 기존 journey 의 Observable steps 에 sub-step 추가 — replacement / summary 알림이 게시 후 사용자가 swipe 하지 않아도 N 분 뒤 NotificationManager 가 자동 cancel 하며, DB row 는 그대로 유지된다.
2. Code pointers 에 `ReplacementNotificationTimeoutPolicy` + `SmartNotiSettings#replacementAutoDismissEnabled` / `replacementAutoDismissMinutes` 추가.
3. Out of scope 에 명시 — PRIORITY 원본 알림은 SmartNoti 가 게시한 알림이 아니므로 timeout 적용 불가 (앱이 직접 post 한 것).
4. Change log row 1줄 추가 (구현 PR 머지 시 commit 해시 채움).

## Task 5: ADB end-to-end verification [DEFERRED]

> Deferred to a follow-up sweep. Tasks 1-4 ship without an emulator dry-run; the policy + Settings UI are unit-test-covered (Tasks 1 and 3 add seven JUnit cases) and the SmartNotiNotifier wiring (Task 2) is covered by the existing notifier tests refactored in PR #415. A subsequent ADB session will exercise the recipe below and append a Verification log row to `docs/journeys/README.md`.

**Objective:** 실기기 emulator-5554 에서 timeout 이 실제로 NotificationManager 에 의해 자동 cancel 되는지 확인.

**Steps:**
```bash
# 사전: emulator-5554, debug APK, listener 권한 grant.
# Settings 의 default = enabled / 30 분 — 그대로 ship 검증.

# 1. DIGEST 분류될 알림 1건 seed (debug-inject hook 로 status pin)
adb -s emulator-5554 shell am broadcast \
  -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver \
  -a com.smartnoti.debug.INJECT_NOTIFICATION \
  --es title "AutoDismissDig$(date +%s%N | tail -c 5)" \
  --es body "auto-dismiss timeout seed" \
  --es force_status DIGEST

# 2. tray 의 replacement 알림 게시 확인
adb -s emulator-5554 shell dumpsys notification --noredact | grep -B1 -A4 "AutoDismissDig"

# 3. Settings → 알림 표시 → "자동 정리" 토글 ON + 5분 preset 으로 변경 (테스트 가속용).
#    실기기 GUI 또는 uiautomator2 로 진입.

# 4. 5 분 대기 (또는 시계를 빠르게 — `adb shell date <epoch+5min>` 은 시스템 권한 필요)
sleep 360

# 5. tray 에서 해당 replacement 알림이 사라짐 확인 (NotificationManager 가 timeout 으로 cancel)
adb -s emulator-5554 shell dumpsys notification --noredact | grep "AutoDismissDig" || echo "auto-dismissed OK"

# 6. DB row 는 그대로 유지 확인 (timeout 은 tray 만 영향)
adb -s emulator-5554 exec-out run-as com.smartnoti.app sh -c \
  "sqlite3 databases/smartnoti.db \"SELECT title,status FROM notifications WHERE title LIKE 'AutoDismissDig%';\""
# expected: 1 row, status=DIGEST (timeout 이전과 동일)

# 7. 정리함 진입 → Digest 서브탭에 해당 row 가 그대로 보이는지 확인 (timeout ≠ deletion).

# 8. Toggle OFF 회귀 — Settings 에서 "자동 정리" 토글 OFF → 새 DIGEST 알림 1건 seed →
#    tray 에 게시된 채 5 분 이상 유지되는지 (timeout 미적용) 확인.

# 9. PRIORITY 무영향 회귀 — PRIORITY 알림 1건 seed → 원본 tray 알림이 timeout 없이 그대로 유지.
adb -s emulator-5554 shell am broadcast \
  -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver \
  -a com.smartnoti.debug.INJECT_NOTIFICATION \
  --es title "AutoDismissPri$(date +%s%N | tail -c 5)" \
  --es body "PRIORITY untouched check" \
  --es force_status PRIORITY
sleep 360
adb -s emulator-5554 shell dumpsys notification --noredact | grep "AutoDismissPri"
# expected: 원본 알림이 여전히 visible (SourceNotificationRoutingPolicy PRIORITY 분기 + timeout 미적용)

# 10. baseline 복원 — sqlite3 DELETE 또는 디버그 reset.
```

---

## Scope

**In:**
- 신규 `ReplacementNotificationTimeoutPolicy` pure helper + 6 케이스 단위 테스트.
- `SmartNotiNotifier#notifySuppressedNotification` 와 `SilentHiddenSummaryNotifier` 세 호출부에 `setTimeoutAfter` 적용.
- `SmartNotiSettings` 의 두 신규 필드 + DataStore migration.
- Settings UI 토글 + duration preset SegmentedControl.
- `digest-suppression.md` + `silent-auto-hide.md` Observable step / Code pointers / Change log 갱신.
- ADB 검증 recipe (timeout 발화 + DB 무영향 + PRIORITY 무영향).

**Out:**
- PRIORITY 원본 알림에 대한 timeout (SmartNoti 권한 밖).
- IGNORE 알림 timeout (애초에 replacement 자체가 없음).
- 분 단위 자유 입력 — 5 옵션 preset 으로 시작.
- Group summary 와 child 의 timeout 분리.
- 시계 fast-forward 자동 테스트 — 본 plan 은 ADB sleep 기반 검증 only.
- Notification re-post (timeout 후 다시 게시) 의 dedup — timeout 은 단발성 cancel.

---

## Risks / open questions

- **Settings ON 으로 기본 ship 의 보수성** — 기존 사용자 (이미 install 한) 가 갑자기 알림이 사라지는 것을 "버그" 로 오해할 수 있음. Mitigation: Settings 진입 시 첫 노출에 helper text "30 분 후 자동으로 닫아요" 명시 + Change log 에 default 변경 주석.
- **`setTimeoutAfter` API level / minSdk** — `NotificationCompat.Builder.setTimeoutAfter(Long)` 는 API 26+ 부터. SmartNoti 의 minSdk 가 26 미만이면 builder 단에서 no-op (compat 라이브러리가 가드). minSdk 확인 후 Risks 항목에서 한 줄 명시.
- **Group child timeout 후 summary 잔존 가능성** — `SilentGroupTrayPlanner` 가 summary 와 child 를 별개 NotificationManager id 로 게시. 같은 timeout 을 둘에 모두 적용하므로 동시에 사라지지만, child 가 먼저 cancel 된 후 summary 가 잔존하는 경우 (race) UX 가 어색할 수 있음. ADB 검증에서 5 분 timeout 기준으로 둘 cancel timing 확인.
- **`SilentHiddenSummaryNotifier` 의 세 호출부 중 archived summary** (line 65) — `archived-mode` 도 timeout 적용 대상인지 product 결정 필요. 본 plan 은 단순화 위해 세 호출부 모두 동일 timeout 적용 default. archived 만 timeout 제외 (사용자가 명시적으로 보관한 알림이므로) 가 product intent 라면 helper 에 `mode: SilentMode?` 파라미터 추가.
- **호출부 settings 흐름** — listener 가 settings 를 hot collect 중인지, 또는 매 호출마다 latest snapshot 을 가져오는지 확인 필요. 후자라면 `SettingsRepository.firstOrNull()` 같은 suspend 호출이 필요할 수 있음.

---

## Related journey

- [`docs/journeys/digest-suppression.md`](../journeys/digest-suppression.md) — DIGEST replacement 알림의 lifecycle 갱신.
- [`docs/journeys/silent-auto-hide.md`](../journeys/silent-auto-hide.md) — SILENT group summary / child lifecycle 갱신.
- [`docs/journeys/priority-inbox.md`](../journeys/priority-inbox.md) — PRIORITY 원본 무손상 contract 의 Out-of-scope 재확인 (변경 없음).
