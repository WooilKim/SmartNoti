---
status: planned
---

# Listener `processNotification` 파이프라인 단계 추출 Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Pure refactor — behavior must not change. Pin with characterization tests before moving any branch.

**Goal:** `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` 의 `processNotification` (현재 ~251 lines, 파일 215→464) 을 의미 단계별 helper 로 카브아웃해 listener service 파일을 ≤ 400 lines, `processNotification` 자체를 ≤ 60 lines 의 "단계 호출 chain" 으로 만든다. 사용자 가시 동작 (분류 결정 / IGNORE early-return / source cancel / replacement notify / DB save / silent summary) 은 그대로 유지. 이 코드 hot path 는 production 의 모든 알림이 흐르는 곳이라 inline-soup 가 길어질수록 회귀 위험이 직접 사용자 신뢰에 닿는다 — 단계가 명확히 분리되면 다음 plan-implementer 가 단일 단계 (예: routing decision) 만 안전히 손댈 수 있다.

**Architecture:**
- 기존 `notification/NotificationProcessingCoordinator` 는 현재 Rules + Categories + Settings 스냅샷 로딩 + classifier 호출까지만 수행 — 즉 `processNotification` 의 첫 1/3 만 cover. 이 plan 은 같은 패키지의 **새 helper 객체들** 로 나머지 2/3 을 추출한다 (coordinator 자체는 그대로 둠 — 그 contract 는 이미 `SmartNotiNotificationListenerServiceCategoryInjectionTest` 로 고정됨):
  - `NotificationDuplicateContextBuilder` — `DuplicateNotificationPolicy` (settings-driven window) + `LiveDuplicateCountTracker` + repository 의 `countRecentDuplicates` 를 받아 `(contentSignature, duplicateCount)` 반환.
  - `NotificationPipelineInputBuilder` — extras → title/body/sender (MessagingStyle resolver 통과) + appName lookup + `CapturedNotificationInput.withContext(...)` 를 만드는 pure builder.
  - `NotificationDecisionPipeline` — classifier output 을 받아 IGNORE early-return 분기 (cancel + repository.save 후 종료) 와 일반 흐름 분기 (suppression / routing / replacement) 둘 다 책임. side-effect 는 `SourceTrayActions` 인터페이스 (cancel / notifyReplacement / save) 로 주입해 unit test 가능하게.
  - `SourceTrayActions` (또는 동등한 functional port) — listener service 가 implementation 을 제공: `cancelNotification(key)` 는 main dispatcher 로, `notifier.notifySuppressedNotification(...)` / `repository.save(...)` 는 그대로.
- `processNotification` 자체는 다음 chain 으로 축소:
  1. `NotificationPipelineInputBuilder.build(sbn, ...)` → `CapturedNotificationInput`
  2. `NotificationProcessingCoordinator.process(input)` → `NotificationUiModel` (기존)
  3. `BuildConfig.DEBUG ? DebugClassificationOverride.resolve(...) : it`
  4. `NotificationDecisionPipeline.dispatch(baseNotification, sbn, settings, ..., actions)` → 모든 side-effect 와 persistence
- 신규 파일은 모두 `app/src/main/java/com/smartnoti/app/notification/` 패키지. import 추가 불필요.
- listener service 의 lazy `processor` / `duplicatePolicy` / `liveDuplicateCountTracker` / `persistentNotificationPolicy` 는 단계 helper 의 생성자 인자로 전달 (기존 lifecycle 그대로).

**Tech Stack:** Kotlin, kotlinx coroutines (Main / IO dispatcher 경계 유지), JUnit unit tests (helper 별 pure-Kotlin), 기존 Robolectric 라우팅 정책 테스트 그대로.

---

## Product intent / assumptions

- **순수 리팩터** — 분류 결정 / 라우팅 / IGNORE early-return / persistent bypass / suppression auto-expansion / replacement notify / silent summary / DB save 의 outcome 모두 동일.
- listener service 의 외부 시그니처 (`onNotificationPosted`, `onListenerConnected`, companion helpers `triggerOnboardingBootstrapIfConnected` / `rehearseOnboardingBootstrapIfConnected` / `cancelSourceEntryIfConnected`) 는 그대로 유지.
- IGNORE early-return 의 위치는 정확히 현재와 동일 (DIGEST/SILENT auto-expand 이전, `decision = IGNORE` 직후) — Plan `2026-04-21-ignore-tier-fourth-decision` Task 4 의 계약을 깨면 안 됨.
- `DuplicateNotificationPolicy` 가 매 호출마다 settings snapshot 으로 재생성되는 현 동작 ("설정 변경이 다음 알림부터 즉시 반영") 은 유지 — Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 4 계약.
- `dedupKeyFor` (legacy field-level `duplicatePolicy` 사용) 는 이번 plan 에서 건드리지 않는다 — 별도 callsite (`reconnectSweepCoordinator`, companion `rehearseOnboardingBootstrapIfConnected`) 가 의존.
- 같은 패키지 내 `private`/`internal` 가시성 유지. helper 들은 `internal class` 로 노출.
- 단계 helper 들은 모두 pure 또는 side-effect 가 functional port 로 주입되는 형태 — JVM unit test 에서 Robolectric 없이 직접 검증 가능해야 한다.

판단 필요 (Risks / open questions 에 escalate):
- `NotificationDecisionPipeline` 이 `withContext(Dispatchers.Main)` 을 직접 호출할지, 아니면 `SourceTrayActions.cancel(key)` 가 내부에서 main 으로 점프할지 — 후자가 테스트하기 좋지만 호출 그래프가 한 단계 늘어남. 본 plan 은 후자를 채택하나 첫 task 의 implementer 가 단일 main 점프로 묶는 편이 나을지 재검토 가능.

---

## Task 1: Pin behavior with characterization tests [IN PROGRESS via PR #468]

**Objective:** 250-line 핫패스를 조각내기 전에 현재 outcome 을 테스트로 고정. 기존 `SmartNotiNotificationListenerServiceCategoryInjectionTest` 는 coordinator 계약만 cover — 그 외 IGNORE early-return / replacement-notify / DB save / source cancel 분기는 unit-level coverage 가 listener-shape 으로 묶여 있지 않다.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/notification/NotificationDecisionPipelineCharacterizationTest.kt`
- (참고로만) 기존 `NotificationCaptureProcessorTest` / `NotificationCaptureProcessorPersistentTest` / `SourceNotificationRoutingPolicyTest` / `SuppressedSourceAppsAutoExpansionPolicyTest` / `NotificationSuppressionPolicyTest`

**Steps:**
1. 새 테스트는 production listener 를 instantiate 하지 말고, `processNotification` 본문의 "분류 결과 → side-effect" 부분을 그대로 옮겨 받을 임시 helper (Task 2 의 미래 `NotificationDecisionPipeline`) 를 inline 으로 정의해 다음 outcome 들을 fake `SourceTrayActions` 로 capture:
   - `decision = IGNORE` → `cancelCalled(key)` 1회 + `repository.save(notification with sourceSuppressionState=CANCEL_ATTEMPTED, replacementNotificationIssued=false)` + 그 외 호출 0회.
   - `decision = SILENT` + `suppressSourceForDigestAndSilent=true` + protected source 아님 → `cancelCalled(key)` 1회 + `notifySuppressedNotification(...)` 1회 + `repository.save(replacementNotificationIssued=true)`.
   - `decision = SILENT` + protected source (예: media playback flag) → `cancelCalled` 0회 + `notifySuppressedNotification` 0회 + save with `replacementNotificationIssued=false`.
   - `decision = DIGEST` + persistent + `hidePersistentSourceNotifications=true` 분기 → `cancelCalled` 1회 + save.
   - `decision = PRIORITY` (no suppression) → `cancelCalled` 0회 + save (state = NOT_APPLIED).
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.NotificationDecisionPipelineCharacterizationTest"` GREEN.
3. (확인) 기존 `:app:testDebugUnitTest` 풀세트 GREEN — 이번 task 는 신규 테스트만 추가해야 하고 production 코드는 0 라인 변경.

## Task 2: Extract `NotificationDecisionPipeline`

**Objective:** Task 1 가 captures 한 outcome 을 production 에서 새 helper 가 동일하게 이뤄내게 한다. 한 단계로 가장 큰 영역 (line 336→463, ~127 lines) 을 떼어낸다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/notification/NotificationDecisionPipeline.kt`
- 신규 `app/src/main/java/com/smartnoti/app/notification/SourceTrayActions.kt` (functional interface)
- 변경 `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`

**Steps:**
1. `SourceTrayActions` 정의: `suspend fun cancelSource(key: String)`, `fun postReplacement(...)`, `suspend fun save(notification, postedAt, contentSignature)` — 셋 다 listener service 가 main / IO 점프 책임.
2. `NotificationDecisionPipeline` 클래스에 다음 메서드 노출:
   - `suspend fun dispatch(input: DispatchInput): DispatchOutcome` — `DispatchInput` 은 `baseNotification`, `sbn`, `settings`, `decision`, `deliveryProfile`, `isPersistent`, `shouldBypassPersistentHiding`, `appName`, `contentSignature` 묶음. `DispatchOutcome` 은 audit / 테스트용 (cancel 했는지, replacement 보냈는지) — production 은 사용 안 해도 됨.
3. dispatch 본문에 IGNORE early-return + protected detector + auto-expansion + suppression policy + routing policy + replacement post + save 를 그대로 이식 (기존 줄 순서 보존). `settingsRepository.setSuppressedSourceApps(autoExpandedApps)` 도 `SourceTrayActions` 의 `setSuppressedApps(...)` 로 추가하거나, 별도 functional port 로 전달.
4. listener service 의 `processNotification` 이 dispatch 를 호출하도록 교체: 분류 결과 (`baseNotification`) + side-effect 인자 (`SourceTrayActions` 구현) 만 넘긴다.
5. `./gradlew :app:testDebugUnitTest` 풀세트 GREEN — 특히 Task 1 의 새 테스트, `SmartNotiNotificationListenerServiceCategoryInjectionTest`, `SilentArchivedCapturePathTest`, `SourceNotificationRoutingPolicyTest`, `SuppressedSourceAppsAutoExpansionPolicyTest` 가 그대로 GREEN 임을 확인.
6. `wc -l SmartNotiNotificationListenerService.kt` 가 ~500 lines 이하인지 확인.

## Task 3: Extract `NotificationDuplicateContextBuilder` + `NotificationPipelineInputBuilder`

**Objective:** Task 2 이후 남는 첫 1/3 (extras 파싱 → MessagingStyle sender → appName → duplicate window/policy/count → CapturedNotificationInput) 을 두 pure builder 로 분리. 마무리하면 `processNotification` 자체가 ~50 lines 의 chain 만 남는다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/notification/NotificationDuplicateContextBuilder.kt`
- 신규 `app/src/main/java/com/smartnoti/app/notification/NotificationPipelineInputBuilder.kt`
- 변경 `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- 신규 `app/src/test/java/com/smartnoti/app/notification/NotificationDuplicateContextBuilderTest.kt` (window 변경 → 다음 호출 즉시 반영 + persistent override 문자열 부착 contract pin)
- 신규 `app/src/test/java/com/smartnoti/app/notification/NotificationPipelineInputBuilderTest.kt` (MessagingStyle sender resolver 호출 + persistent + isPersistent 부착 + duplicateThreshold 전달)

**Steps:**
1. `NotificationDuplicateContextBuilder` 가 받는 인자: `tracker: LiveDuplicateCountTracker`, `repository: NotificationRepository`. `build(sbn, settings, isPersistent, contentSignaturePrefix)` 가 `(contentSignature, duplicateCount, duplicateWindowStart)` 반환. window 는 매 호출 settings 로 새로 만든다 (현 동작 유지).
2. `NotificationPipelineInputBuilder` 는 packageManager + `MessagingStyleSenderResolver` + `persistentNotificationPolicy` + `settingsRepository.currentNotificationContext(...)` 를 인자로 받아 `build(sbn): PipelineInput` 반환 — `PipelineInput` 은 `(captureInput, isPersistent, shouldBypassPersistentHiding, appName, contentSignature)`.
3. `processNotification` 이 두 builder 를 차례로 호출 → coordinator → debug override → pipeline.dispatch chain 으로 줄어드는지 확인.
4. `./gradlew :app:testDebugUnitTest` GREEN. listener service 파일 `wc -l` ≤ 400 lines.

## Task 4: PR + journey gap annotation

**Objective:** 리팩터 사실을 journey doc 의 Known-gap entry 옆에 plan 링크로 annotate (gap 본문은 변경하지 않음 — gap-planner 의 계약). 사용자 가시 동작 변경 없음을 PR 본문에 명시.

**Files:**
- 변경 `docs/journeys/notification-capture-classify.md` (Known gap line 120 옆에 `→ plan: docs/plans/2026-04-27-refactor-listener-process-notification-extract.md` annotate). 본문은 그대로.
- 변경 `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md` frontmatter `status: shipped` + Change log 한 줄 + (해당 시) `superseded-by:` 미사용 (journey 자체는 새로 만들지 않음).

**Steps:**
1. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 풀세트 GREEN.
2. PR 제목 후보: `refactor(listener): extract processNotification stages into pipeline helpers`.
3. PR 본문에 명시:
   - 신규 파일 (helper 4 + 테스트 3) 와 listener service 라인 변화 (627 → ≤ 400).
   - 동작 변경 없음 — Task 1 characterization test + 기존 `SmartNotiNotificationListenerServiceCategoryInjectionTest` 가 GREEN.
   - 어떤 journey 의 Observable steps / Exit state / Code pointers 도 본문 수정 없음 (Code pointer 가 줄번호를 박지 않으므로 자동 안전; helper 이름이 추가되더라도 다음 verification sweep 의 journey-tester 가 갱신).
4. journey 의 Known-gap 줄 (현 line 120) 옆에 `→ plan: docs/plans/2026-04-27-refactor-listener-process-notification-extract.md` annotate (text 자체는 보존). Plan ship 후 동일 줄을 `(resolved 2026-04-27, plan ...)` 로 prepend.

---

## Scope

**In:**
- 4개 신규 helper 파일 + listener service refactor.
- `processNotification` ≤ 60 lines / listener service ≤ 400 lines.
- Pure-Kotlin unit test 3개 (Task 1 + Task 3 두 개).

**Out:**
- 동작 / 라우팅 / suppression / silent summary planner / replacement formatter 의 outcome 변경.
- `dedupKeyFor` / `reconnectSweepCoordinator` 의 변경 (별도 paths).
- `NotificationProcessingCoordinator` 의 시그니처 / loadRules / loadCategories / loadSettings 계약 변경 — 이미 #245 plan 으로 고정.
- `onListenerConnected` 의 silentSummaryJob / storeSyncJob 분해 (별도 후속 plan 가능).

---

## Risks / open questions

- **Main dispatcher 점프 위치 (escalate to user)**: dispatch helper 가 `withContext(Dispatchers.Main)` 을 직접 호출하면 helper 가 Robolectric / coroutines test 에서 dispatcher 주입을 받아야 한다. 대안은 `SourceTrayActions.cancelSource` 구현이 main 으로 점프 — 본 plan 은 후자 (구현 책임자 = listener service) 를 채택했으나 implementer 가 dispatcher 주입을 더 깔끔하다고 판단하면 Task 2 step 3 에서 변경 가능.
- **`settingsRepository.setSuppressedSourceApps(autoExpandedApps)` write side-effect 의 ownership**: pipeline helper 가 settings 의 SSOT 를 mutate 하는 건 가시 동작이 같다면 OK 이나, 단일 책임 측면에서 builder 가 read 한 결과를 listener service 가 write 하도록 둘 수도 있음. 첫 PR 은 helper 안에 두고 (현 코드 위치와 같음) 필요하면 follow-up 으로 외부화.
- **Test fragility**: `SmartNotiNotificationListenerServiceCategoryInjectionTest` 가 listener service 의 lazy property 를 reflection 으로 swap 하는 패턴이라면 helper 추가 후에도 통과 여부를 조기에 확인 (Task 2 step 5 에서 명시). 현재 시점에서는 별도 read 필요.
- **Performance**: helper 분해는 메서드 호출 한 단계 추가 — 핫패스 영향은 무시할 수준이지만, profiler 로 확인하지 않는다면 `inline class` / `@JvmInline value class` 로 `DispatchInput` / `PipelineInput` 을 감싸는 것은 별도 plan 으로 미룬다.
- **Migration ordering**: `processNotification` 안의 `applyPendingMigrations()` 같은 호출은 없으나 (그건 `onListenerConnected` 에 있음), helper 추출 시 호출 순서를 그대로 보존해야 한다 — 특히 `categoriesRepository.currentCategories()` 호출 위치 (snapshot read) 와 `setSuppressedSourceApps` write 사이의 race 가 발생하지 않도록 settings 는 한 번만 읽고 reuse.

---

## Related journey

[notification-capture-classify](../journeys/notification-capture-classify.md) — Known gaps line 120 ("code health (2026-04-27): … `processNotification` is ~251 lines …"). 본 plan ship 후 그 줄을 `(resolved YYYY-MM-DD, plan ...)` 로 prepend, plan frontmatter 를 `status: shipped` 로 flip + Change log 추가.
