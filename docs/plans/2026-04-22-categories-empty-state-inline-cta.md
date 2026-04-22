---
status: planned
---

# 분류 탭 Empty State — Inline CTA 추가

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 분류 탭에 진입했는데 아직 분류가 하나도 없는 사용자가, "분류를 어떻게 만들지" 에 대한 시각적 길 안내를 받지 못해 우하단 FAB 를 놓치는 문제를 해결한다. Empty state 중앙에 **"새 분류 만들기"** 일차 버튼을 추가해, 신규 사용자가 EmptyState 카피를 읽은 흐름 그대로 탭 한 번으로 에디터에 진입할 수 있게 한다.

**Architecture:** `ui/components/EmptyState` 는 현재 `title` / `subtitle` / `icon` 만 받는 순수 표현 컴포넌트이다. 여기에 **옵셔널 `action: (@Composable () -> Unit)?`** 슬롯을 추가해 하위 호환을 유지하면서, `CategoriesScreen` 이 비었을 때 해당 슬롯에 `FilledTonalButton` (또는 `Button`) + "새 분류 만들기" 를 전달한다. 버튼 onClick 은 기존 FAB 와 동일하게 `editorTarget = CategoryEditorTarget.New` 로 설정 → 기존 `CategoryEditorScreen` 다이얼로그가 그대로 뜬다. FAB 는 유지 (분류가 몇 개 있는 normal state 에서는 여전히 1급 entry). Known gap 1건은 없고 (journey `Known gaps` 에 empty-state CTA 누락 항목 자체가 아직 기재돼 있지 않음) — journey 에는 이 변경을 Change log + Observable step 5 에 살짝 덧붙인다.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Compose UI Test (`createComposeRule` + semantics).

---

## Product intent / assumptions

- Empty state 는 신규 사용자가 **가장 먼저 보는 분류 탭의 얼굴**이므로, "무엇을 할지" 를 설명하는 카피 바로 아래에 **행동 유도 버튼**이 있어야 한다. FAB 는 fixed position 이라 신규 사용자의 시선 흐름 (Header → EmptyState 카피) 에서 벗어나 있다.
- 버튼 카피는 **"새 분류 만들기"** — 현재 FAB 의 `"새 분류 추가"` 와 살짝 다르게 두어 "empty 상태에서의 처음 한 번" 임을 드러낸다. 이 카피 차이가 과하다고 판단되면 `"새 분류 추가"` 로 통일하는 것도 가능 — 구현자가 UX 규칙(`.claude/rules/ui-improvement.md`)에 맞게 결정.
- FAB 는 **그대로 유지**. 분류가 하나라도 생긴 이후 (normal state) 에서는 FAB 가 1급 entry 로 계속 동작해야 하므로, FAB 를 제거하거나 숨기지 않는다. 이 plan 은 "**empty 일 때만 추가 CTA**" 이다.
- `EmptyState` 에 action slot 이 생기면 다른 탭 (Hidden, Digest, Priority 등) 이 같은 패턴을 나중에 채용할 수 있게 된다. 이 PR 에서는 Categories 만 적용하고, 다른 탭은 별개 plan 대상으로 남긴다.

---

## Task 1: Add failing Compose UI test for empty-state CTA

**Objective:** 분류 탭이 비어 있을 때 "새 분류 만들기" 버튼이 존재하고, 탭하면 editor 가 열리는 계약을 테스트로 고정. 오늘은 없으므로 RED.

**Files:**
- 신규: `app/src/androidTest/java/com/smartnoti/app/ui/screens/categories/CategoriesScreenEmptyStateTest.kt`
  (이미 같은 디렉토리에 다른 androidTest 가 없다면 instrumented test 설정이 필요 — 이 경우 `app/src/test/...` 의 Robolectric 기반 Compose UI 테스트로 대체. 구현자가 현행 프로젝트의 Compose UI 테스트 패턴을 따라 결정.)

**Steps:**
1. `createComposeRule` 로 `CategoriesScreen(contentPadding = PaddingValues(0.dp))` 렌더. `CategoriesRepository` 의 `observeCategories()` 가 빈 리스트를 emit 하도록 테스트 더블 또는 실제 DataStore 빈 상태를 준비.
2. `onNodeWithText("새 분류 만들기").assertHasClickAction().assertExists()` — empty 상태에서 inline 버튼 존재 검증.
3. 버튼 `performClick()` 후 editor 다이얼로그 (`CategoryEditorScreen`) 의 identifying node (예: "분류 만들기" 타이틀 또는 "이름" 필드) 가 노출되는지 검증.
4. `./gradlew :app:testDebugUnitTest`(또는 `connectedAndroidTest`) 로 실행 → fail 확인.

## Task 2: Add `action` slot to EmptyState and wire the inline CTA

**Objective:** Task 1 테스트가 통과하게 구현.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/components/EmptyState.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt`

**Steps:**
1. `EmptyState` 시그니처에 옵셔널 `action: (@Composable () -> Unit)? = null` 파라미터 추가. `subtitle` 아래 `Column` 안에 `action?.invoke()` 삽입 (기본 `spacedBy(12.dp)` 간격 그대로 사용, 필요 시 버튼 위만 `Spacer(4.dp)` 로 강조). 기본 호출자(`null`) 는 기존 렌더와 동일한 결과가 나와야 함.
2. `CategoriesScreen` 의 `EmptyState(...)` 호출에 `action = { ... }` 람다 추가 — `FilledTonalButton` (또는 `.claude/rules/ui-improvement.md` 의 "accent sparingly" 원칙에 맞는 variant) + "새 분류 만들기" 텍스트 + `onClick = { editorTarget = CategoryEditorTarget.New }`.
3. 버튼 아이콘(`Icons.Outlined.Add`)은 선택. 구현자 판단으로 "quiet confidence" 규칙에 맞게 포함 여부 결정.
4. 분류가 하나 이상 있는 normal state 에서는 기존 FAB 만 보이도록 유지 — Task 2 의 변경이 이 경로에 영향을 주지 않는지 직접 시각 확인.
5. `./gradlew :app:testDebugUnitTest` (해당 Compose 테스트 포함) — Task 1 테스트 초록 확인.

## Task 3: Update journey doc + ship

**Objective:** 문서 일치 + PR 마감.

**Files:**
- `docs/journeys/categories-management.md`

**Steps:**
1. Observable steps 2 바로 뒤에(또는 step 5 의 "FAB 또는 Detail '편집'" 근처에) "분류가 0개일 때 EmptyState 내 'inline CTA' 로도 같은 editor 에 진입 가능" 짧게 추가.
2. Code pointers 에 `EmptyState` 의 `action` slot 확장 1줄 추가.
3. Change log 에 오늘(YYYY-MM-DD) 날짜 + 요약 + commit 해시(머지 시 채움) 1줄 append.
4. `last-verified` 는 **건드리지 않는다** — 이 PR 에서 ADB 로 recipe 전체를 돌린 게 아니면 갱신하지 않음 (per `.claude/rules/docs-sync.md`).
5. PR 제목: `fix(categories): add inline CTA to empty state`.

---

## Scope

**In:**
- `EmptyState` 에 `action` 옵셔널 슬롯 추가.
- `CategoriesScreen` empty 렌더에만 "새 분류 만들기" inline 버튼 주입.
- RED-first Compose UI 테스트 1개.
- Journey 문서 갱신.

**Out:**
- 다른 탭(Hidden / Digest / Priority / Ignored) 의 EmptyState 에 CTA 추가 — 별개 plan.
- Empty 상태에서 FAB 를 숨기는 UX — 현행 유지.
- 분류 자동 추천 / onboarding 프리셋 강화 — 관련 없음.

---

## Risks / open questions

- **버튼 카피 "새 분류 만들기" vs FAB 의 "새 분류 추가":** 구현자가 UX 규칙에 맞게 판단. 두 문구의 톤 차이가 의도적(empty = 처음 만들기, normal = 추가)인지, 통일이 맞는지 PR 리뷰에서 결정.
- **Compose UI 테스트 환경:** 프로젝트에 기존 instrumented test / Robolectric Compose 테스트가 설정돼 있지 않을 가능성. 있으면 그 패턴을 따르고, 없으면 일단 `@Composable` 단위로 semantic 트리를 검증할 수 있는 가장 가벼운 경로 선택. 단위 테스트가 불가능하면 이 Task 를 ADB uiautomator dump 검증으로 바꿀지 구현자가 결정하고 Risks 에 기록.
- **Accent 남용 우려:** `.claude/rules/ui-improvement.md` 는 "accent sparingly" 를 강조. empty state 의 inline 버튼이 accent 색을 쓰면 FAB 와 합쳐 accent 가 두 번 나올 수 있음 → `FilledTonalButton` 또는 outlined variant 를 권장. 시각적으로 어색하면 text/outlined 로 한 단계 낮추는 것도 허용.

---

## Related journey

- [`categories-management`](../journeys/categories-management.md) — 이 plan 이 shipped 되면 해당 journey 의 Observable steps 2 / Code pointers / Change log 를 갱신한다. 현재 Known gaps 에 "empty-state CTA 누락" 항목 자체가 기재돼 있지 않으므로 bullet 제거는 필요 없음; 대신 Change log 에 "empty state 에 inline CTA 추가" 한 줄 append.
