---
status: shipped
fixes: 524
priority: P1
last-updated: 2026-04-28
superseded-by:
  - docs/journeys/silent-auto-hide.md
  - docs/journeys/digest-suppression.md
  - docs/journeys/protected-source-notifications.md
---

# Fix #524 — Settings 트레이 정리 버튼 (pre-fix source 알림 일괄 cancel)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Source issue: https://github.com/WooilKim/SmartNoti/issues/524 (P1 release-prep, completes the value chain of #511's forward-only fix). Loop is in release-prep / issue-driven mode per merged meta-plan #479 — keep the surface tight.

**Goal:** 사용자가 Settings → "트레이 정리" 카드의 "모두 정리하기" 버튼을 한 번 탭하면, SmartNoti 의 `smartnoti_silent_group_app:*` 그룹들이 가리키는 source packageName 들의 트레이 잔존 알림 (#511 fix 가 적용되기 전 시점에 게시된 ~100+개) 이 단일 패스로 cancel 된다. R3CY2058DLJ 의 현재 상태 (198 active = SmartNoti silent_group 49 + source 100+) → cleanup 후 SmartNoti silent_group 49 + PERSISTENT_PROTECTED 일부만 남고 source 0 에 가깝게 정돈된다. `MigrateOrphanedSourceCancellationRunner` (#511) 가 못 잡은 cohort — `replacementNotificationIssued = 0` 으로 저장된 row 들 — 를 사용자 의지로 한 번 정리.

**Architecture:**
- 신규 `TrayOrphanCleanupRunner` (port + impl 쌍, `AppLabelResolver` / `MigrateOrphanedSourceCancellationRunner` 패턴 mirror) 가 `data.local` 에 들어간다. `ActiveTrayInspector` (read-only port; production impl 은 `SmartNotiNotificationListenerService.activeNotifications` 를 wrap) + 기존 `SourceCancellationGateway` 를 주입받아 작동.
- 식별 알고리즘: 활성 `StatusBarNotification` 한 번 순회 → (a) `groupKey` 가 `smartnoti_silent_group_app:` 으로 시작하거나 SmartNoti 자기 패키지에서 게시된 entry 는 보존, (b) 그 외 entry 중 `smartnoti_silent_group_app:<pkg>` group 의 `<pkg>` 로 추출된 packageName set 에 속하면 cancel 후보, (c) `Notification.flags & (FLAG_FOREGROUND_SERVICE | FLAG_NO_CLEAR | FLAG_ONGOING_EVENT) != 0` 인 entry 는 PERSISTENT_PROTECTED 로 간주하고 skip. 단일 pass 라 100+ 항목도 ms 단위.
- UI 는 신규 `SettingsTrayCleanupSection` Composable (`ui/screens/settings/`) — preview count + 미리보기 토글 (default ON) + "모두 정리하기" 버튼 + confirm dialog. 기존 `SuppressionManagementCard` 바로 아래에 itemize 해 넣어 thematic grouping 유지. 한국어 라벨, 다른 카드와 동일한 `SmartSurfaceCard` 컨테이너로 visual rhythm 일관.
- Cancel 경로는 #511 의 `SourceCancellationGateway` → `SmartNotiNotificationListenerService.cancelSourceEntryIfConnected(...)` 그대로 재사용 — 신규 cancel API 추가 없음. Listener 가 bind 안 돼 있으면 runner 가 0 cancel + UI 토스트로 surface ("트레이 정리는 알림 권한이 활성일 때만 가능해요").
- DB / 마이그레이션 / DataStore flag 변경 없음. 1회성이지만 사용자 trigger 라 idempotent (재탭 시 다시 같은 알고리즘 실행 — 새로 쌓인 source orphans 도 청소 가능).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), AndroidX `NotificationListenerService`, JUnit + MockK (unit), ADB e2e on R3CY2058DLJ.

---

## Product intent / assumptions

- **#511 의 forward-only fix 만으로는 사용자가 가치 체감 불가능.** 디바이스 dump 분석에서 100+개 source orphan 이 가시화돼 있고, 이들은 자연 소멸하지 않는다 (사용자가 알림 하나하나 swipe 해야 함 → 기능 실패로 인지). 본 plan 은 #511 의 가치 노출에 필수.
- **One-tap cleanup 은 사용자가 원하는 affordance.** Issue body 의 사용자 제안 그대로 — Settings 안의 명시적 버튼. 자동 (백그라운드 sweep) 으로 만들지 않는다 — 사용자가 "내가 청소했다" 는 mental model 을 갖게 해야 trust 형성.
- **PERSISTENT_PROTECTED 는 절대 cancel 시도하지 않는다.** 음악 플레이어 (`FLAG_NO_CLEAR`) / 통화 / 내비 / 다운로드 (`FLAG_FOREGROUND_SERVICE`) 의 알림이 끊기면 서비스 자체가 죽거나 사용자 작업 중단. [protected-source-notifications](../../docs/journeys/protected-source-notifications.md) journey 의 보호 계약을 cleanup 경로도 똑같이 따른다.
- **Preview ON (default).** "정리될 알림 미리보기" 토글이 켜진 상태에서 카드 본문에 "원본 알림 N건 정리 가능 (앱 X, Y, Z…)" 한 줄. 0건이면 "정리할 원본 알림이 없어요" 로 surface — 빈 상태도 명확히 보여줘서 "정상 작동 중" 신호.
- **Confirm dialog by default.** 100+ 개를 한 번에 cancel 하는 작업이라 한 단계 가드. "다시 묻지 않기" 토글 (DataStore key `tray_cleanup_skip_confirm_v1`) 로 power user 는 1탭 가능. 본 plan 의 default 는 confirm — Open Question 으로 사용자 검증 받을 항목.
- **Open question for user (Risks 절에 명시):** 사용자가 "이 앱은 cleanup 대상에서 제외" 화이트리스트를 원할 가능성. v1 은 하지 않음 — UI 복잡도 / Settings 누적 vs. cleanup 의 즉시 가치 trade-off 에서 후자 우선. 신고 누적 시 v2 plan 으로 분리.

---

## Task 1: Failing test — `TrayOrphanCleanupRunnerTest` `[IN PROGRESS via PR #530]`

**Objective:** Issue body 의 정확한 fixture (active source 5 + SmartNoti silent_group 5, 1개는 PERSISTENT_PROTECTED) 가 들어왔을 때 source 4개에 대해 `SourceCancellationGateway.cancel(sourceKey)` 가 호출되고 PERSISTENT_PROTECTED 1개는 skip 되는지 RED 상태로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/data/local/TrayOrphanCleanupRunnerTest.kt`

**Steps:**
1. `FakeActiveTrayInspector` — production `ActiveTrayInspector` 의 fake. `listActive(): List<ActiveTrayEntry>` 반환. `ActiveTrayEntry` 는 `(key, packageName, groupKey, flags)` data class.
2. `FakeSourceCancellationGateway` — `SourceCancellationGateway` 의 fake. `cancelled: List<String>` accumulator.
3. Fixture A (`fresh_cleanup_cancels_orphan_sources_and_skips_protected`):
   - `silent_group_app:com.naver.android.search` (SmartNoti 자기 패키지, groupKey `smartnoti_silent_group_app:com.naver.android.search`) × 1
   - `silent_group_app:com.coupang.eats` × 1, ..., 총 5 개의 SmartNoti 그룹
   - `com.naver.android.search` source × 1, `com.coupang.eats` source × 1, ..., 총 5 개의 source orphan
   - 5번째 source 는 `com.spotify.music` 이고 `flags = FLAG_NO_CLEAR | FLAG_ONGOING_EVENT` (PERSISTENT_PROTECTED 시뮬)
   - SmartNoti 그룹은 항상 보존 (cancel 호출되면 안됨)
   - Run runner.cleanup() → result.cancelledCount == 4, result.skippedProtectedCount == 1, fakeGateway.cancelled 에 protected 제외 4 개 sourceKey
4. Fixture B (`listener_not_bound_returns_zero_no_calls`): `inspector.isListenerBound() == false` → result.notBound, gateway 호출 0회.
5. Fixture C (`empty_tray_returns_zero`): active 가 비어있으면 result.cancelledCount == 0.
6. Fixture D (`preview_does_not_cancel`): runner.preview() 는 동일 식별 로직을 돌리지만 gateway 호출 0회. `PreviewResult(candidateCount, candidatePackageNames)` 반환.
7. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.local.TrayOrphanCleanupRunnerTest"` 로 RED 확인.

## Task 2: Implement `TrayOrphanCleanupRunner` + ports `[IN PROGRESS via PR #530]`

**Objective:** Task 1 의 4 개 fixture 를 GREEN. 단일 활성 알림 pass 로 식별 + cancel.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/data/local/ActiveTrayInspector.kt`
- 신규: `app/src/main/java/com/smartnoti/app/data/local/TrayOrphanCleanupRunner.kt`
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` — `activeTrayEntriesSnapshotIfConnected(): List<ActiveTrayEntry>?` static helper 추가 (`activeSourceKeysSnapshotIfConnected` 패턴 mirror).

**Steps:**
1. `ActiveTrayEntry(key: String, packageName: String, groupKey: String?, flags: Int)` data class.
2. `interface ActiveTrayInspector { fun isListenerBound(): Boolean; fun listActive(): List<ActiveTrayEntry> }` + `ListenerActiveTrayInspector` impl 이 listener static helper 호출.
3. `TrayOrphanCleanupRunner(inspector: ActiveTrayInspector, gateway: SourceCancellationGateway)`:
   - `data class PreviewResult(val candidateCount: Int, val candidatePackageNames: List<String>)`
   - `data class CleanupResult(val cancelledCount: Int, val skippedProtectedCount: Int, val notBound: Boolean)`
   - `fun preview(): PreviewResult` — 식별 로직만 실행, candidate sourceKey 의 packageName 을 dedupe 해 `List<String>` 반환 (가장 많이 등장한 N=5 만 surface, 나머지는 "외 K개" UI 측 처리).
   - `suspend fun cleanup(): CleanupResult` — `isListenerBound() == false` 면 `notBound = true` 즉시 반환. else preview 와 동일 식별 → 각 candidate 마다 `gateway.cancel(key)`, protected 는 `skippedProtectedCount` 만 증가.
4. 식별 알고리즘 (단일 pass, O(N)):
   - `entries = inspector.listActive()`
   - `smartNotiOrphanPackages: Set<String> = entries.filter { it.groupKey?.startsWith("smartnoti_silent_group_app:") == true }.mapNotNullTo(HashSet()) { it.groupKey?.substringAfter("smartnoti_silent_group_app:") }`
   - `candidates = entries.filter { it.packageName in smartNotiOrphanPackages && it.packageName != BuildConfig.APPLICATION_ID }`
   - `protectedFlags = Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT`
   - `protected = candidates.filter { (it.flags and protectedFlags) != 0 }`
   - `cancellable = candidates - protected`
5. `companion object { fun create(context: Context): TrayOrphanCleanupRunner }` — production wiring with `ListenerActiveTrayInspector` + `ListenerSourceCancellationGateway`.
6. Task 1 GREEN 확인. 전체 unit suite (`./gradlew :app:testDebugUnitTest`) 회귀 없음.

## Task 3: `SettingsTrayCleanupSection` Composable + preview state `[IN PROGRESS via PR #530]`

**Objective:** Settings 화면에 새 카드 — preview count + 미리보기 토글 + 버튼 + confirm dialog. 다른 카드와 동일한 visual rhythm.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsTrayCleanupSection.kt` (Composable + state holder)
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/settings/SettingsTrayCleanupSummaryBuilderTest.kt` — preview count 포맷 ("원본 알림 4건 정리 가능 (네이버, 쿠팡이츠, 카카오톡 외 1개)" / "정리할 원본 알림이 없어요" / "알림 권한 활성 후 다시 시도해 주세요") 의 분기 단위 테스트.

**Steps:**
1. `SettingsTrayCleanupSummaryBuilder` (pure builder, testable) 가 `(PreviewResult, isListenerBound, appLabelResolver)` → user-facing 한 줄 텍스트 반환. Test fixture 4개 (정리 가능 / 0건 / not bound / N>3 truncation).
2. `SettingsTrayCleanupSection` Composable:
   - `SmartSurfaceCard` 컨테이너 (다른 settings 카드와 일치)
   - 헤더: "트레이 정리"
   - sub-text: builder 결과 한 줄
   - `SettingsToggleRow`: "정리 전 미리보기" (default ON) — 상태는 `remember { mutableStateOf(true) }` (현재 화면 lifetime 만, 영속 안 함)
   - 1차 버튼: "모두 정리하기" — preview ON 이고 candidateCount > 0 일 때 활성, 아니면 disabled
   - confirm dialog: `AlertDialog` ("원본 알림 N건을 정리할까요?" + "다시 묻지 않기" 체크박스 + 확인/취소). 확인 시 `onCleanupConfirmed` 콜백.
   - "다시 묻지 않기" 체크 시 confirm dialog 를 다음부터 skip (DataStore key `tray_cleanup_skip_confirm_v1: Boolean`, default false). Toggle 자체는 별도 Settings 노출 없음 — 다이얼로그 안에서만 set.
3. preview 자체는 화면 진입 / "다시 시도" 시 launch 하는 LaunchedEffect 에서 `runner.preview()` 호출 + state hoisting.
4. Cleanup 실행 시 IO scope 에서 `runner.cleanup()`, 결과 토스트 ("N건 정리됨" / "정리할 알림이 없었어요" / "권한이 비활성이에요"). 토스트 후 LaunchedEffect 가 preview 를 갱신해 "0건" 으로 떨어지는 것을 사용자가 즉시 보게.
5. 신규 settings 키 (`tray_cleanup_skip_confirm_v1`) 는 `SettingsRepository` 의 기존 prefix 패턴 따라 추가 — 기본 false, no migration needed (boolean default).

## Task 4: Wire `SettingsTrayCleanupSection` into `SettingsScreen` `[IN PROGRESS via PR #N]`

**Objective:** Settings 화면의 `SuppressionManagementCard` 직후 새 `item { SettingsTrayCleanupSection(...) }` 삽입.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` — `SuppressionManagementCard` 호출 직후 (line ~311 직후, `IgnoredArchiveSettingsCard` 직전) 새 `item { }` 추가.
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` — `tray_cleanup_skip_confirm_v1` boolean key + getter / setter / Flow.

**Steps:**
1. SettingsScreen 의 ViewModel 또는 `remember { TrayOrphanCleanupRunner.create(context) }` 로 runner instance 확보 (다른 카드들이 repository 를 직접 받는 패턴 따름 — runner 도 동일).
2. preview state 는 `remember { mutableStateOf<PreviewResult?>(null) }` + `LaunchedEffect(Unit) { state = runner.preview() }`.
3. `onCleanupClick = { coroutineScope.launch { val result = runner.cleanup(); toast(result); state = runner.preview() } }`.
4. Confirm-skip preference 는 `repository.observeTrayCleanupSkipConfirm()` collectAsState 로 binding.

## Task 5: ADB e2e on R3CY2058DLJ `[DEFERRED via PR #N — device not connected; recipe preserved below; follow-up tracked as Known gap on silent-auto-hide.md]`

**Objective:** 실제 디바이스에서 100+ source orphan → cleanup → 0 (PERSISTENT_PROTECTED 제외) 확인.

**Steps:**
```bash
# 사전: 현재 트레이 dump 캡처
adb -s R3CY2058DLJ shell dumpsys notification --noredact > /tmp/before.txt
adb -s R3CY2058DLJ shell dumpsys notification --noredact | \
  grep -cE "NotificationRecord.*pkg=(com\.naver|com\.coupang|com\.kakao|com\.samsung\.android\.app\.calendar)"
# 기대값: 100+ source entries (issue body 상태)

# 빌드 + 설치
./gradlew :app:installDebug

# Settings 진입 → "트레이 정리" 카드의 preview 한 줄 확인 → "모두 정리하기" 탭 → confirm → 실행
# (수동 탭 — 자동화 안 함)

# Post-cleanup dump
adb -s R3CY2058DLJ shell dumpsys notification --noredact > /tmp/after.txt
adb -s R3CY2058DLJ shell dumpsys notification --noredact | \
  grep -cE "NotificationRecord.*pkg=(com\.naver|com\.coupang|com\.kakao|com\.samsung\.android\.app\.calendar)"
# 기대값: 0 (음악 플레이어 / 통화 등 제외하면 source 가 모두 사라짐)

# SmartNoti 자기 알림은 보존됐는지 확인
adb -s R3CY2058DLJ shell dumpsys notification --noredact | grep -c "smartnoti_silent_group_app:"
# 기대값: pre-cleanup 과 동일 (49 개 안팎)
```

결과 (before/after diff + 카운트) 를 PR body 에 첨부. PERSISTENT_PROTECTED 로 skip 된 패키지는 정확히 logcat 에 한 줄씩 (`TrayOrphanCleanupRunner` warn 로그) 남기도록 Task 2 에서 logging 추가 — 분석 용이.

## Task 6: Journey doc updates `[IN PROGRESS via PR #N]`

**Files:**
- `docs/journeys/silent-auto-hide.md` — Known gaps 에서 "100+ pre-#511 cohort 가 트레이에 잔존" 항목 (있다면) 을 Change log 로 이동, Code pointers 에 `TrayOrphanCleanupRunner` 추가, `last-verified` 는 Task 5 ADB sweep 날짜 (`date -u +%Y-%m-%d`) 로 bump.
- `docs/journeys/digest-suppression.md` — 동일 — pre-#511 DIGEST cohort 도 cleanup 영향 받음을 Change log.
- `docs/journeys/protected-source-notifications.md` — Change log 에 "트레이 정리 경로도 PERSISTENT_PROTECTED 계약을 따름" 명시 + `last-verified` bump.
- 본 plan frontmatter `status: shipped` + `superseded-by:` 는 implementer 가 PR merge 시점에 갱신.

## Task 7: Self-review + PR `[IN PROGRESS via PR #N]`

- 모든 unit test GREEN (`./gradlew :app:testDebugUnitTest`).
- ADB Task 5 결과 (before/after grep count + 토스트 스크린샷 or 캡처) PR body 에 첨부.
- PR title: `fix(#524): one-tap tray cleanup for pre-#511 source orphans`.
- PR body: invariant 명시 (사용자 trigger; PERSISTENT_PROTECTED skip; idempotent), Risks 의 fallback / not-bound 동작 설명.

---

## Scope

**In:**
- `TrayOrphanCleanupRunner` + `ActiveTrayInspector` 신규 ports (data.local)
- `SettingsTrayCleanupSection` Composable + summary builder + 단위 테스트
- `SettingsScreen` 에 새 item 한 줄 + `SettingsRepository` 에 `tray_cleanup_skip_confirm_v1` boolean key
- 3 개 journey doc Change log + `last-verified` bump
- ADB e2e on R3CY2058DLJ

**Out:**
- 자동 백그라운드 sweep (사용자 trigger 만; cleanup 가치를 사용자가 인지해야 trust 형성)
- Per-app 화이트리스트 / 제외 (Open question 으로 보류)
- `MigrateOrphanedSourceCancellationRunner` (#511) 알고리즘 변경 — 본 plan 은 사용자 trigger 단일 pass 가 보완 경로
- DIGEST 분기 동작 변경 (이미 cancel 됨, cleanup 은 잔존 cohort 만)
- PRIORITY pass-through 의 source 동작 (변경 없음)
- DataStore migration / Room schema 변경

---

## Risks / open questions

- **R1 (Open question for user): Confirm dialog 강도.** 본 plan default = confirm dialog ON + "다시 묻지 않기" 토글. 대안: 1탭 (confirm 없음). 100+ 개 cancel 의 비가역성 (사용자가 "방금 청소된 알림 중 하나를 다시 보고 싶다" 면 source 앱에서 직접 가야 함) 때문에 default 는 confirm 권장. **사용자 명시 요청 시 1탭 mode 로 변경.**
- **R2 (Open question for user): Per-app 화이트리스트.** "이 앱은 cleanup 안 함" 옵션. v1 에 넣으면 UI 복잡도 + SettingsRepository / DataStore 추가. v1 은 PERSISTENT_PROTECTED 자동 skip 만으로 충분한가? 신고 누적 시 v2 plan. **사용자 의견 필요.**
- **R3: Listener not bound 시 사용자 경험.** `inspector.isListenerBound() == false` 면 토스트 "알림 권한이 활성일 때만 가능해요" 만 surface — 자동 retry 안 함. 사용자가 권한 화면으로 직접 이동하도록 sub-text 에 "권한 확인하기" 링크 (선택; UI 카드 polish) 검토 — 본 plan 은 단순 토스트 default.
- **R4: PERSISTENT_PROTECTED 식별의 false positive / negative.** `FLAG_NO_CLEAR | FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE` 만 보고 판단. [protected-source-notifications](../../docs/journeys/protected-source-notifications.md) 의 `ProtectedSourceNotificationDetector` 는 더 풍부한 신호 (`category in {call,transport,navigation,alarm,progress}` + MediaSession extras + MediaStyle template) 를 본다. 본 plan 은 cleanup 1회성이라 단순 flag 만 봐도 안전한가? — false negative (음악 cancel) 가 더 위험하므로 **Task 2 구현 시 `ProtectedSourceNotificationDetector.signalsFrom(sbn).isProtected(...)` 를 재사용** 으로 보강. `ActiveTrayInspector` 가 raw `StatusBarNotification` 도 함께 노출하면 가능.
- **R5: 100+ 항목 단일 pass 의 main thread blocking.** Cleanup 자체는 IO scope 에서 돌리지만 `cancelNotification(key)` 는 listener 가 main thread 에서 처리. 100 회 호출이 사용자에게 frame drop 을 일으킬 risk → batch 100ms throttle (예: 10 건마다 `delay(50)`) 검토. v1 은 throttle 없이 측정 후 필요시 후속 PR.
- **R6: Cleanup 후 새 source 가 들어오면?** Cleanup 은 시점 snapshot 만 처리. 새 알림은 #511 의 forward path 가 즉시 cancel 하므로 잔존하지 않음. 사용자가 cleanup 후 Settings 진입을 다시 하면 preview = 0건 으로 정상 surface.

---

## Related journey

본 plan 이 ship 되면 다음 journey 의 Known gaps / Change log 가 갱신됨:

- [`docs/journeys/silent-auto-hide.md`](../journeys/silent-auto-hide.md) — pre-#511 SILENT cohort 의 트레이 잔존 cleanup 경로 추가
- [`docs/journeys/digest-suppression.md`](../journeys/digest-suppression.md) — pre-#511 DIGEST cohort 도 동일 cleanup 영향
- [`docs/journeys/protected-source-notifications.md`](../journeys/protected-source-notifications.md) — cleanup 경로도 PERSISTENT_PROTECTED 계약 준수

연결 issue: https://github.com/WooilKim/SmartNoti/issues/524
선행 plan: [`docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`](2026-04-28-fix-issue-511-cancel-source-on-replacement.md) (#511 forward fix; 본 plan 이 그 효과를 사용자에게 즉시 노출)
