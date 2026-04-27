---
status: shipped
shipped: 2026-04-27
superseded-by: ../journeys/notification-detail.md
---

# NotificationDetailScreen 을 sub-section composable 로 split Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Refactor — behavior must not change.

**Goal:** `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt` (687 lines, code-health-scout 의 700-line critical threshold 직전) 를 의미 단위 sub-file 로 분해해 root 파일이 ≤ 350 lines 로 내려가게 한다. 현재 한 파일에 root composable 1개 + 4개 helper (`DetailTopBar`, `ReasonSubSection`, `resolveOwningCategoryAction`, `ruleMatches`) + LazyColumn 안에 인라인된 7개의 `item { Card { Column { ... } } }` 카드 (요약 / 이유 / 온보딩 추천 / 전달 모드 / 원본 상태 / 보관 처리 완료 / 분류 변경 CTA) + 2개의 다이얼로그/시트 호출 (`CategoryAssignBottomSheet`, `CategoryEditorScreen`) 이 동거해 IDE 탐색 / git blame / plan-implementer 작업 위치 파악이 무거워진다. 동작은 변경하지 않는다 — composable 시그니처 / state hoisting / repository / use-case wiring / snackbar / sheet flow 전부 유지. 본 plan 은 직전 `2026-04-27-refactor-home-screen-split.md` / `2026-04-27-refactor-settings-screen-split.md` 에서 검증된 패턴 (characterization test → carve-out → 사이즈 검증) 을 그대로 재사용한다.

**Architecture:**
- 같은 패키지 (`ui/screens/detail/`) 에 sub-file 3개 신설:
  - `NotificationDetailReasonSection.kt` — `ReasonSubSection` 헬퍼 + 인라인 "왜 이렇게 처리됐나요?" 카드 (`SmartNoti 가 본 신호` / `지금 적용된 정책` / `적용된 규칙` 3 sub-section 분기) + `onboardingRecommendationSummary` 카드 (~3 composables, ~150 lines, 현 line 257→339 + line 665→686 helper).
  - `NotificationDetailDeliverySection.kt` — `deliveryProfileSummary` "어떻게 전달되나요?" 카드 + `sourceSuppressionSummary` "원본 알림 처리 상태" 카드 + ARCHIVED-only "조용히 보관 중 / 처리 완료로 표시" 카드 (`MarkSilentProcessedTrayCancelChain` 호출 포함) (~3 composables, ~140 lines, 현 line 340→461).
  - `NotificationDetailReclassifyActions.kt` — `DetailTopBar` 헬퍼 + "이 알림 분류하기" CTA 카드 + `CategoryAssignBottomSheet` 호출 블록 + `CategoryEditorScreen` 호출 블록 + `resolveOwningCategoryAction` / `ruleMatches` 두 helper fun (~3 composables + 2 helper funs, ~210 lines, 현 line 67→88 + line 462→614 + line 626→663).
- `NotificationDetailScreen.kt` 는 root composable + 화면-레벨 state hoisting (repository / use-case `remember`, `notification`/`rules`/`categories`/`settings` flow collect, `notification == null` empty branch, `LazyColumn` 골격 + 첫 요약 카드 `item`, `SnackbarHost` overlay) 만 보유.
- 신규 파일은 모두 같은 package `com.smartnoti.app.ui.screens.detail` — 외부 import 영향 없음.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit / Robolectric characterization tests (composable hierarchy node assertion).

---

## Product intent / assumptions

- 본 plan 은 **순수 리팩터** — 사용자 가시 동작 / Detail 카드 순서 / 카피 / "분류 변경" 시트 / Path A / Path B 흐름 / snackbar 메시지 / "처리 완료로 표시" 체인 모두 변경 없음.
- 기존 `NotificationDetailScreen` composable 의 호출자 (`AppNavHost.kt`) 는 시그니처 변경 없음.
- 분해 단위는 "현재 파일 안에서 인접해 그룹된 카드들" — 같은 의미 영역끼리 묶는다 (Reason 3 / Delivery 3 / Reclassify 3+2helper). 직전 SettingsScreen / HomeScreen split 과 동일한 cut-and-paste 전략.
- carving 후 root 파일 = root composable + state hoisting + 요약 (앱명/sender/body/StatusBadge) 카드 + `LazyColumn` 골격 + sub-section 호출 + `SnackbarHost` 만 남는다. 본 plan 은 "root 파일이 350 lines 이하" 를 통과 기준으로 한다 (HomeScreen split 과 동일 비례 목표: 877 → ≤ 350 vs 687 → ≤ 350).
- LazyColumn 의 `item { ... }` 호출은 root 에 남기고, item 내부 Card content 만 sub-section composable 로 추출 — 즉 `item { ReasonCard(sections, quietHoursExplainer, onRuleClick) }` 형태. LazyListScope receiver 를 sub-section 으로 넘기지 않는다 (Compose recomposition graph 단순 유지 + 호출자가 each item 의 visibility 조건을 명시적으로 보유).
- 2개 다이얼로그/시트 (`CategoryAssignBottomSheet`, `CategoryEditorScreen`) 호출 블록은 `LazyColumn` 밖에 있어 한 sub-section composable 로 묶어 root 에서 단일 호출. lambdas 내부의 `applyToCurrentRowUseCase.apply` / `assignUseCase.assignToExisting` / `confirmationMessageBuilder.build` / `snackbarHostState.showSnackbar` 호출 체인 무변경.
- preview 가 있으면 sub-file 로 함께 이동 (현재 `NotificationDetailScreen.kt` 에 `@Preview` annotation 없음 — Task 1 에서 grep 으로 재확인).

---

## Task 1: Pin behavior with characterization tests [IN PROGRESS via PR #465]

**Objective:** Refactor 전에 현 화면의 가시 contract 를 테스트로 고정. 테스트 없이 687-line composable 을 조각내면 사용자 동작 회귀 (예: ARCHIVED 카드 visibility 조건, quiet-hours explainer 출현 조건, `사용자 규칙` higher-precedence suppression) 를 잡을 방법이 없다. SettingsScreen / HomeScreen split 의 PR #457 / #461 패턴 재사용.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreenCharacterizationTest.kt`

**Steps:**
1. fake `NotificationRepository` / `RulesRepository` / `CategoriesRepository` / `SettingsRepository` 주입 (기존 `SettingsScreenCharacterizationTest` / `HomeScreenCharacterizationTest` 의 anonymous-object 패턴 채택). fake 들은 알려진 `NotificationUiModel` 1건 + 빈 rules/categories 로 시작.
2. `composeTestRule.setContent { NotificationDetailScreen(contentPadding=PaddingValues(0.dp), notificationId="known", onBack={}, onRuleClick={}) }` 로 다음 affordance 노출을 assertion (현 코드 GREEN baseline):
   - "알림 상세" top bar + 뒤로 가기 IconButton.
   - 요약 카드의 appName / sender(또는 title) / body / StatusBadge.
   - "왜 이렇게 처리됐나요?" 헤더 + 적어도 한 sub-section (`reasonSections.classifierSignals` 또는 `ruleHits` 또는 `quietHoursExplainer` 가 채워질 때).
   - "어떻게 전달되나요?" 헤더 + `전달 모드 · …` / `소리 · …` 5개 라벨 prefix (deliveryProfileSummary 가 not-null 일 때).
   - "이 알림 분류하기" 헤더 + `Button("분류 변경")` 텍스트.
   - ARCHIVED 시나리오 별도 fake (status=SILENT, silentMode=ARCHIVED) → "조용히 보관 중" + `Button("처리 완료로 표시")` visibility.
   - quietHoursExplainer 시나리오 (reasonTags 에 `조용한 시간` 포함, `사용자 규칙` 미포함) → "지금 적용된 정책" sub-section visibility.
   - higher-precedence suppression (reasonTags 에 `사용자 규칙` + `조용한 시간` 동시) → "지금 적용된 정책" sub-section **부재** assertion.
   - "분류 변경" 버튼 클릭 → `CategoryAssignBottomSheet` mount 어서 visibility (sheet 헤더 카피 검출).
   - notificationId 가 fake repo 에 없는 경우 → "알림을 찾을 수 없어요" empty state visibility.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.detail.NotificationDetailScreenCharacterizationTest"` → 모두 GREEN (현 코드 기준).

## Task 2: Carve out Reason + Delivery sub-files

**Objective:** 가장 결합도 낮은 두 영역 (reason 3 카드 / delivery 3 카드) 을 같은 패키지의 신규 파일로 이동. 파일 사이즈를 즉시 ~290 lines 깎는다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailReasonSection.kt`
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailDeliverySection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt`

**Steps:**
1. `ReasonSubSection` helper composable + "왜 이렇게 처리됐나요?" 인라인 Card 블록 + 온보딩 추천 Card 블록 (현 line 257~339 + 665~686) 을 `NotificationDetailReasonSection.kt` 로 cut-and-paste:
   - 신규 `internal fun NotificationDetailReasonCard(sections: NotificationDetailReasonSectionBuilder.Sections?, quietHoursExplainer: QuietHoursExplainerBuilder.Result?, onRuleClick: (String) -> Unit)` — `hasReasonContent` gate 를 함수 안에서 검사하고 false 시 nothing emit (root 호출자는 항상 호출, sub-fn 이 자체 visibility 결정).
   - 신규 `internal fun NotificationDetailOnboardingRecommendationCard(summary: NotificationDetailOnboardingRecommendationSummaryBuilder.Result?)` — null 시 nothing emit.
   - `ReasonSubSection` 은 같은 파일에 `private` 로 보존.
   - root 의 `item { … }` 호출은 `item { NotificationDetailReasonCard(...) }` + `item { NotificationDetailOnboardingRecommendationCard(...) }` 로 1줄씩 변환. (visibility 조건이 sub-fn 안에서 무-emit 이라도 LazyColumn item 자체는 항상 1개 record 함 — Lazy slot key 변동 없으므로 recomposition 그래프 동일.)
2. delivery profile + source suppression + ARCHIVED 처리 완료 카드 (현 line 340~461) 를 `NotificationDetailDeliverySection.kt` 로 동일 패턴 이동:
   - 신규 `internal fun NotificationDetailDeliveryProfileCard(summary: NotificationDetailDeliveryProfileSummaryBuilder.Result?)`.
   - 신규 `internal fun NotificationDetailSourceSuppressionCard(summary: NotificationDetailSourceSuppressionSummaryBuilder.Result?)`.
   - 신규 `internal fun NotificationDetailArchivedCompletionCard(notification: NotificationUiModel, repository: NotificationRepository, scope: CoroutineScope)` — 현재 `MarkSilentProcessedTrayCancelChain` 호출 체인을 그대로 옮김. visibility gate (`status == SILENT && silentMode == ARCHIVED`) 도 함수 안.
3. import 정리 — 이동한 카드의 import (`androidx.compose.foundation.layout.Column` 등) 는 새 파일에 복제하고 root 에서는 unused 만 제거. `NotificationRepository` / `MarkSilentProcessedTrayCancelChain` / `SmartNotiNotificationListenerService` import 는 `ArchivedCompletionCard` 가 있는 파일 (`NotificationDetailDeliverySection.kt`) 에만 필요.
4. `./gradlew :app:assembleDebug` 통과 + Task 1 테스트 GREEN.
5. `wc -l NotificationDetailScreen.kt` ≤ 400 인지 확인 (687 - 290 ≈ 397). 초과 시 어떤 헬퍼가 root 에 남았는지 식별.

## Task 3: Carve out Reclassify Actions sub-file

**Objective:** 남은 가장 큰 영역 (DetailTopBar + "분류 변경" CTA + Sheet + Editor + 2 helper funs) 도 분리해 root 파일을 ≤ 350 lines 로 마무리.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailReclassifyActions.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt`

**Steps:**
1. `DetailTopBar` (현 line 67~88) 를 `NotificationDetailReclassifyActions.kt` 로 cut-and-paste, visibility `private` → `internal`. (top bar 가 가장 단순한 helper 이므로 같이 묶는다.)
2. "이 알림 분류하기" CTA 카드 (현 line 462~494) 를 신규 `internal fun NotificationDetailReclassifyCta(onOpenAssignSheet: () -> Unit)` 로 추출 — root 가 `var showAssignSheet by remember { … }` state 를 hoisting 그대로 보유.
3. `CategoryAssignBottomSheet` 호출 블록 (현 line 508~565) + `CategoryEditorScreen` 호출 블록 (현 line 567~613) 을 신규 `internal fun NotificationDetailReclassifyHost(notification: NotificationUiModel, categories: List<Category>, rules: List<RuleUiModel>, showAssignSheet: Boolean, onAssignSheetVisibilityChange: (Boolean) -> Unit, pendingPrefill: CategoryEditorPrefill?, onPendingPrefillChange: (CategoryEditorPrefill?) -> Unit, assignUseCase: AssignNotificationToCategoryUseCase, applyToCurrentRowUseCase: ApplyCategoryActionToNotificationUseCase, confirmationMessageBuilder: DetailReclassifyConfirmationMessageBuilder, snackbarHostState: SnackbarHostState, scope: CoroutineScope)` 로 추출 — 모든 외부 dependency 를 명시적 파라미터로 받아 추후 단위 테스트 가능. lambdas 내부 동작 (`applyToCurrentRowUseCase.apply` 호출, `confirmationMessageBuilder.build` 호출, `snackbarHostState.showSnackbar` 호출) 은 그대로 보존.
4. `resolveOwningCategoryAction` (현 line 626~640) + `ruleMatches` (현 line 642~663) helper fun 두 개를 같은 파일 (`NotificationDetailReclassifyActions.kt`) 로 이동. visibility `private` 유지 — 같은 파일 안에서만 호출되므로 `internal` 승격 불필요.
5. root 의 `if (showAssignSheet) { ... }` + `if (prefill != null) { ... }` 두 블록은 `NotificationDetailReclassifyHost(...)` 1줄 호출로 대체.
6. `wc -l NotificationDetailScreen.kt` ≤ 350 인지 검증 — 초과 시 어떤 helper 가 root 에 남았는지 식별 (예: 요약 카드 inline 을 `NotificationDetailHeaderSection.kt` 로 추가 분리하는 follow-up plan).
7. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 통과.

## Task 4: PR + journey doc note

**Objective:** Refactor 사실을 doc 에 한 줄로 기록 — 사용자 가시 동작 변경 없음을 명시해 notification-detail journey 의 Verification recipe 가 영향받지 않음을 확인.

**Files:**
- `docs/journeys/notification-detail.md` — Known gaps 의 "code health (2026-04-27): NotificationDetailScreen 687 lines" bullet 을 Change log 의 한 줄 기록으로 대체.
- `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md` — frontmatter `status: planned → shipped` + `shipped: <YYYY-MM-DD>` (`date -u` per `.claude/rules/clock-discipline.md`) + Change log 추가 + `superseded-by: ../journeys/notification-detail.md`.

**Steps:**
1. `./gradlew :app:assembleDebug` GREEN.
2. PR 본문에 다음 명시:
   - 3개 신규 sub-file + root file size before/after.
   - 동작 변경 없음 — Task 1 characterization test 가 그대로 GREEN 임을 명시.
   - notification-detail journey 문서 본문 변경: Known gaps 의 "code health (2026-04-27)" bullet 제거 + Change log 에 "<YYYY-MM-DD> (per `date -u`): NotificationDetailScreen split into 3 sub-files (reason / delivery / reclassify-actions). Root file 687 → ≤ 350 lines. 동작 변경 없음."
3. PR 제목 후보: `refactor(detail): split NotificationDetailScreen.kt into per-section sub-files`.
4. frontmatter 를 `status: shipped` 로 flip + Change log 에 PR 링크 (per `.claude/agents/plan-implementer.md` MP-2.1 의 frontmatter-on-merge 규칙).

---

## Scope

**In:**
- 3개 신규 sub-file 로 composable 분리.
- `NotificationDetailScreen.kt` root file 사이즈 ≤ 350 lines.
- Characterization test (composable hierarchy assertion + ARCHIVED visibility + quietHoursExplainer suppression gate).
- notification-detail journey 의 Known gap "code health (2026-04-27)" bullet 정리 + Change log 추가.

**Out:**
- 동작 변경 / state hoisting 재설계 / repository flow 구독 패턴 변경 / use-case 인스턴스 생성 위치 변경 / `remember` 키 변경.
- Sub-section 내부 추가 분해 (예: `ReasonSubSection` 안의 sub-composable, sheet 안의 row 단위 분리).
- Detail 카드 순서 / 카피 / margin / spacing 변경.
- "분류 변경" 시트 / 에디터 호출 흐름 자체 (Path A / Path B / lambda 체인) 의 의미 변경.
- 다른 큰 파일 (`SettingsRepository.kt` 705 lines, `NotificationRepository.kt` 450 lines, listener `processNotification` 251 lines, settings residual 1118-line suppression cluster) — 각각 별도 plan.

---

## Risks / open questions

- `NotificationDetailReclassifyHost` 의 파라미터 수가 12개로 많아 — 호출자 (root) 가 `data class NotificationDetailReclassifyDeps` 같은 묶음 객체로 전달하는 옵션도 검토. 결정은 plan-implementer 의 1차 PR 에서 (저비용 리팩터, 본 plan 의 Goal 에 영향 없음). 본 plan 은 "12개 explicit params" 또는 "1개 deps 객체" 둘 다 합격으로 본다.
- `NotificationDetailScreen.kt` 안에 `@Preview` annotation 이 있는지 — Task 1 직전에 grep 으로 재확인하고, 있으면 함께 sub-file 로 옮긴다. (현 시점 `Grep ^@Preview` 로 발견 안 됨 — Task 1 에서 binding 확인.)
- `internal` 승격이 같은 module 의 다른 파일에서 의도치 않게 호출되지 않는지 grep — 현재는 모두 `private` 였으므로 외부 호출자 없음. plan-implementer 가 cut-paste 후 `grep -r "DetailTopBar\|ReasonSubSection\|resolveOwningCategoryAction\|ruleMatches"` 로 cross-package 호출 부재 재확인.
- `MarkSilentProcessedTrayCancelChain` 호출 체인의 ARCHIVED 카드는 `repository::markSilentProcessed`, `repository::sourceEntryKeyForId`, `SmartNotiNotificationListenerService.Companion::cancelSourceEntryIfConnected` 세 method reference 를 묶는다. Sub-fn 으로 옮길 때 이 메서드 참조 표기가 컴파일러에서 정상 resolve 되는지 (sub-file 의 import 가 충분한지) 확인 필요. 가장 안전한 선택: chain 인스턴스 생성을 sub-fn 안 lambdas 안에 그대로 보존 (현 코드와 1:1 mapping).
- Compose recomposition perf 영향 없음 — 파일 분해는 코드 위치만 바꾸지 graph 를 바꾸지 않는다. LazyColumn item lambda 안의 `NotificationDetailReasonCard(...)` 호출은 root 의 `item { Card { Column { ... } } }` 인라인 호출과 동일 노드 수.
- characterization test 의 fake repository 주입 방식이 직전 SettingsScreen / HomeScreen 과 다를 수 있음 (Detail 은 `observeNotification(id)` flow 단일 row + `categoriesRepository.appendRuleIdToCategory` suspend setter 등 추가 시그니처 필요). 첫 task 에서 anonymous-object 패턴으로 시작하되, 시그니처 폭이 넓어지면 `FakeNotificationDetailDeps` 헬퍼로 묶는 옵션을 plan-implementer 가 결정 (저비용 결정).
- notification-detail journey 의 Code pointers 가 줄번호를 박지 않았다는 가정 — `.claude/rules/docs-sync.md` 위반 여부를 plan-implementer 가 Task 4 직전에 한 번 확인 (`Grep "NotificationDetailScreen.kt:" docs/journeys/notification-detail.md`).
- `date -u` 시점은 plan-implementer 가 task 시작 시 한 번 읽어 frontmatter `shipped:` + Change log 행 + journey Change log 행 모두에 동일 값 사용 (per `.claude/rules/clock-discipline.md`).

---

## Related journey

[notification-detail](../journeys/notification-detail.md) — Known gaps 의 "code health (2026-04-27): `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt` — file is 687 lines (close to 700 critical threshold), `NotificationDetailScreen` composable body ~493 lines (line 91→~580+) inlining 4 anonymous-object repository overrides + several reason / delivery / classifier sub-sections. Refactor candidate — extract reason card + delivery card + reclassify-bottom-sheet wiring into sibling composables; hoist the inline anonymous repository wrappers into a fake-friendly factory." 항목이 본 plan 의 source gap. ship 시 해당 bullet 을 Change log 한 줄로 대체. (Anonymous-object repository wrapper hoisting 부분은 본 plan 의 Out-of-scope — 별도 follow-up plan 후보.)

---

## Change log

- 2026-04-27: Task 1 shipped via #465 — 32-case `NotificationDetailScreenCharacterizationTest` 가 redirect/visibility/empty/click 모두 픽스 (root file 687 lines, refactor 직전 baseline GREEN).
- 2026-04-27: Tasks 2–4 shipped (this PR) — `NotificationDetailScreen.kt` 687 → 271 lines. 신규 sub-file 3개: `NotificationDetailReasonSection.kt` (149 lines, reason card + onboarding card + ReasonSubSection helper), `NotificationDetailDeliverySection.kt` (175 lines, delivery profile + source suppression + ARCHIVED completion), `NotificationDetailReclassifyActions.kt` (289 lines, DetailTopBar + reclassify CTA + sheet/editor host + 2 helper funs). 동작 변경 없음 — characterization tests + full unit suite GREEN. journey notification-detail Known gaps 의 code-health bullet 제거 + Change log 추가. Status flipped to shipped.
