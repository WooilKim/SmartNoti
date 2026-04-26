---
status: shipped
shipped: 2026-04-26
superseded-by: ../journeys/rules-management.md
---

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** "미분류 상태로 둔 Rule" 의 의미를 사용자 의도에 맞게 분리한다. 현재 사용자가 Rule editor 저장 직후 `CategoryAssignBottomSheet` 에서 "나중에 분류에 추가" 를 누르거나 sheet 를 그대로 닫으면 — Rule 은 어떤 Category 에도 속하지 않는 채 RulesScreen 의 "미분류" 섹션에 영구히 남는다. 미분류 row 를 다시 탭해서 sheet 를 열고 또 다시 닫아도 같은 상태가 silently 유지되어, "이 Rule 은 영원히 미분류로 두겠다" (의도된 보류) 와 "sheet 를 깜빡 닫았다" (실수) 를 구분할 신호가 없다. 이 plan 이 ship 되면 Rule 에 `draft: Boolean` 영속 플래그가 추가되어, 신규 Rule 은 `draft=true` 로 저장되고, 사용자가 sheet 에서 분류를 명시적으로 선택하거나 "나중에 하기" 가 아닌 "보류 (의도적)" 라는 별도 CTA 를 누를 때만 plain unassigned 상태로 전이된다. RulesScreen 미분류 섹션은 두 하위 그룹 ("작업 필요" — draft=true, "보류" — draft=false unassigned) 으로 분리되어 사용자가 실수로 닫은 sheet 를 한눈에 구별할 수 있다.

**Architecture:** 변경의 중심은 `RuleUiModel` + `Rule` 도메인 모델에 `draft: Boolean = false` 필드를 추가하고, `RuleStorageCodec` 의 line-oriented 포맷을 7-column 에서 8-column 으로 확장하는 것이다 (decoder 는 7-column legacy row 를 `draft = false` 로 tolerate 해 기존 사용자 마이그레이션 부담 0). `RulesRepository.upsertRule(rule, isNewlyCreated: Boolean)` 시그니처 확장으로 신규 저장 경로는 `draft = true` 로 강제하고, `CategoriesRepository.appendRuleIdToCategory` 가 호출되면 그 직후 `RulesRepository.markRuleAsAssigned(ruleId)` 가 자동으로 `draft = false` 로 flip 한다 — 두 op 의 순서/원자성은 새 helper `AssignRuleToCategoryUseCase` 가 책임진다. RulesScreen 의 미분류 섹션은 기존 `UnassignedRulesDetector.detect()` 결과를 추가로 partition 해 두 sub-bucket 으로 렌더한다 (pure helper `UnassignedRulesPartitioner` 신설). `CategoryAssignBottomSheet` UI 는 기존 "나중에 분류에 추가" CTA 를 두 개로 분기 — "작업 목록에 두기 (나중에 분류)" 가 default (draft=true 유지), "분류 없이 보류 (의도적)" 가 secondary (draft=false 로 flip + sheet 미표시 transition).

**Tech Stack:** Kotlin, Jetpack Compose, DataStore preferences, Gradle JVM unit tests (Robolectric 불필요 — 모든 변경이 pure helper + DataStore round-trip 으로 fits).

---

## Product intent / assumptions

- 신규 Rule 은 항상 `draft = true` 로 저장된다. 사용자가 단 한 번이라도 sheet 에서 Category 를 선택해 합류시키면 그 Rule 은 영구히 `draft = false`. 다시 분류에서 떼어내도 `draft = true` 로 돌아가지 않는다 (한 번이라도 사용자 의도로 routing 된 적이 있다는 사실은 stable).
- "분류 없이 보류" CTA 를 누른 Rule 은 `draft = false` + 어떤 Category 에도 속하지 않은 상태 — RulesScreen 의 "보류" 서브-버킷에 작은 회색 톤으로 표시된다. 분류기 동작은 plain unassigned 와 동일 (SILENT fall-through) — 이 플래그는 순수 UI/UX hint 이고, 분류 결정에는 영향이 없다.
- "작업 목록에 두기 (나중에 분류)" 는 default CTA — `draft = true` 유지. RulesScreen 의 "작업 필요" 서브-버킷에 강조 톤 + "분류에 추가되기 전까지 비활성" 배너가 그대로 노출된다.
- 미분류 카드 탭 → sheet 재오픈 → 사용자가 다시 닫는 경로에서는 `draft` 플래그가 변경되지 않는다 (이전 상태 유지). "닫음" 자체는 의도 신호로 해석하지 않음.
- Migration: 기존에 "미분류" 로 남아있던 Rule (legacy 7-column row, `draft` 필드 없음) 은 decoder default `draft = false` 로 들어와 자동으로 "보류" 서브-버킷으로 분류된다. 이 데이터는 사용자가 이미 한 번 sheet 를 닫았던 row 이므로 "보류 의도" 로 해석하는 것이 가장 안전한 fallback. 이 결정은 user 확인 필요.
- **사용자 판단 필요**:
  1. "보류" / "작업 필요" 서브-버킷의 정확한 한국어 라벨 — 후보: "작업 필요 / 보류", "분류 대기 / 보류 중", "할 일 / 보관"
  2. Sheet 의 두 CTA 라벨 — "작업 목록에 두기 (나중에 분류)" / "분류 없이 보류" 가 명확한지, 아니면 더 짧게 "나중에" / "보류" 가 좋은지
  3. Migration 정책 — 기존 unassigned Rule 을 "보류" 로 보내는 것이 맞는지, 아니면 일괄 "작업 필요" 로 두고 사용자가 명시 정리하게 할지
  4. "분류 없이 보류" 를 누른 뒤 다시 작업 필요로 되돌리는 경로가 필요한지 (현재 plan 은 "작업 필요로 끌어올리기" 액션을 보류 row 의 longpress menu 로 제공)

---

## Task 1: Pin current contract + new partition behavior with failing tests [SHIPPED via PR #357]

**Objective:** 현재 "신규 Rule 저장 → sheet 닫음 → 영구 미분류" 동작이 새 `draft` 필드 도입 후에도 분류기 측에서 변하지 않음을 회귀 고정. 동시에 새 partition helper 의 contract 를 RED 로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/UnassignedRulesPartitionerTest.kt`
- 보강: `app/src/test/java/com/smartnoti/app/data/rules/RuleStorageCodecTest.kt` (이미 존재한다면 케이스 추가, 없다면 신규)
- 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/RuleWithoutCategoryNoOpTest.kt` — `draft = true` / `draft = false` 어느 쪽이든 owning Category 가 없으면 SILENT fall-through 가 동일하게 발생함을 명시.

**Steps:**
1. `UnassignedRulesPartitionerTest` — 입력 (rules, categories) 에 대해 `partition()` 가 두 list 를 반환:
   - `actionNeeded`: `draft == true` && unassigned
   - `parked`: `draft == false` && unassigned
   각 list 가 입력 순서를 보존하는지, claimed Rule 은 어느 list 에도 안 들어가는지, 빈 입력 케이스 (rules empty / categories empty) 모두 cover.
2. `RuleStorageCodecTest` — (a) `draft = true` round-trip, (b) `draft = false` round-trip, (c) **legacy 7-column row** 입력 시 `draft = false` 로 decode, (d) 8-column row 의 `draft` 컬럼이 잘못된 값 (e.g. 빈 문자열) 일 때 `false` 로 fallback.
3. `RuleWithoutCategoryNoOpTest` — `draft = true` Rule + `draft = false` Rule 이 모두 owning Category 없으면 `RuleCategoryActionIndex.actionFor(...) == null` 임을 단언 (분류기 contract 변경 없음 명시).
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.UnassignedRulesPartitionerTest" --tests "com.smartnoti.app.data.rules.RuleStorageCodecTest" --tests "com.smartnoti.app.domain.usecase.RuleWithoutCategoryNoOpTest"` → 실패 (RED) 확인.

## Task 2: Extend domain model + codec [SHIPPED via PR #357]

**Objective:** `RuleUiModel` / `Rule` 에 `draft: Boolean = false` 필드 추가, `RuleStorageCodec` 8-column 포맷 확장 + legacy 7-column tolerance.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/model/RuleUiModel.kt`
- `app/src/main/java/com/smartnoti/app/domain/model/Rule.kt`
- `app/src/main/java/com/smartnoti/app/data/rules/RuleStorageCodec.kt`

**Steps:**
1. `RuleUiModel` 과 `Rule` 양쪽에 `val draft: Boolean = false` 필드 추가. KDoc 에 "신규 저장 직후 true; 사용자가 한 번이라도 Category 에 합류시키면 영구히 false; '보류' CTA 를 누른 경우도 false" 명시.
2. `RuleStorageCodec.encode` 가 8번째 컬럼으로 `rule.draft.toString()` 을 추가.
3. `RuleStorageCodec.decode` 가:
   - `parts.size >= 8` (post-Task-2 신규 layout, index 4 == "true"/"false") → index 7 을 `toBooleanStrictOrNull() ?: false` 로 읽어 `draft` 채움.
   - `parts.size == 7` (post-P1-Task-4 legacy without draft) → `draft = false`.
   - `parts.size >= 8` 이지만 index 4 가 boolean 이 아닌 경우 (pre-Phase-P1 8-column with action) → 기존 `removeAt(4)` 후 7 columns → `draft = false`.
   - `parts.size >= 9` (legacy 8-column with action + draft 가 동시에 존재하는 가상 케이스) → `removeAt(4)` 후 8 columns → index 7 을 `draft` 로.
4. Task 1 의 `RuleStorageCodecTest` GREEN 확인.

## Task 3: Repository setter + AssignRuleToCategoryUseCase [SHIPPED via PR #357]

**Objective:** `RulesRepository` 에 `markRuleAsAssigned(ruleId)` + `markRuleAsParked(ruleId)` 두 setter 추가, `AssignRuleToCategoryUseCase` 가 두 repository op 의 순서/원자성을 책임.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/rules/RulesRepository.kt`
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/AssignRuleToCategoryUseCase.kt`
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/AssignRuleToCategoryUseCaseTest.kt`

**Steps:**
1. `RulesRepository` 에 두 suspend 메서드 추가:
   - `markRuleAsAssigned(ruleId: String)` — 해당 id 의 Rule 을 `draft = false` 로 update + persist (id 가 없으면 no-op).
   - `markRuleAsParked(ruleId: String)` — 동일하게 `draft = false` 로 flip. (이름은 다르지만 storage 효과는 같음 — 향후 분리 가능성에 대비한 별도 entry point.)
2. `AssignRuleToCategoryUseCase(rulesRepository, categoriesRepository)` 의 `assign(ruleId, categoryId)`:
   - `categoriesRepository.appendRuleIdToCategory(categoryId, ruleId)` 호출
   - 성공 시 `rulesRepository.markRuleAsAssigned(ruleId)` 호출
   - 두 op 모두 suspend; 첫 번째가 throw 하면 두 번째는 호출하지 않음 (Rule 은 draft 로 남아 사용자가 재시도 가능).
3. `AssignRuleToCategoryUseCaseTest` — fake repository 두 개로 (a) 정상 흐름에서 두 op 모두 호출됨, (b) appendRuleIdToCategory 가 throw 하면 markRuleAsAssigned 호출 안 됨, (c) 이미 다른 Category 에 속한 Rule 을 또 assign 해도 두 op 모두 동일 순서로 호출됨 확인.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.AssignRuleToCategoryUseCaseTest"` GREEN.

## Task 4: Wire UseCase into RulesScreen + new "보류" CTA [SHIPPED via PR #357]

**Objective:** `CategoryAssignBottomSheet` 의 기존 "나중에 분류에 추가" 한 개 CTA 를 두 개 ("작업 목록에 두기" default + "분류 없이 보류" secondary) 로 분기. Category 선택 경로가 `AssignRuleToCategoryUseCase.assign(...)` 을 호출하도록 wiring 변경.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`
- 필요 시 ViewModel 또는 동등 호출자 (RulesViewModel 가 있다면).

**Steps:**
1. `RulesScreen` 의 sheet 호출부 (`CategoryAssignSheetContent` 옆) 에서 Category 선택 콜백을 `assignRuleToCategory(rule.id, categoryId)` 로 교체 — `AssignRuleToCategoryUseCase` 인스턴스를 ViewModel / `remember` 로 노출.
2. Sheet 의 "나중에 분류에 추가" CTA 를 두 개로 분리:
   - **작업 목록에 두기** (primary tonal, default focus) — sheet 만 dismiss, draft 변경 없음 (기존 동작 보존).
   - **분류 없이 보류** (secondary text-only) — `rulesRepository.markRuleAsParked(rule.id)` 호출 후 dismiss.
3. 두 CTA 모두 dismiss 후 sheet 자동 재오픈 없음 (사용자가 row 를 탭해야 다시 노출).
4. Compose 단위 테스트 또는 lightweight contract test 는 ViewModel layer 까지만 — Compose preview 로 두 CTA 가 모두 노출되는지 시각 확인 (PR 에 스크린샷 첨부).

## Task 5: Partition unassigned section into two sub-buckets [SHIPPED via PR #357]

**Objective:** RulesScreen 의 기존 단일 "미분류" 섹션을 "작업 필요" + "보류" 두 sub-section 으로 분리. Empty sub-bucket 은 헤더도 노출하지 않음.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/UnassignedRulesPartitioner.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/ui/components/RuleRow.kt` — `RuleRowPresentation.Unassigned` 에 `isParked: Boolean` 파라미터 추가해 chip 톤/배너 카피 분기.

**Steps:**
1. `UnassignedRulesPartitioner.partition(rules, categories): Partition` — `Partition(actionNeeded: List<RuleUiModel>, parked: List<RuleUiModel>)` data class 반환. 내부적으로 `UnassignedRulesDetector.detect()` 호출 후 `draft` 로 split.
2. RulesScreen 의 "미분류" 섹션 자리에서:
   - `actionNeeded` 가 비어있지 않으면 "작업 필요" 헤더 + 강조 톤 chip + "분류에 추가되기 전까지 비활성" 배너로 렌더 (기존 동작 그대로).
   - `parked` 가 비어있지 않으면 "보류" 헤더 + 회색 톤 chip + "사용자가 보류함 — 필요 시 작업으로 끌어올리세요" 배너로 렌더.
   - 두 list 모두 비어있으면 어떤 헤더도 노출 안 함 (기존 unconditional 헤더 노출 회귀 방지).
3. "보류" row 의 longpress menu 또는 chip 측 secondary action 으로 "작업으로 끌어올리기" 제공 → ViewModel 에서 `rulesRepository.upsertRule(rule.copy(draft = true))` 호출.
4. Task 1 의 `UnassignedRulesPartitionerTest` GREEN 확인.

## Task 6: Update journey docs [SHIPPED via PR #357]

**Files:**
- `docs/journeys/rules-management.md`
  - Observable steps 6 의 "미분류 draft 로 남고" 문장을 두 갈래로 분리: "작업 목록에 두기" → draft=true 유지, "분류 없이 보류" → draft=false 로 flip.
  - Observable steps "미분류 섹션" 설명을 "작업 필요 + 보류 두 sub-section" 으로 갱신.
  - Code pointers 에 `UnassignedRulesPartitioner`, `AssignRuleToCategoryUseCase` 추가.
  - Tests 에 `UnassignedRulesPartitionerTest`, `AssignRuleToCategoryUseCaseTest` 추가.
  - Known gaps 의 "**신규 미분류 Rule sheet 거부 흐름**" 항목을 (resolved YYYY-MM-DD, plan `2026-04-26-rule-explicit-draft-flag`) 로 갱신 — 단, Change log 자체는 plan-implementer 가 ship 일자에 추가.
  - Verification recipe 갱신: skip 경로를 "작업 목록에 두기" / "분류 없이 보류" 두 분기로 cover.

## Task 7: Self-review + PR [SHIPPED via PR #357]

- 모든 단위 테스트 통과 (`./gradlew :app:testDebugUnitTest`).
- ADB end-to-end: (a) 신규 Rule 저장 → 두 CTA 모두 노출 확인, (b) "작업 목록에 두기" 누른 후 RulesScreen "작업 필요" 섹션에 표시 확인, (c) 같은 Rule 카드 탭 → sheet 재오픈 → "분류 없이 보류" 누름 → "보류" 섹션으로 이동 확인, (d) "보류" 카드 longpress → "작업으로 끌어올리기" → 다시 "작업 필요" 섹션으로 이동 확인, (e) Category 합류 후 Rule 이 양 섹션에서 모두 사라지는지 확인. 결과를 PR 본문에 첨부.
- PR 제목: `feat(rules): explicit draft flag separates "작업 필요" and "보류" unassigned rules`.

---

## Scope

**In scope:**
- `RuleUiModel` / `Rule` 에 `draft: Boolean` 필드 추가.
- `RuleStorageCodec` 8-column 확장 + legacy 7-column / 8-column-with-action tolerance.
- `RulesRepository` setter (`markRuleAsAssigned` / `markRuleAsParked`).
- `AssignRuleToCategoryUseCase` (두 repository op 의 ordered call).
- RulesScreen 의 "미분류" 섹션 partition + 두 sub-bucket UI.
- `CategoryAssignBottomSheet` 의 두 CTA 분기.
- Journey 문서 갱신.

**Out of scope:**
- Bulk 미분류 분류 흐름 (별도 Known gap, 별도 plan 으로).
- "보류" Rule 에 대한 분류기 동작 변경 (이 plan 은 순수 UI/UX hint — 분류 결정에 영향 없음).
- Sheet 닫음 자체를 명시 신호로 해석하기 (X button / outside tap 둘 다 draft 상태 유지).
- Onboarding quick-start preset 의 draft 처리 — 기존 코드 경로 (`OnboardingQuickStartSettingsApplier`) 가 `draft = false` 로 바로 합류시키므로 기존 동작 유지 (변경 없음).

## Risks / open questions

- **Migration 정책 (반드시 user 확인)**: 기존 unassigned Rule 을 일괄 `draft = false` (보류) 로 두는 것이 안전한지, 아니면 `draft = true` (작업 필요) 로 두고 사용자가 명시 처리하게 할지. 후자가 안전하지만 기존 사용자에게 갑자기 "작업 필요" 섹션이 길게 노출되어 피로감을 줄 수 있음.
- **두 CTA 라벨 카피 (반드시 user 확인)**: "작업 목록에 두기 (나중에 분류)" vs "분류 없이 보류" 가 사용자에게 명확하게 다른 의미로 읽히는지. 카피가 모호하면 본 plan 의 핵심 가치가 사라짐.
- **Sub-bucket 라벨 카피 (반드시 user 확인)**: "작업 필요 / 보류" 라는 2-tier 분류가 한국어 UI 컨벤션에 맞는지.
- **"작업으로 끌어올리기" 액션 위치**: 보류 row 의 longpress menu 에 두는 것이 발견 가능한지. AssistChip 옆 secondary text button 으로 두는 것이 더 명시적일 수 있음 — UI 디자인 확인 필요.
- **Storage backward compat**: 7-column → 8-column 전환은 decoder 가 tolerate 해 신규 빌드 다운그레이드만 위험하다 (구 빌드는 8th column 을 무시). 다운그레이드 시 사용자가 명시 보류한 Rule 이 다시 "미분류 단일 섹션" 에 나타나지만 분류 동작은 영향 없음 — acceptable.
- **`Rule` (도메인) vs `RuleUiModel` 의 draft 필드 동기화**: 두 모델이 별도로 존재하므로 둘 다 필드를 추가해야 한다. 추후 두 모델을 통합한다면 별도 plan.
- **`appendRuleIdToCategory` 가 이미 다른 호출 사이트에서 직접 호출되는지** 확인 필요 — RulesScreen 외에 호출자가 있다면 모두 `AssignRuleToCategoryUseCase` 경유로 전환해야 draft flip 이 누락되지 않는다 (Task 4 자체 점검 항목).

## Related journey

- [rules-management](../journeys/rules-management.md) — Known gap "**신규 미분류 Rule sheet 거부 흐름**: 사용자가 Rule 을 저장 후 sheet 에서 '나중에 분류에 추가' 를 눌러 Rule 을 미분류로 남기고, 다시 sheet 를 열기 위해 미분류 row 를 탭한 뒤 또 닫으면 — 미분류 상태가 silently 유지된다. 명시적 '이 Rule 은 영원히 미분류로 두겠다' 의도와 'sheet 를 깜빡 닫았다' 를 구분할 신호는 아직 없음. 후속 plan: explicit `draft: Boolean` storage flag." 해소 대상.
