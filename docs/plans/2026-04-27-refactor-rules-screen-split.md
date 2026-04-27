---
status: shipped
shipped: 2026-04-27
superseded-by: ../journeys/rules-management.md
---

# Rules 화면을 sub-section composable 로 split Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Refactor — behavior must not change.

**Goal:** `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt` (1446 lines) 를 의미 단위 sub-file 로 분해해 root 파일이 ~600 lines 이하로 내려가게 한다. 현재 한 파일에 root composable + 14 이상의 `private fun` (UnassignedRuleRowSlot / CategoryAssignSheetContent / RuleEditorAppSuggestionRow / RuleOverrideEditorSection / RuleOverrideBaseDropdown / RepeatBundleThresholdEditor ...) 이 동거. 동작은 변경하지 않는다.

**Architecture:**
- 같은 패키지 (`ui/screens/rules/`) 에 sub-file 3개 신설:
  - `RulesUnassignedSection.kt` — `UnassignedRuleRowSlot` + `CategoryAssignSheetContent` + `CategoryAssignRow` + `categoryActionLabel` (~3 composables + 1 helper, ~250 lines)
  - `RuleEditorOverrideSection.kt` — `RuleOverrideEditorSection` + `RuleOverrideBaseDropdown` + `supersetWarningMessage` + `ruleRowPresentationFor` (~250 lines)
  - `RuleEditorTypeSection.kt` — `RepeatBundleThresholdEditor` + `RuleEditorAppSuggestionRow` + `typeLabel` + `matchLabelFor` (~200 lines)
- root `RulesScreen.kt` 는 `RulesScreen` root composable + dialog state hoisting 만 보유.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit characterization tests.

---

## Product intent / assumptions

- 순수 리팩터. 사용자 가시 동작 / multi-select / draft-park 흐름 / override editor / category assign sheet 모두 변경 없음.
- 시그니처 변경 없음. 호출자 (`AppNavHost.kt` + Settings 의 `AdvancedRulesEntryCard`) 는 무영향.
- carving 단위는 인접 영역 — line 968~1163 = 미분류 + assign sheet, 1173~1292 = repeat bundle / app suggestion, 1315~1414 = override editor.

---

## Task 1: Pin behavior with characterization tests [IN PROGRESS via PR #455]

**Objective:** Refactor 전에 RulesScreen 의 sub-bucket 분리 ("작업 필요" / "보류"), multi-select ActionBar, override editor sheet, category assign sheet 의 가시 affordance 를 테스트로 고정.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/rules/RulesScreenCharacterizationTest.kt`

**Steps:**
1. fake repositories 로 다음 fixture 렌더:
   - 1 active rule + 1 draft=true + 1 draft=false unassigned → "작업 필요" 1건 + "보류" 1건 sub-section label 노출.
   - long-press → ActionBar `1개 선택됨` + `분류 추가` + `취소` assertion.
2. category assign sheet 렌더 → "이 규칙을 어떤 분류에 추가하시겠어요?" + "+ 새 분류 만들기" terminal row assertion.
3. override editor sheet 렌더 → "예외 규칙" switch + base dropdown candidate assertion.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.rules.RulesScreenCharacterizationTest"` → GREEN.

## Task 2: Carve out unassigned + assign sheet

**Objective:** 가장 큰 영역 (unassigned slot + category assign sheet) 을 신규 sub-file 로 이동.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesUnassignedSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`

**Steps:**
1. line 968~1172 영역 (`UnassignedRuleRowSlot`, `CategoryAssignSheetContent`, `CategoryAssignRow`, `categoryActionLabel`) 을 컷-앤-페이스트.
2. visibility `private → internal`.
3. `./gradlew :app:assembleDebug` + Task 1 GREEN.

## Task 3: Carve out editor sub-sections

**Objective:** Override editor + repeat bundle / app suggestion 영역을 두 sub-file 로 분리.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/rules/RuleEditorOverrideSection.kt`
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/rules/RuleEditorTypeSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`

**Steps:**
1. `RuleOverrideEditorSection` + `RuleOverrideBaseDropdown` + `supersetWarningMessage` + `ruleRowPresentationFor` → `RuleEditorOverrideSection.kt`.
2. `RepeatBundleThresholdEditor` + `RuleEditorAppSuggestionRow` + `typeLabel` + `matchLabelFor` → `RuleEditorTypeSection.kt`.
3. `wc -l RulesScreen.kt` ≤ 700 (root + nested startCreate/startEdit local funs + 컴포지션 외 잔존 코드) 검증. 600 미달이면 더 좋음.
4. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 통과.

## Task 4: PR + journey doc note

**Steps:**
1. `./gradlew :app:assembleDebug` GREEN.
2. PR 본문: 신규 파일 3개 + before/after 사이즈 + 동작 변경 없음 + Task 1 characterization GREEN 명시.
3. `rules-management` journey 의 Code pointers 가 줄번호를 박지 않았는지 확인 (현 docs-sync 규칙대로 박혀있지 않으면 변경 불필요).
4. PR 제목 후보: `refactor(rules): split RulesScreen.kt into per-section sub-files`.
5. frontmatter `status: shipped` flip + Change log 에 PR 링크.

---

## Scope

**In:**
- 3개 sub-file 분리.
- `RulesScreen.kt` root size ≤ 700 lines.
- Characterization test.

**Out:**
- 동작 변경 / multi-select state 재설계 / draft-park 흐름 변경.
- Editor 의 dialog → bottom sheet 변환 같은 UX 변경 (Known gap "AlertDialog 좁음" 은 별도 plan).

---

## Risks / open questions

- `internal` 승격 — 같은 패키지의 다른 파일이 의도치 않게 호출하지 않는지 grep.
- Editor 가 `AlertDialog` 안에서 호출되므로 sub-file 로 분리해도 dialog scope 의 모든 import 가 새 파일에 들어가야 한다. 누락 시 빌드 깨짐 — IDE auto-import 로 안전.
- Compose preview 가 root 안에 있으면 sub-file 로 함께 이동.

---

## Related journey

[rules-management](../journeys/rules-management.md) — Observable steps / Exit state / Verification recipe 변경 없음. Code pointers 가 file path 만 갖고 있으면 자동 안전.
