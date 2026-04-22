---
status: planned
---

# 분류 탭 Empty State — Inline CTA + FAB 가시성 회복

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 분류 탭에 진입했는데 아직 분류가 하나도 없는 사용자가, "분류를 어떻게 만들지" 에 대한 시각적 길 안내를 받지 못해 우하단 FAB 를 놓치는 문제를 해결한다. Empty state 중앙에 **"새 분류 만들기"** 일차 버튼을 추가해, 신규 사용자가 EmptyState 카피를 읽은 흐름 그대로 탭 한 번으로 에디터에 진입할 수 있게 한다. 동시에 `ui-ux-inspector` 가 발견한 더 근본적인 문제를 함께 수정한다: **현재 FAB 는 `Box(fillMaxSize())` 가 Scaffold `contentPadding` 을 소비하지 않아 하단 네비바 뒤로 가려진다** — 즉 empty-state 에서도, normal 상태에서도 사용자가 FAB 를 탭할 수 없는 상태. 두 수정이 함께 가야 신규 사용자(inline CTA)와 기존 사용자(재사용 FAB) 모두가 분류를 만들 수 있다.

**Architecture:** 두 가닥의 변경:
1. **FAB 가시성 회복 (신규 Task 0):** `CategoriesScreen` 루트를 `Box(fillMaxSize())` → **로컬 `Scaffold(floatingActionButton = { ... })`** 로 리팩토링. 부모 `AppNavHost` 의 Scaffold `contentPadding` 은 로컬 Scaffold 의 `Modifier.padding(contentPadding)` 으로 소비해 로컬 Scaffold 가 AppBottomBar 위 영역에서만 레이아웃된다. 이렇게 하면 Material3 의 `floatingActionButton` 슬롯이 자동으로 bottomBar 인셋을 반영하고, 기존의 `.align(Alignment.BottomEnd).padding(bottom = 24.dp, end = 24.dp)` 패턴이 필요 없어진다.
2. **EmptyState CTA (Task 1–3, 기존 범위):** `ui/components/EmptyState` 는 현재 `title` / `subtitle` / `icon` 만 받는 순수 표현 컴포넌트이다. 여기에 **옵셔널 `action: (@Composable () -> Unit)?`** 슬롯을 추가해 하위 호환을 유지하면서, `CategoriesScreen` 이 비었을 때 해당 슬롯에 `FilledTonalButton` + "새 분류 만들기" 를 전달한다. 버튼 onClick 은 기존 FAB 와 동일하게 `editorTarget = CategoryEditorTarget.New` 로 설정 → 기존 `CategoryEditorScreen` 다이얼로그가 그대로 뜬다. FAB 는 유지 (분류가 몇 개 있는 normal state 에서는 여전히 1급 entry). 사용자는 **둘 다** 필요: CTA 는 first-run 시선 경로에 있는 명시적 진입점, FAB 는 분류 생성 이후 지속되는 2급 entry.

**카피 통일:** FAB 의 기존 `"새 분류 추가"` 와 Detail sheet 의 `"분류 만들기"`, 새 CTA 의 `"새 분류 만들기"` 를 **모두 `"새 분류 만들기"`** 로 통일 — action-oriented 표현이고, Detail 편집 플로우와 용어가 일치한다.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Compose UI Test (`createComposeRule` + semantics).

---

## Product intent / assumptions

- Empty state 는 신규 사용자가 **가장 먼저 보는 분류 탭의 얼굴**이므로, "무엇을 할지" 를 설명하는 카피 바로 아래에 **행동 유도 버튼**이 있어야 한다. FAB 는 fixed position 이라 신규 사용자의 시선 흐름 (Header → EmptyState 카피) 에서 벗어나 있다.
- 버튼 카피는 **"새 분류 만들기"** 로 Detail 편집 플로우의 어휘와 통일. FAB 도 같이 `"새 분류 만들기"` 로 바꿔 일관성 유지.
- FAB 는 **그대로 유지**. 분류가 하나라도 생긴 이후 (normal state) 에서는 FAB 가 1급 entry 로 계속 동작해야 하므로, FAB 를 제거하거나 숨기지 않는다.
- `EmptyState` 에 action slot 이 생기면 다른 탭 (Hidden, Digest, Priority 등) 이 같은 패턴을 나중에 채용할 수 있게 된다. 이 PR 에서는 Categories 만 적용하고, 다른 탭은 별개 plan 대상으로 남긴다.
- **FAB 가시성:** `ui-ux-inspector` 가 발견한 regression — `Box(fillMaxSize())` 가 부모 Scaffold 의 `contentPadding` (= AppBottomBar 높이) 을 소비하지 않아 FAB 가 네비바 뒤로 렌더된다. `Scaffold(floatingActionButton = ...)` 로 슬롯 방식으로 전환하면 Material3 가 인셋을 알아서 처리한다.

---

## Task 0: Route FAB through a local Scaffold `floatingActionButton` slot

**Objective:** `CategoriesScreen` 의 FAB 가 AppBottomBar 뒤로 가려지지 않도록, 부모 `contentPadding` 을 존중하는 레이아웃으로 전환.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt`
- `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoriesEmptyStateContractTest.kt` (신규 — Task 1 과 묶어서 한 파일에 작성 가능)

**Steps:**
1. 현재 구조: `Box(Modifier.fillMaxSize()) { LazyColumn(... .padding(contentPadding) ...); ExtendedFloatingActionButton(... .align(Alignment.BottomEnd).padding(bottom = 24.dp, end = 24.dp)) }`. Box 가 부모 `contentPadding` 을 무시하므로 FAB 는 전체 화면 기준 BottomEnd 에 놓여 AppBottomBar 영역과 겹친다.
2. 리팩토링: Box 를 **로컬 `Scaffold`** 로 감싸고, `modifier = Modifier.padding(contentPadding)` 로 부모 Scaffold 의 bottom-bar 인셋을 소비. `floatingActionButton = { ExtendedFloatingActionButton(...) }` 슬롯에 FAB 이동, `containerColor = Color.Transparent` 로 배경 중복 제거. 내부 `LazyColumn` 은 로컬 Scaffold 의 `innerPadding` 을 받고, 외부 `contentPadding` 은 이미 모바일 Scaffold modifier 에서 소비됐으므로 `.padding(contentPadding)` 호출 제거.
3. FAB 텍스트 `"새 분류 추가"` → `"새 분류 만들기"` 로 교체 + 공용 상수 (`CategoriesEmptyStateAction.LABEL`) 참조.
4. 논리-레벨 테스트: Compose UI 테스트 인프라(`createComposeRule`) 가 이 repo 에 없으므로 ADB uiautomator 로 최종 검증하되, 회귀 방지용 단위 테스트는 "FAB label 과 EmptyState CTA label 이 단일 source of truth 에서 온다" 계약만 유지 (`CategoriesEmptyStateContractTest`).
5. 빌드/기존 테스트 전체가 초록인지 확인.

## Task 1: Add failing unit test for empty-state CTA contract

**Objective:** Compose UI 테스트 인프라 부재로 headless 계약 테스트로 대체. `CategoriesEmptyStateAction` 이라는 단일 상수 객체에 `LABEL = "새 분류 만들기"` 가 있고, 이 label 이 Detail sheet 과 FAB 의 대외 label 과 일치한다는 계약. RED-first 이므로 먼저 상수 파일이 없는 상태에서 테스트를 실패하게 두고, Task 2 에서 구현.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoriesEmptyStateContractTest.kt`

**Steps:**
1. `CategoriesEmptyStateAction` (prod) 가 존재하지 않는 상태에서 `CategoriesEmptyStateAction.LABEL` 을 참조하는 JUnit 테스트 추가.
2. 테스트: `assertEquals("새 분류 만들기", CategoriesEmptyStateAction.LABEL)` — 카피 계약.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.CategoriesEmptyStateContractTest"` → 컴파일 실패(상수 없음) 로 RED.

## Task 2: Add `action` slot to EmptyState and wire the inline CTA

**Objective:** Task 1 테스트가 통과하게 구현.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/components/EmptyState.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesEmptyStateAction.kt` (단일 상수 object, 테스트와 공용)

**Steps:**
1. `EmptyState` 시그니처에 옵셔널 `action: (@Composable () -> Unit)? = null` 파라미터 추가. `subtitle` 아래 `Column` 안에 `action?.invoke()` 삽입. 기본 호출자(`null`) 는 기존 렌더와 동일한 결과가 나와야 함.
2. `CategoriesEmptyStateAction` object 에 `LABEL = "새 분류 만들기"` 상수 정의.
3. `CategoriesScreen` 의 `EmptyState(...)` 호출에 `action = { FilledTonalButton(onClick = { editorTarget = CategoryEditorTarget.New }) { Text(CategoriesEmptyStateAction.LABEL) } }` 추가. `.claude/rules/ui-improvement.md` 의 "accent sparingly" 원칙에 맞는 `FilledTonalButton` variant 사용.
4. Task 0 의 FAB 도 `Text(CategoriesEmptyStateAction.LABEL)` 로 동일 상수 참조.
5. 분류가 하나 이상 있는 normal state 에서는 기존 FAB 만 보이도록 유지.
6. `./gradlew :app:testDebugUnitTest` 전체 초록 확인.

## Task 3: ADB smoke + update journey doc + ship

**Objective:** 실제 디바이스에서 FAB / CTA 동시 가시성 확인 + 문서 일치 + PR 마감.

**Files:**
- `docs/journeys/categories-management.md`

**Steps:**
1. `./gradlew :app:assembleDebug` + `adb install -r` → 앱 실행, 분류 탭 진입. **신규/초기화 상태** (분류 0개) 로 EmptyState inline CTA 가시, FAB 도 AppBottomBar 위에 가시 확인. `adb shell uiautomator dump` 로 두 노드 모두 탭 가능한 bounds 에 있는지 확인 (FAB 가 네비바보다 위에 있는지 y 좌표 비교).
2. FAB 탭 → editor 열림 확인. CTA 탭 → editor 열림 확인 (같은 타깃).
3. Observable steps 2 의 FAB 라벨을 `"새 분류 만들기"` 로 갱신. Step 5 근처 또는 Step 2 뒤에 "분류가 0개일 때 EmptyState 내 inline CTA 로도 같은 editor 에 진입 가능" 추가. Step 2 문구를 FAB 가 AppBottomBar 위에 항상 보이도록 Scaffold 슬롯 사용한다는 사실 반영.
4. Code pointers 에 `EmptyState` 의 `action` slot 확장 + `CategoriesEmptyStateAction` 상수 + Scaffold 슬롯 FAB 배치를 각각 짧게 추가.
5. Known gaps 에 기존 항목은 무관하지만, 본 PR 이 해결한 "FAB 가 bottomBar 뒤로 숨던 regression" 은 Change log 에만 요약.
6. Change log 에 오늘(YYYY-MM-DD) 날짜 + 요약 + PR 번호(머지 시 채움) 1줄 append.
7. `last-verified` 는 이 PR 에서 실제 ADB 로 verification recipe 전체(또는 해당 부분) 를 돌리면 갱신; 아니면 유지.
8. PR 제목: `fix(categories): restore FAB visibility and add empty-state inline CTA`.

---

## Scope

**In:**
- `CategoriesScreen` 을 로컬 `Scaffold(floatingActionButton = ...)` 로 리팩토링 → FAB 가 AppBottomBar 위로 올라와 가시성 회복.
- `EmptyState` 에 `action` 옵셔널 슬롯 추가.
- `CategoriesScreen` empty 렌더에만 "새 분류 만들기" inline 버튼 주입.
- FAB 및 CTA 라벨을 `"새 분류 만들기"` 로 통일 (공용 상수 `CategoriesEmptyStateAction.LABEL`).
- Headless 단위 테스트 1개 (RED-first: 상수 계약).
- ADB smoke (uiautomator dump 로 FAB 가 bottomBar 위 bounds 에 있는지 확인).
- Journey 문서 갱신.

**Out:**
- 다른 탭(Hidden / Digest / Priority / Ignored) 의 EmptyState 에 CTA 추가 — 별개 plan.
- Empty 상태에서 FAB 를 숨기는 UX — 현행 유지.
- 분류 자동 추천 / onboarding 프리셋 강화 — 관련 없음.

---

## Risks / open questions

- **로컬 Scaffold 중첩:** `AppNavHost` 의 외부 Scaffold 에 이미 bottomBar 가 있는 상태에서 각 screen 안에 로컬 Scaffold 를 추가하는 것은 Material3 에서 허용되며 흔한 패턴 (다른 대안은 FAB 를 공용 Scaffold 슬롯으로 올리는 것인데 모든 screen 의 콜백을 NavHost 까지 plumbing 해야 해서 범위가 훨씬 커짐). 로컬 Scaffold 는 `containerColor = Color.Transparent` 로 배경 중복을 막는다.
- **Compose UI 테스트 환경:** 프로젝트에 `createComposeRule` 기반 인프라가 없음 — `compose-ui-test` 의존성 없이 최소 작업으로 갈 수 있도록 **headless 계약 테스트**(상수 단일화) + **ADB uiautomator dump** 로 대체. 추후 Compose 테스트 인프라 도입 시 본 테스트를 rendered-node 검증으로 격상 가능.
- **Accent 남용 우려:** `.claude/rules/ui-improvement.md` 는 "accent sparingly" 를 강조. empty state 의 inline 버튼이 accent 색을 쓰면 FAB 와 합쳐 accent 가 두 번 나올 수 있음 → CTA 는 `FilledTonalButton` 으로 한 단계 낮추고, FAB 는 기존대로 primary 유지.

---

## Related journey

- [`categories-management`](../journeys/categories-management.md) — 이 plan 이 shipped 되면 해당 journey 의 Observable steps 2 / Code pointers / Change log 를 갱신한다. Change log 에 "FAB 가시성 회복 + empty state inline CTA 추가" 한 줄 append; Observable steps 2 의 FAB 라벨을 `"새 분류 만들기"` 로 고치고 EmptyState CTA 존재를 명시.
