---
status: planned
created: 2026-04-24
---

# Rule Editor — Remove Action Dropdown + Force Category Assignment (D3)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Tests-first; UI/nav changes land last.

**Goal:** "고급 규칙 편집" 의 Rule editor 에서 액션 dropdown 을 완전히 제거한다. Rule 저장 직후 사용자에게 "이 조건을 어떤 분류에 추가하시겠어요?" 를 묻는 `CategoryAssignBottomSheet` (또는 동등한 어포던스) 를 강제 노출해, 분류(원인) 와 액션(결과) 의 소유 관계가 UI 에서도 명확해지도록 한다. 사용자는 더 이상 "Rule 에 액션을 박는다" 는 잘못된 mental model 에 노출되지 않으며, 새 Rule 은 분류로 explicit 하게 옮기기 전까지 어떤 알림도 분류하지 않는 "**미분류**" draft 상태로 머문다. 기존 Rule-Category 1:1 link 는 마이그레이션으로 보존된다.

**Architecture:**
- `RulesScreen.kt` 의 rule editor AlertDialog 에서 `SectionLabel(title = "처리 방식")` (line 458) + `EnumSelectorRow(... RuleActionUi ...)` (line 464) + 파일 하단의 `actionLabel(RuleActionUi)` helper (line 714) 를 제거. `draftAction` state + `RuleDraftFactory.create(... action = ...)` 호출 인자도 함께 정리.
- 저장 buttom 의 `confirmButton` onClick 에서 1:1 companion Category auto-upsert 블록 (line 534–560) 을 제거. 대신 `rulesRepository.upsertRule(newRule)` 만 수행하고, 저장 직후 새 composable state 로 `CategoryAssignBottomSheet` (또는 navigate-to-Categories) 를 트리거.
- Rule 의 "미분류" 상태는 storage 모델에 새 flag 를 두지 않고 **derive** 한다 — 어떤 Category 의 `ruleIds` 에도 속하지 않은 Rule 은 자동으로 미분류로 간주. `RuleCategoryActionIndex` 가 owning Category 를 못 찾으면 `null` action → classifier 에서 무시 (no-op) → 알림이 분류되지 않음. 단 open question 으로 "explicit `draft: Boolean` flag 를 둘지" 를 보존 (아래 Risks 참조).
- 마이그레이션: 앱 upgrade 시 기존 Rule 들은 이미 `cat-from-rule-<ruleId>` Category 로 연결돼 있으므로 자동 보존. 별도 코드 변경 없음 — 다만 `RulesDataMigrationGuardTest` (가칭) 로 "이번 PR 코드가 1:1 link 를 깨지 않는다" 를 회귀 고정.
- 기존 `RuleActionUi` enum 자체는 여전히 다른 곳 (RulesScreen 의 filter chip, `RuleListGroupingBuilder`) 에서 owning Category.action 을 derive 해 표시하는 용도로 사용됨 — 제거하지 않는다. 제거하는 것은 **editor dialog 의 selector** 만.

**Tech Stack:** Kotlin, Jetpack Compose (`AlertDialog` / `ModalBottomSheet`), DataStore (`RulesRepository`, `CategoriesRepository`), Gradle unit tests.

---

## Product intent / assumptions

- **사용자가 결정한 D3 방향 (확정):** action dropdown 을 Rule editor 에서 **완전히** 제거한다. Hide / disable 가 아닌 hard remove. 이 결정은 plan-implementer 단계에서 재논의하지 않는다.
- **"원인 → 결과" mental model:** Rule = 매처 (조건). Category = 처리기 (액션). UI 가 이 분리를 reinforce 해야 사용자 신뢰가 쌓인다. action dropdown 은 그 모델을 silent magic 으로 깨고 있던 leak.
- **신규 Rule 은 미분류 draft:** "Rule 만 만들고 분류에 안 넣는" 상태가 처음으로 perfectly valid 한 1급 상태가 된다. 그 동안에는 어떤 알림도 분류하지 않는다 (no-op). Classifier hot path 는 이미 `RuleCategoryActionIndex.lookup(rule.id)?.action` 형태이므로 자연 동작.
- **저장 후 Category sheet 는 강제 노출:** 사용자가 sheet 를 닫더라도 Rule 자체는 이미 저장됨 (미분류 draft 로 남음). 강제는 "노출" 까지지 "선택까지 강제" 가 아님 — Cancel 가능, 단 사용자가 의도적으로 분류를 미루는 흐름이 됨.
- **기존 Rule 은 깨지지 않음:** Phase P1 의 `RuleToCategoryMigration` 이 이미 1:1 마이그레이션을 끝낸 상태. 이 PR 은 Rule editor 의 UX 만 바꾼다. 데이터 모델 변경 없음.
- **사용자 결정이 필요한 두 지점은 plan-implementer 가 결정하지 않고 보존:** (a) "미분류" 상태를 explicit flag 로 둘지 derive 로 둘지, (b) Category assignment 어포던스가 inline bottom sheet 인지 full nav 인지 — 두 항목 모두 Risks / open questions 에 명시.

---

## Task 1: Failing tests for "rule with no owning Category is no-op" + draft factory drops action

**Objective:** Storage / classifier 계약을 테스트로 먼저 고정해 후속 코드 변경이 회귀 없이 진행되도록.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/RuleWithoutCategoryNoOpTest.kt`
- 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/RuleDraftFactoryTest.kt` (있으면) 또는 신규
- 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/RuleCategoryActionIndexTest.kt` (있으면) 또는 신규

**Steps:**
1. `RuleWithoutCategoryNoOpTest` — 입력: `RuleUiModel` 1건 + `categories = emptyList()` + 매칭되는 `NotificationInput`. 기대: classifier (`NotificationClassifier.classify(...)` 또는 `ClassifyNotificationUseCase`) 가 분류를 내지 않거나 `NotificationDecision.NONE` 류를 반환 — 현 구현이 이미 그렇게 동작한다는 것을 회귀 고정.
2. `RuleDraftFactoryTest` — `RuleDraftFactory.create(...)` 가 더 이상 `action` 파라미터를 받지 않는 시그니처임을 fail-fast 로 고정 (이 단계는 RED — Task 2 에서 시그니처 변경 후 GREEN).
3. `RuleCategoryActionIndexTest` — `lookup(ruleId)` 가 어떤 Category 에도 속하지 않은 ruleId 에 대해 `null` 을 반환하는지.
4. `./gradlew :app:testDebugUnitTest` 로 RED 확인.

---

## Task 2: Drop `action` from RuleDraftFactory + RulesScreen draft state (storage-level removal)

**Objective:** Task 1 의 RED 를 GREEN 으로. Rule 저장 경로에서 action 입력 자체를 제거.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/usecase/RuleDraftFactory.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`
- 그 외 `RuleDraftFactory.create(... action = ...)` 호출부 전부 (예: 자동 룰 생성 경로, onboarding preset)

**Steps:**
1. `RuleDraftFactory.create(...)` 시그니처에서 `action: RuleActionUi` 제거. `RuleUiModel` 자체는 이미 action 필드가 없음 (Phase P1 Task 4 결과) — factory 내부 mapping 만 정리.
2. `RulesScreen.kt` 에서 `var draftAction by remember { mutableStateOf(...) }` 선언 + 모든 read 지점 제거.
3. 호출부 컴파일 에러를 따라가며 인자 정리 — onboarding preset 생성기, feedback loop, override superset preview 등.
4. `./gradlew :app:testDebugUnitTest` 통과 확인.

---

## Task 3: Remove action dropdown UI block + actionLabel helper from RulesScreen

**Objective:** 사용자가 보는 dropdown 자체를 제거. 지금까지의 "처리 방식" SectionLabel + EnumSelectorRow 가 사라진다.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`

**Steps:**
1. line 458 `SectionLabel(title = "처리 방식")` 제거.
2. line 459–474 의 코멘트 + `EnumSelectorRow(options = listOf(RuleActionUi.ALWAYS_PRIORITY, ...), ...)` 블록 제거.
3. line 714–722 의 `private fun actionLabel(action: RuleActionUi): String = when ...` helper 제거 — 이 helper 는 editor 내부에서만 호출됐는지 grep 으로 확인 후 제거 (만약 다른 곳에서 쓰면 그 호출부도 정리 또는 별도 위치로 이동).
4. 컴파일 통과 + 단위 테스트 통과 확인.
5. **주의 — 제거하지 말 것:** 같은 화면 위쪽의 filter chip / list grouping 의 action chip 은 owning Category 의 action 을 `RuleCategoryActionIndex` 로 derive 해 보여주는 별개 코드 — 손대지 않는다.

---

## Task 4: Remove auto-upsert companion Category block in confirmButton onClick

**Objective:** "1:1 silent magic" 제거. Rule 저장 시 Category 를 자동으로 만들지 않는다.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`

**Steps:**
1. `confirmButton = { Button(onClick = { ... }) }` 안의 line 534–560 (`val draftCategoryAction = draftAction.toCategoryActionOrNull()` 부터 시작해 `categoriesRepository.upsertCategory(category)` 까지) 블록 전체 제거.
2. `scope.launch { repository.upsertRule(newRule) }` 만 남기고, 그 직후 새 composable state (예: `var pendingCategoryAssignmentRuleId by remember { mutableStateOf<String?>(null) }`) 를 set — Task 5 의 sheet 트리거가 이 state 를 구독.
3. `toCategoryActionOrNull()` extension 이 다른 호출부에 없으면 함께 제거.
4. **편집 (existing rule) 경로 보존 확인:** 이번 PR 은 신규 Rule 의 draft 흐름만 미분류로 만든다. **기존 Rule 을 편집해 저장하는 경우** companion Category 가 이미 있을 가능성이 높음 — 그 Category 의 `ruleIds` link 는 손대지 않는다 (auto-upsert 로직만 제거하면 이미 존재하는 Category 는 그대로 유지됨). 단위 테스트 (Task 1 + Task 2) 로 회귀 고정.

---

## Task 5: After-save Category assignment affordance (sheet OR navigate)

**Objective:** Rule 저장 직후 사용자가 즉시 분류로 이동시킬 수 있게.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt` — sheet 트리거
- 신규 또는 기존 재사용: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryAssignBottomSheet.kt` (없다면 신규, 있으면 재사용)

**Design (open question 의 두 옵션 중 하나 — Risks 참조):**

**Option A (Inline bottom sheet, 권장 default):**
- `ModalBottomSheet` 을 RulesScreen 안에서 호스트.
- Header 카피: "이 조건을 어떤 분류에 추가하시겠어요?"
- Body: 기존 Category 목록 + "**새 분류 만들기**" CTA. 선택 시 `CategoriesRepository.upsertCategory(...)` 로 ruleIds 에 새 Rule.id 를 append 해 link.
- "나중에" / 닫기 버튼 → sheet 닫고 Rule 은 미분류 draft 로 남음.

**Option B (Full nav to Categories tab):**
- `navController.navigate(Routes.Categories.create(highlightUnassignedRuleId = newRule.id))` 로 분류 탭으로 이동.
- 분류 탭이 highlight 모드로 진입해 사용자에게 "새 매처가 미분류 상태입니다 — 분류에 추가하세요" banner 노출.

**Steps:**
1. Plan-implementer 가 Option A/B 중 하나를 사용자 컨펌 후 구현. Default 추천: A (모달 시트가 컨텍스트 보존이 가장 강함).
2. Sheet/screen 의 "새 분류 만들기" CTA 는 기존 `CategoryEditorScreen` 의 신규 모드를 재사용 — Rule pre-selected 상태로.
3. 사용자가 Cancel 해도 Rule 은 이미 저장된 상태 (미분류 draft). Toast 같은 명시적 피드백으로 "**미분류 규칙으로 저장됐어요** — 분류 탭에서 나중에 추가할 수 있어요" 안내.

---

## Task 6: Surface "미분류 규칙" 시각 affordance in RulesScreen + CategoriesScreen

**Objective:** 사용자가 미분류 Rule 의 존재를 인지할 수 있어야 한다 — 안 그러면 "왜 내 룰이 안 먹어요?" 가 다음 issue 가 됨.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt` — 미분류 row 에 chip 추가
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt` — Category 목록 상단에 "미분류 규칙 N개" 안내 카드 (선택적)
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/UnassignedRulesDetector.kt` (pure helper)

**Design:**
- RulesScreen: Rule row 에서 owning Category 가 없으면 기존 action chip 자리에 "**미분류**" border-only chip 을 표시 (회색 톤). 탭하면 Task 5 의 sheet/nav 가 다시 열린다.
- CategoriesScreen (선택적, plan-implementer 판단): 미분류 Rule 이 존재하면 상단에 "미분류 규칙 N개 — 분류에 추가하세요" 카드.
- `UnassignedRulesDetector` 는 pure 함수: `(rules: List<RuleUiModel>, categories: List<Category>) -> List<RuleUiModel>` — 어떤 Category 의 `ruleIds` 에도 속하지 않은 Rule 들 반환.

**Steps:**
1. `UnassignedRulesDetectorTest` 먼저 작성 (RED).
2. detector 구현 (GREEN).
3. RulesScreen 의 `RuleRow` presentation 빌더에 "미분류" 분기 추가 — 기존 `RuleListGroupingBuilder` 가 미분류 rule 을 별도 그룹 ("미분류") 으로 emit 하도록 확장 가능.
4. ADB 검증 — 미분류 chip 이 새 Rule 저장 직후 보이는지.

---

## Task 7: Update journey docs + close known-gap

**Files:**
- `docs/journeys/rules-management.md` — Observable steps 6 의 "액션 선택 (...) UI-level 편의 필드. 저장 시 ... 1:1 Category 가 자동 upsert ..." 문단을 "액션은 분류 (Category) 가 소유. Rule 저장 직후 `CategoryAssignBottomSheet` (또는 분류 탭 이동) 가 강제 노출되어 분류에 추가 ..." 로 재작성. Code pointers 에서 `actionLabel` / `EnumSelectorRow(RuleActionUi)` 항목 제거. Known gaps 의 "**Rule 조건 편집 시 action 변경**" 항목은 액션 dropdown 자체가 사라졌으므로 resolved 표기. Change log 신규 entry 추가.
- `docs/journeys/categories-management.md` — "미분류 Rule 진입점" Observable step 추가 (필요 시). Change log entry.
- `docs/journeys/notification-capture-classify.md` — "owning Category 가 없는 Rule 은 no-op" 명시 (이미 그렇지만 doc-level 에 노출).
- `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md` — frontmatter `status: shipped` + `superseded-by: docs/journeys/rules-management.md` (이 PR 머지 시).

**Steps:**
1. Journey 의 Goal / Trigger / Exit state 는 거의 그대로 유지. Observable steps 만 갱신.
2. `last-verified` 는 ADB 재검증 전까지 갱신하지 않는다 (per `.claude/rules/docs-sync.md`).

---

## Scope

**In:**
- Rule editor AlertDialog 의 action dropdown + 라벨 helper + 1:1 Category auto-upsert 제거.
- Rule 저장 후 Category assignment 어포던스 추가 (sheet 또는 nav, plan-implementer 컨펌 후 결정).
- "미분류 Rule" 상태의 시각 surfacing (RulesScreen 미분류 chip + 선택적 CategoriesScreen 안내 카드).
- 관련 journey 문서 갱신.

**Out:**
- `RuleActionUi` enum 자체 제거 — filter chip / grouping 에서 derive 해 표시하는 용도로 여전히 사용. 별도 plan.
- Category assignment 의 bulk 모드 (여러 미분류 Rule 한꺼번에 분류에 추가) — 후속 plan.
- "미분류 Rule" 의 자동 분류 추천 (ML/휴리스틱) — 후속 plan.
- 마이그레이션 코드 변경 — 기존 1:1 link 는 이미 `RuleToCategoryMigration` 이 처리 완료. 이번 PR 은 UX 만.
- Override / nested rule 흐름 — 이번 PR 은 base rule 의 action 입력 제거에만 집중. Override 는 base 를 따라가므로 자연 처리.

---

## Risks / open questions

- **(open) "미분류" 상태의 storage 표현 — 사용자 결정 필요:** 두 옵션:
  - **(A) Derive (권장 default):** 어떤 Category 의 `ruleIds` 에도 없는 Rule = 미분류. 별도 flag 없음. 단순함, 마이그레이션 불필요. 단점: "사용자가 의도적으로 미분류로 둔 Rule" 과 "실수로 link 가 끊긴 Rule" 을 구분 못함.
  - **(B) Explicit `draft: Boolean` flag 를 RuleUiModel + 저장 codec 에 추가:** 명시성 ↑, 의도/실수 구분 가능. 단점: 마이그레이션 코드 + codec 버전업 필요. 신규 사용자에겐 추가 가치 적음.
  - 추천 default 는 A. 사용자가 B 를 원하면 plan-implementer 가 추가 task 로 codec 마이그레이션 + 테스트 작성.
- **(open) Category assignment 어포던스 — UI 결정 필요:** Task 5 의 Option A (`ModalBottomSheet`) 또는 Option B (full nav to Categories tab + highlight banner). 추천 default 는 A — 컨텍스트 보존 + 빠른 의사결정 흐름. 사용자가 B 를 원하면 highlight 파라미터 추가 + nav 라우트 변경 필요.
- **자동 룰 생성 경로의 영향:** `rules-feedback-loop.md` 의 자동 person rule 생성 (`RulesRepository.upsertRule(person:...)`) 도 이 변경의 영향권. 자동 생성된 Rule 은 Category 가 없으므로 미분류로 떨어진다 — 의도된 동작인지 사용자 확인 필요. 만약 "자동 생성된 Rule 은 그대로 분류돼야 한다" 면 별도 path 에서 default Category (예: "사람 알림") 에 자동 link 하는 로직이 필요. **이 결정은 본 plan 의 scope 가 아니지만 plan-implementer 가 사용자에게 reminder 로 제기할 것.**
- **Onboarding preset 의 영향:** Onboarding 이 quick-start preset rule 들을 생성한다면, 그 Rule 들은 이미 Category 와 함께 묶여 생성될 것 (기존 마이그레이션 / preset 코드가 그렇게 동작). 회귀 없는지 확인 필요.
- **Toast / 안내 카피의 사용자 친화성:** "미분류 규칙으로 저장됐어요" 카피는 plan-implementer 가 한국어 UX writing 관점에서 1차 결정 + 사용자 리뷰. ("미분류 규칙" / "분류 안 된 규칙" / "draft 규칙" 등 후보).
- **편집 모드 (기존 Rule 편집) 에서 sheet 강제 노출은 적절한가:** 기존 Rule 은 이미 Category 에 묶여 있을 가능성 높음. 편집 저장 후에도 sheet 를 강제로 띄우면 노이즈. 권장: **신규 Rule (editingRule == null) 일 때만 sheet 노출, 편집 모드는 기존대로 dialog 닫고 끝.** 이 분기는 plan-implementer 가 자연스럽게 구현.
- **Recipe 회귀:** 기존 `rules-management.md` verification recipe 가 "키워드 인증번호 + 액션 즉시 전달 저장" 흐름을 가정. 이 plan 머지 후 recipe 도 "키워드 인증번호 저장 → sheet 에서 기존 분류에 추가" 로 갱신 필요. Task 7 에 포함.

---

## Related journey

- [rules-management](../journeys/rules-management.md) — 가장 큰 영향. Observable steps 6 + Code pointers + Known gaps 의 "Rule 조건 편집 시 action 변경" 항목을 본 plan 이 해소.
- [categories-management](../journeys/categories-management.md) — "미분류 Rule" 진입점이 새로 추가됨.
- [notification-capture-classify](../journeys/notification-capture-classify.md) — 미분류 Rule 의 no-op 동작을 doc-level 에 명시.
- [rules-feedback-loop](../journeys/rules-feedback-loop.md) — 자동 person rule 생성 경로의 미분류 영향 확인.

이 plan 이 shipped 되면 위 journey 들의 해당 Observable steps / Known gaps 를 갱신하고 Change log 에 본 plan 링크를 남긴다.
