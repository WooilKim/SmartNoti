---
status: shipped
shipped: 2026-04-28
superseded-by: docs/journeys/inbox-unified.md
---

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Tests-first; the failing scoped-overload test (Task 1) must land before the production code (Task 2).

**Goal:** 정리함 Inbox 의 high-volume suggestion card 에서 사용자가 `[예, 묶을게요]` 를 탭하면 **그 packageName 한 건의 트레이 잔존 알림만** 정돈된다. 이전에 다른 앱을 accept 한 적이 있어도 그 앱들의 잔존 orphan 은 그대로 보존되어, 사용자의 "이 앱을 묶어줘" 의지가 의도한 범위 안에서만 트레이를 건드린다. 결과적으로 (a) accept 후 트레이 정리가 의지의 정밀도와 일치하고, (b) 다른 앱의 source orphan 을 의도치 않게 cancel 해 그 앱의 알림 history 가 무리하게 사라지는 일이 없어진다.

**Architecture:** `TrayOrphanCleanupRunner` 에 `suspend fun cleanup(targetPackages: Set<String>): CleanupResult` overload 를 추가한다. 기존 unscoped `cleanup()` 는 그대로 — Settings → "트레이 정리" 카드 (#524) 가 계속 사용. 신규 overload 는 동일 식별 알고리즘 (`identifyCandidates`) 을 거친 뒤 `entries.filter { it.packageName in targetPackages }` 를 한 단계 더 거는 prefilter 만 추가. 이후 `InboxScreen` 의 `InboxScreenSuggestionCallbacks.cleanupTrayOrphans(targetPackages)` 가 `cleanupRunner.cleanup()` 대신 `cleanupRunner.cleanup(targetPackages)` 를 호출하도록 한 줄을 바꾼다. PERSISTENT_PROTECTED skip / NotBound 분기 / 결과 데이터 클래스 / 로깅 모두 기존과 동일 — 정밀도만 좁혀진다.

**Tech Stack:** Kotlin + JVM unit test (kotlinx.coroutines runBlocking + 기존 fake `ActiveTrayInspector` / `SourceCancellationGateway`). Android-free.

## Product intent / assumptions

- 사용자가 `[예, 묶을게요]` 를 탭한 packageName 만 cleanup 대상이라는 명시적 계약. v1 의 "더 적극적 정돈" fallback 은 의도된 trade-off 였지만, 사용자 정성 피드백 + inbox-unified.md Known gap 로 정밀화가 합당하다고 판단.
- Snooze (`[나중에]`) / dismiss (`[무시]`) 분기는 cleanup 호출 자체가 없으므로 본 plan 의 영향 범위 밖.
- Settings → "트레이 정리" 카드 (#524) 는 unscoped `cleanup()` 을 계속 호출 — 사용자가 "트레이 통째로 정리" 의지로 진입하는 별개 surface. 두 surface 가 같은 runner 의 두 overload 를 각자 호출하는 모양이 되어 의지의 표현이 surface 별로 분리된다.
- `targetPackages` 가 빈 set 이면 cancel 0건 (`identifyCandidates` 결과를 비우는 한 줄 prefilter 의 자연 동작) — caller 가 의지를 비워서 부르는 것은 noop 이라는 의미.
- 기존 `cleanup()` overload 는 deprecate 하지 않는다 — `data/local/TrayOrphanCleanupRunner.kt` 의 production wiring (#524) 이 그대로 의존.
- 본 변경은 **사용자 관측 동작 변경** (accept 후 다른 앱 orphan 이 보존됨) → `docs/journeys/inbox-unified.md` 의 Known gap "scoped cleanup overload pending" 을 Change log + Code pointers 갱신으로 닫는다.

## Task 1: Add failing test for scoped overload [SHIPPED via PR #546 commit f9b7326]

**Objective:** scoped overload 의 정밀도 계약 (target 만 cancel, 그 외 보존) 을 단일 fixture 로 고정. 본 task 가 RED 인 상태로 commit 해서 implementer 의 다음 step 이 GREEN 으로 만드는 motion 을 가시화.

**Files:**
- 보강: `app/src/test/java/com/smartnoti/app/data/local/TrayOrphanCleanupRunnerTest.kt`

**Steps:**
1. 기존 `fresh_cleanup_cancels_orphan_sources_and_skips_protected` 의 fake fixture 헬퍼 (FakeActiveTrayInspector / RecordingSourceCancellationGateway 또는 동일 명칭) 를 그대로 재사용. 새 헬퍼 추가 금지 — 같은 fake 의 entries 만 다양화.
2. 신규 test: `scoped_cleanup_only_cancels_target_packages_and_preserves_others`.
   - Inspector fixture: 5개 source orphan (네이버 / 카카오 / 쿠팡이츠 / 마이제네시스 / 삼성헬스 — `com.nhn.android.search`, `com.kakao.talk`, `com.coupang.eats`, `com.genesis.mygenesis`, `com.samsung.android.shealth`) + 각 packageName 마다 `smartnoti_silent_group_app:<pkg>` group summary entry (SmartNoti 가 게시했다는 신호). 모든 entry `flags = 0` (PERSISTENT_PROTECTED skip 분기는 본 case 에서 비활성).
   - Listener bound = true.
   - 호출: `runner.cleanup(setOf("com.nhn.android.search"))`.
   - Assert: `result.cancelledCount == 1`, `result.skippedProtectedCount == 0`, `result.notBound == false`. `gateway.cancelled` 가 정확히 한 entry — 그것의 packageName 이 `com.nhn.android.search` (`gateway.cancelled.single().key.startsWith("com.nhn.android.search:")` 또는 fake 의 record 형식에 맞게).
3. 신규 test: `scoped_cleanup_with_empty_target_set_is_noop`.
   - 동일 fixture, `runner.cleanup(emptySet())` 호출.
   - Assert: `cancelledCount == 0`, `gateway.cancelled.isEmpty()`, `notBound == false`.
4. 신규 test: `scoped_cleanup_still_skips_protected_target_entries`.
   - Inspector fixture: `com.nhn.android.search` packageName 의 source orphan 2건 — 하나는 `flags = 0`, 다른 하나는 `flags = Notification.FLAG_FOREGROUND_SERVICE`. 같은 packageName.
   - 호출: `runner.cleanup(setOf("com.nhn.android.search"))`.
   - Assert: `cancelledCount == 1`, `skippedProtectedCount == 1`, `gateway.cancelled.single()` 이 unprotected entry. (PERSISTENT_PROTECTED 가 packageName 정밀도와 합쳐져도 여전히 보호됨을 확인.)
5. 신규 test: `scoped_cleanup_short_circuits_when_listener_not_bound`.
   - Inspector fixture: `isListenerBound() = false`, entries 비어 있어도 무관.
   - 호출: `runner.cleanup(setOf("com.nhn.android.search"))`.
   - Assert: `cancelledCount == 0`, `skippedProtectedCount == 0`, `notBound == true`. `gateway.cancelled.isEmpty()` (호출조차 시도 안 됨 — short-circuit).
6. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.local.TrayOrphanCleanupRunnerTest"` 실행해 4개 신규 case 가 **컴파일 에러로 RED** 인지 확인 (overload 가 아직 없으니 unresolved reference). RED 메시지를 Task 1 commit body 에 첨부.

## Task 2: Add scoped `cleanup(targetPackages)` overload [SHIPPED via PR #546 commit f9b7326]

**Objective:** Task 1 의 4 case 가 GREEN.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/data/local/TrayOrphanCleanupRunner.kt`

**Steps:**
1. 기존 `suspend fun cleanup(): CleanupResult` 위/아래에 sibling 으로 다음을 추가:
   ```kotlin
   suspend fun cleanup(targetPackages: Set<String>): CleanupResult {
       if (!inspector.isListenerBound()) {
           return CleanupResult(
               cancelledCount = 0,
               skippedProtectedCount = 0,
               notBound = true,
           )
       }

       val entries = inspector.listActive()
       val candidates = identifyCandidates(entries)
           .filter { entry -> entry.packageName in targetPackages }
       val (cancellable, protectedEntries) = candidates.partition { entry ->
           (entry.flags and PROTECTED_FLAGS) == 0
       }

       protectedEntries.forEach { entry ->
           Log.w(
               TAG,
               "tray-cleanup skip protected pkg=${entry.packageName} key=${entry.key} flags=0x${Integer.toHexString(entry.flags)}",
           )
       }

       cancellable.forEach { entry -> gateway.cancel(entry.key) }

       return CleanupResult(
           cancelledCount = cancellable.size,
           skippedProtectedCount = protectedEntries.size,
           notBound = false,
       )
   }
   ```
   — 기존 unscoped `cleanup()` 와 거의 동일하지만 `identifyCandidates(...)` 결과에 `.filter { it.packageName in targetPackages }` 한 줄 prefilter 만 추가. PROTECTED_FLAGS 처리 / 로깅 / NotBound 분기 / 결과 형식은 기존과 1:1.
2. KDoc: 짧게 한 단락 — "v1 정리함 suggestion accept 의 [예] 분기에서 호출되는 scoped variant. 사용자가 의지를 밝힌 packageName 만 cleanup. 빈 set 은 noop." 기존 class-level KDoc 의 알고리즘 설명을 다시 적지 말고 unscoped overload 와의 관계만 명시.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.local.TrayOrphanCleanupRunnerTest"` GREEN — 4 신규 case + 기존 case 모두 통과.

## Task 3: Wire `InboxScreen` to call scoped overload [SHIPPED 2026-04-28]

**Objective:** suggestion accept 경로가 실제로 scoped overload 를 사용. Production wiring 한 줄 + 주변 주석 정리.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxScreen.kt`

**Steps:**
1. `InboxScreenSuggestionCallbacks.cleanupTrayOrphans` 본문 (현재 line 441-454) 수정:
   - `cleanupRunner.cleanup()` → `cleanupRunner.cleanup(targetPackages)`.
   - 그 위 v1 fallback 주석 (line 442-447, "v1: the scoped overload ... still pending #524 follow-up plan Task 4 ... it walks the tray once and cancels every orphan ...") 을 새 진실로 교체:
     ```kotlin
     // Scoped overload — only cancels orphans whose packageName is in
     // [targetPackages]. The accept handler always passes a singleton set
     // of the candidate's packageName, so the user's intent ("stop bundling
     // this app") is honored exactly. Other packages' orphans are
     // preserved; the unscoped Settings → "트레이 정리" card is the
     // separate surface for whole-tray cleanup.
     ```
   - `result.cancelledCount` mapping 은 그대로.
2. `InboxScreenSuggestionCallbacks` class-level KDoc (line 411-429) 의 `[cleanupTrayOrphans]` 단락도 동일하게 갱신: "the scoped overload is still pending #524 follow-up plan Task 4" 문구 제거 후 "scoped overload — single-packageName precision" 으로 교체.
3. Build smoke: `./gradlew :app:assembleDebug` 통과 (UI wiring 변경이라 컴파일만 확인; UI 테스트는 본 plan 범위 밖).

## Task 4: Verify accept handler integration test still GREEN + extend if needed [SHIPPED 2026-04-28 — InboxSuggestionAcceptIntegrationTest 4 case GREEN, 추가 case 불필요 (RecordingCallbacks 가 이미 cleanupTargets: List<Set<String>> 로 record)]

**Objective:** `InboxSuggestionAcceptIntegrationTest` 의 `accept_runs_suppress_and_cleanup_in_documented_order` 가 새 wiring 에서도 통과. callbacks fake 를 인터페이스 그대로 호출하므로 변경 불필요한 게 정상 — 확인 단계 포함.

**Files:**
- 확인 (필요 시 보강): `app/src/test/java/com/smartnoti/app/ui/screens/inbox/InboxSuggestionAcceptIntegrationTest.kt`

**Steps:**
1. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.inbox.InboxSuggestionAcceptIntegrationTest"` 실행. 기존 `accept_runs_suppress_and_cleanup_in_documented_order` 가 callbacks fake 의 `cleanupTrayOrphans(setOf(pkg))` 호출 순서를 검증한다면 변경 없이 통과. RED 가 나오면 fake 가 unscoped variant 를 가정하고 있다는 뜻 — fake 는 `InboxSuggestionAcceptCallbacks` interface 만 구현하므로 영향 없어야 한다.
2. (옵션) 추가 case `accept_passes_only_target_packageName_to_cleanup`: fake 가 `cleanupTrayOrphans` 호출 시 받은 set 을 record, accept 후 `recordedSet == setOf(candidate.packageName)` 검증. 이미 동등한 verification 이 있으면 skip — 신규 추가는 noise.
3. 전체 inbox 테스트 스위트 GREEN 확인: `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.inbox.*"`.

## Task 5: Update journey doc + flip plan status [SHIPPED 2026-04-28]

**Objective:** `docs/journeys/inbox-unified.md` Known gap "High-volume suggestion card v1: scoped cleanup overload pending" 을 닫고 Change log + Code pointers 동기화. 본 plan frontmatter 를 `status: shipped` + `superseded-by:` 로 flip.

**Files:**
- 수정: `docs/journeys/inbox-unified.md`
- 수정: `docs/plans/2026-04-28-tray-orphan-cleanup-scoped-overload.md` (본 파일)

**Steps:**
1. `docs/journeys/inbox-unified.md` Known gaps 섹션의 `**High-volume suggestion card v1: scoped cleanup overload pending**` bullet 을 `(resolved YYYY-MM-DD, plan docs/plans/2026-04-28-tray-orphan-cleanup-scoped-overload.md)` prefix 로 마킹. 본문은 보존 (history).
2. Change log 신규 entry: 본 PR 의 commit hash + 한두 줄 — "scoped `TrayOrphanCleanupRunner.cleanup(targetPackages)` overload 추가 + suggestion accept 가 single-packageName 정밀도로 cleanup. 다른 packageName 의 orphan 은 보존."
3. Code pointers 섹션에 `data/local/TrayOrphanCleanupRunner#cleanup(targetPackages)` 한 줄 추가 (기존 unscoped `cleanup()` pointer 와 sibling).
4. 본 plan frontmatter `status: planned` → `status: shipped` + `superseded-by: docs/journeys/inbox-unified.md` 추가.
5. `last-verified` 는 건드리지 않는다 — recipe 재실행이 아니라 wiring 변경 + unit test gate 만이므로.

## Scope

**In scope:**
- `TrayOrphanCleanupRunner.cleanup(targetPackages: Set<String>): CleanupResult` overload 신규.
- `InboxScreen` 의 단일 call site 가 새 overload 를 사용.
- 4 신규 unit case + journey 동기화.

**Out of scope:**
- Settings → "트레이 정리" 카드 (#524) 의 unscoped `cleanup()` 호출 변경 — whole-tray cleanup 의 의지를 그대로 유지.
- `ActiveTrayEntry` projection 확장 (이미 충분) — PERSISTENT_PROTECTED 신호도 기존 flags 만으로 처리.
- `MigrateOrphanedSourceCancellationRunner` (#511) — listener bind 시 1회성 sweep 은 별개 메커니즘. 본 plan 의 scoped overload 와 독립.
- ADB e2e 검증 — JVM unit test 로 contract 충분히 묶임. 정리함 ADB e2e 는 #525 Task 9 가 이미 R3CY2058DLJ-class fixture 의존 Known gap 으로 별도 추적.
- UI 변화 / copy 변경 — accept 후 toast / snackbar 는 기존 그대로.

## Risks / open questions

- **Task 1 의 Inspector fixture 가 기존 `fresh_cleanup_*` 와 호환되어야 한다.** 만약 기존 fake 가 `entries: List<ActiveTrayEntry>` 를 생성자에 받는 단순 형태가 아니라면, scoped case 4건도 같은 builder/DSL 패턴을 따라야 함 — implementer 가 처음에 기존 fixture 를 그대로 읽고 시작.
- **`InboxScreen.kt:441-454` 의 코드 위치는 Task 3 실행 시점에 문서 작성 시각의 line number 와 일치하지 않을 수 있다** (#525-cohort 후속 PR 이 inbox 영역을 추가 손볼 가능성). Implementer 는 `InboxScreenSuggestionCallbacks` symbol 검색 + `cleanupTrayOrphans` override 본문을 기준으로 식별.
- **Snooze / dismiss 경로가 의도치 않게 cleanup 을 호출하는지** — `InboxSuggestionAcceptHandler.accept` 만 호출하는 게 맞는지 implementer 가 확인. Snooze / dismiss 는 `InboxScreen` 의 onSnooze / onDismiss callback 에서 `SettingsRepository` 직접 호출 (`InboxSuggestionAcceptHandler` 우회 — 위 Read 로 확인됨) 이므로 영향 없어야 한다.
- **정밀도가 좁아지는 의도된 효과의 부작용**: 사용자가 이전에 다른 앱을 accept 한 적이 있고 그 앱의 orphan 이 트레이에 잔존 중이라면, 본 변경 이전에는 새 accept 로 함께 정돈됐지만 이후에는 보존된다. Settings → "트레이 정리" 카드를 안내하는 nudge 를 추가할지 여부는 본 plan 범위 밖 (UX 결정) — Risks 로만 기록.
- **Open question**: `cleanup(targetPackages)` 이 빈 set 을 "noop" 으로 처리하는 것이 맞는가, 아니면 "전체 cleanup" 으로 폴백하는 것이 맞는가? 본 plan 은 noop 으로 결정 (caller 의 의지를 그대로 honour) — `InboxScreen` 의 accept handler 가 항상 singleton set 을 넘기므로 빈 set 호출 자체가 발생하지 않으나, 오용 방지 차원에서 noop 이 안전. 후속 caller 가 추가될 때 재고려 필요.

## Related journey

- [inbox-unified](../journeys/inbox-unified.md) — Known gap "High-volume suggestion card v1: scoped cleanup overload pending" 을 본 plan 이 닫는다. Change log entry + Code pointers 추가는 Task 5.
- 우산 plan (history): [`docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`](./2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md) §Task 4 는 "depends on #524" 로 기록되어 shipped 시점에 deferred. 본 plan 이 그 Task 4 를 단독으로 수행.
- 인접 plan: [`docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`](./2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md) — 기존 unscoped `cleanup()` overload 의 owner. 본 plan 은 그 옆에 scoped overload 를 sibling 으로 둔다 — 두 surface 가 같은 runner 의 두 overload 를 각자 호출하는 모양.
