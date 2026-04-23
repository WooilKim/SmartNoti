---
status: planned
---

# Onboarding quick-start: seed Categories alongside Rules

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 첫 사용자가 온보딩의 "이대로 시작할게요" 를 눌렀을 때, Rules DataStore 에 16개 quick-start 룰이 seed 되는 것과 동일한 트랜잭션에서 **Categories DataStore 에도 대응되는 기본 Category 들이 seed** 되도록 한다. 그 결과 Detail "분류 변경" 시트의 경로 A ("기존 분류에 포함") 가 첫 진입에서부터 비어 있지 않고, `home-uncategorized-prompt` 의 cover-가 첫 분류 알림부터 의미 있게 동작하며, [`rules-feedback-loop`](../journeys/rules-feedback-loop.md) 의 Precondition (`최소 하나 이상의 Category 가 존재`) 이 Path B 강제가 아닌 Path A 도 자연스럽게 충족된다.

**Architecture:** 현재 `OnboardingQuickStartSettingsApplier.applySelection()` 은 (a) `OnboardingQuickStartRuleApplier` 로 룰을 만들고 `RulesRepository.upsertRule` 까지 영속화하지만, `CategoriesRepository` 는 호출하지 않는다. Phase P1 (`docs/plans/2026-04-22-categories-split-rules-actions.md`) 이후 Categories 가 분류 결정의 owner 가 됐으므로, quick-start 가 만든 각 Rule 은 그 Rule 만 ruleIds 로 갖는 1:1 Category 로도 함께 등장해야 한다. 새 컴포넌트 `OnboardingQuickStartCategoryApplier` 를 추가해 `Map<OnboardingQuickStartPresetId, RuleUiModel>` 을 입력으로 받아 결정적인 id (`cat-onboarding-<presetId>`) 를 가진 Category 리스트를 만들고, `OnboardingQuickStartSettingsApplier` 가 rule upsert 직후 `categoriesRepository.upsertCategory(...)` 로 한 건씩 영속화한다. 결정적 id 로 idempotency 보장 — 사용자가 (예: `pm clear` 가 아닌 onboarding flag manual reset 같은 미래 시나리오로) 다시 quick-start 를 적용해도 동일한 cat id 가 in-place upsert 되어 중복이 쌓이지 않는다.

**Tech Stack:** Kotlin, AndroidX DataStore (Preferences), JUnit + Robolectric (`RulesRepositoryTest` 와 같은 패턴).

---

## Product intent / assumptions

- **Quick-start 가 만든 Rule 은 각각 독립된 Category 가 된다 (1:1).** 더 적극적인 그룹핑 (예: `중요 알림` Rule + `프로모션 알림` Rule 을 한 Category 로 묶는 카테고라이즈) 은 고민거리지만 본 plan 의 범위 밖 — 사용자가 그렇게 묶고 싶다면 분류 탭에서 직접 합칠 수 있다. 1:1 이 가장 보수적이고 Detail "분류 변경" 의 첫 인상에 정확히 부합 (`중요 알림` 룰 → `중요 알림` Category).
- **Category 이름은 Rule.title 을 그대로 차용**. Quick-start 룰의 `title` 이 이미 사용자에게 보일 카피 (`중요 알림`, `프로모션 알림`, `반복 알림`) 이므로 일관성 유지.
- **Category.action 매핑은 결정적**: `IMPORTANT_PRIORITY` → `PRIORITY`, `PROMO_QUIETING` → `DIGEST`, `REPEAT_BUNDLING` → `DIGEST`. 즉 Rule 의 `RuleActionUi` 와 직접 1:1 (`ALWAYS_PRIORITY → PRIORITY`, `DIGEST → DIGEST`).
- **Category.order 는 preset 의 정의 순서**. `OnboardingQuickStartRuleApplier.orderedPresetIds` 와 동일 순 (`IMPORTANT_PRIORITY=0, PROMO_QUIETING=1, REPEAT_BUNDLING=2`).
- **Category.appPackageName = null** — 이 quick-start 룰들은 모두 KEYWORD/REPEAT_BUNDLE 기반이라 특정 앱 pin 이 부적절.
- **Category id 는 `cat-onboarding-<presetId.lowercase>` 결정적**. 동일 preset 재선택 시 in-place upsert.
- **사용자 판단이 필요한 항목** (Risks 에 옮김): Rule 만 선택 해제했을 때 (mergeRules 가 reused id 를 보존해 Rule 이 사라지지 않음) Category 까지 같이 정리할지 여부 — 본 plan 은 **Category 도 보존** (사용자가 분류 탭에서 명시적으로 삭제하지 않는 한 살림). 한 번 만들어진 Category 가 quick-start 재적용으로 사라지지 않는 게 사용자 멘탈모델에 가까움.

---

## Task 1: Add failing unit test for `OnboardingQuickStartCategoryApplier` contract

**Objective:** RED-first. 새 컴포넌트가 quick-start 가 만든 룰별로 결정적 id / 적절한 action / order / null appPackage / ruleIds=[ruleId] 의 Category 를 produce 한다는 계약 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartCategoryApplierTest.kt`

**Steps:**
1. `OnboardingQuickStartCategoryApplier` (prod) 가 없는 상태에서 다음 케이스로 테스트 작성:
   - `buildCategoriesByPresetId(emptyMap())` → 빈 리스트.
   - `buildCategoriesByPresetId(mapOf(IMPORTANT_PRIORITY to fakeRule("rule-imp")))` → 단일 Category, `id = "cat-onboarding-important_priority"`, `name = "중요 알림"`, `action = PRIORITY`, `ruleIds = ["rule-imp"]`, `order = 0`, `appPackageName = null`.
   - 세 preset 모두 입력 → 세 Category, `order` 0/1/2 순.
   - 동일 입력 두 번 호출 → 결과 동일 (deterministic).
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartCategoryApplierTest"` → 컴파일 실패 (RED).

## Task 2: Implement `OnboardingQuickStartCategoryApplier`

**Objective:** Task 1 RED → GREEN.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartCategoryApplier.kt`

**Steps:**
1. `class OnboardingQuickStartCategoryApplier` 추가. 의존성 없음 (순수 매핑).
2. `fun buildCategoriesByPresetId(rulesByPresetId: Map<OnboardingQuickStartPresetId, RuleUiModel>): List<Category>` — preset 의 결정적 순서대로 매핑.
3. preset → CategoryAction 매핑 헬퍼 (`IMPORTANT_PRIORITY → PRIORITY`, 그 외 → DIGEST). `when` exhaustive 로 누락 방지.
4. id = `"cat-onboarding-${presetId.name.lowercase()}"`.
5. 단위 테스트 GREEN 확인.

## Task 3: Add failing test for applier wiring (Categories upsert called per preset)

**Objective:** `OnboardingQuickStartSettingsApplier.applySelection` 이 quick-start rule upsert 와 동일 호출 안에서 `categoriesRepository.upsertCategory` 를 각 preset 만큼 호출한다는 계약을 RED 로 박는다.

**Files:**
- `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartSettingsApplierTest.kt` (기존 — 테스트 추가)

**Steps:**
1. 기존 테스트가 어떤 fake/mock 패턴을 쓰는지 확인 (`InMemoryCategoriesRepository` 가 이미 있는지 검색; 없으면 fake/spy 추가).
2. 새 테스트 케이스: 세 preset 선택 시 호출 직후 `categoriesRepository.observeCategories().first()` 가 세 Category 를 포함하는지 (id / action / ruleIds 검증). 한 preset 만 선택하면 한 Category 만.
3. 동일 selection 으로 두 번 적용해도 Category 개수가 누적되지 않는다 (idempotent).
4. RED 확인 — 현재 applier 가 categoriesRepository 의존성 자체를 모름.

## Task 4: Wire `CategoriesRepository` into `OnboardingQuickStartSettingsApplier`

**Objective:** Task 3 GREEN 으로 만들기.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartSettingsApplier.kt`
- `app/src/main/java/com/smartnoti/app/AppContainer.kt` (또는 applier 가 인스턴스화되는 DI 지점 — 실제 위치는 grep 으로 확인)

**Steps:**
1. 생성자에 `categoriesRepository: CategoriesRepository` + `categoryApplier: OnboardingQuickStartCategoryApplier` 추가.
2. `applySelection` 안에서 `ruleApplier.upsertOrSkipRules(...)` 가 끝난 직후 (rule id 가 결정된 시점), `quickStartRulesByPresetId` (이미 함수 안에 있는 변수) 로부터 `categoryApplier.buildCategoriesByPresetId(...)` 를 만들어 각 Category 를 `categoriesRepository.upsertCategory(...)` 한다.
3. DI 컨테이너에서 새 의존성을 주입 (`AppContainer` 등에서 이미 살아있는 `CategoriesRepository` singleton 을 재사용).
4. 단위 테스트 GREEN 확인. 기존 onboarding 관련 테스트 전부 그대로 PASS 인지 재실행.

## Task 5: Update journey docs and ship

**Objective:** 사용자 관측 동작이 바뀐 만큼 두 journey 문서를 동기화.

**Files:**
- `docs/journeys/onboarding-bootstrap.md`
- `docs/journeys/rules-feedback-loop.md`

**Steps:**
1. `onboarding-bootstrap.md` Observable steps 6.2 (rule applier) 다음에 6.2.1 정도로 **"OnboardingQuickStartCategoryApplier 가 동일 quick-start 셀렉션으로부터 1:1 Category 들을 만들어 `CategoriesRepository.upsertCategory` 로 영속화한다 (id 결정적, action 매핑은 PRIORITY/DIGEST/DIGEST)"** 한 줄 추가. Exit state 에 "CategoriesRepository 에 quick-start preset 만큼의 Category 가 저장됨" 추가. Code pointers 에 `OnboardingQuickStartCategoryApplier` 추가. Change log 에 오늘 날짜 + 요약 + PR 번호 1줄.
2. `rules-feedback-loop.md` Known gaps 의 "온보딩 quick-start 가 Rules DataStore 에는 16개 룰을 seed 하지만 Categories DataStore 는 비워둠 ..." 항목을 **resolved** 로 마킹 + `→ plan: docs/plans/2026-04-23-onboarding-quick-start-seed-categories.md (#PR)` 링크 부착. Change log 1줄.
3. `last-verified` 는 **이 PR 안에서 직접 verification recipe 를 다시 돌리지 않는 한** 변경하지 않는다. journey-tester 의 다음 sweep 이 갱신.
4. PR 제목: `feat(onboarding): seed Categories alongside Rules in quick-start`.

---

## Scope

**In:**
- 새 컴포넌트 `OnboardingQuickStartCategoryApplier` (pure mapping).
- `OnboardingQuickStartSettingsApplier` 에 `CategoriesRepository` + 새 applier 의존성 주입.
- `applySelection` 내부에서 quick-start rule 영속화 직후 Category 들 영속화 (idempotent, 결정적 id).
- 단위 테스트 2종 (mapping contract + wiring contract).
- 두 journey 문서 동기화.

**Out:**
- 기존 사용자 (이미 onboarding 통과) 의 backfill — 본 plan 은 신규 사용자만 커버. backfill 은 별도 plan 대상.
- Quick-start 외 경로 (`Detail → 분류 변경 → 새 분류`) 의 Category 생성 — 이미 별 경로로 동작 중.
- Categories 의 자동 묶음 / 추천 — 1:1 mapping 만.
- ADB end-to-end 검증을 위한 verification recipe 갱신 — journey-tester 의 다음 sweep.

---

## Risks / open questions

- **Quick-start 재적용 시 사용자가 직접 만든 Category 가 영향을 받는가?** No — 결정적 id (`cat-onboarding-*`) 로만 upsert 하므로 사용자 생성 Category (id 가 다름) 는 무관. 단, **사용자가 quick-start Category 의 이름/action 을 분류 탭에서 수정한 뒤 재적용하면 덮어쓰여진다** — 본 plan 은 quick-start 가 한 번만 (onboarding 도중) 적용된다는 현 동작 가정. 미래에 "quick-start 를 다시 돌릴 수 있게" 한다면 이 부분 재검토 필요.
- **mergeRules 와의 일관성**: `OnboardingQuickStartRuleApplier.mergeRules` 는 reused rule id 를 보존하는 정교한 로직이 있는데 (사용자가 같은 매치값의 룰을 이미 가졌을 때 id 충돌을 피함), Category 쪽도 비슷하게 reused id 를 보존해야 하는가? 본 plan 은 결정적 id 로 단순 upsert — 사용자가 같은 카피 (`중요 알림`) 의 Category 를 직접 만들어둔 경우 별개 entity 로 공존한다. 사용자 멘탈모델에 부합 (자기가 만든 것 vs onboarding 이 만든 것).
- **`Category.appPackageName = null` 결정**: keyword 룰 중심이라 합리적. 미래에 quick-start 가 앱 단위 preset (`KAKAO_QUIETING` 같은) 을 도입하면 그 때 매핑 추가 — 본 plan 의 enum 매핑 위치가 명확해서 확장 부담은 낮다.
- **DI 위치 확인 필요**: `OnboardingQuickStartSettingsApplier` 가 인스턴스화되는 정확한 호출 site (AppContainer 또는 OnboardingScreen 의 Composable graph) 를 Task 4 첫 단계에서 grep 으로 확인하고 그 곳에서 의존성 주입. 위치를 잘못 잡으면 prod 런타임은 깨지지 않지만 fake CategoriesRepository 가 새지 않아 다음 quick-start 호출에서 Category 가 안 만들어질 수 있음.

---

## Related journey

- [`rules-feedback-loop`](../journeys/rules-feedback-loop.md) — Known gaps 의 onboarding-categories drift 항목을 본 plan 이 해소. shipped 시 해당 bullet 을 "resolved" 로 마킹.
- [`onboarding-bootstrap`](../journeys/onboarding-bootstrap.md) — Observable steps / Exit state / Code pointers / Change log 갱신 대상.
- 부수적: [`home-uncategorized-prompt`](../journeys/home-uncategorized-prompt.md) — quick-start preset 이 Categories 에 들어오면 첫 분류된 알림에 대해 "Uncategorized" cover 가 더 정확히 동작 (`UncategorizedAppsDetector` 가 `Category.appPackageName` 만 본다는 별도 known gap 은 직교).
