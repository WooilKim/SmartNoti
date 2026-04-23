---
status: shipped
related-journey: ignored-archive
superseded-by: ../journeys/ignored-archive.md
---

# Ignored-archive first-tap nav race fix

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Settings 의 `showIgnoredArchive` 토글을 OFF→ON 으로 뒤집은 **직후 같은 composition** 에서 "무시됨 아카이브 열기" 버튼을 탭해도 `IllegalArgumentException: Navigation destination ignored_archive cannot be found` 크래시가 발생하지 않는다. 사용자 입장에서 첫 opt-in 순간이 안전해진다.

**Architecture:** 현재 `AppNavHost` 는 `produceState { showIgnoredArchive }` 를 collect 하고, `true` 일 때만 `composable(Routes.IgnoredArchive.route)` 를 graph 에 등록 + Settings 카드 버튼의 `onOpenIgnoredArchive` 람다를 non-null 로 내려준다. 버튼 람다는 `showIgnoredArchive` 가 true 로 재구성된 같은 frame 에 노출되지만, NavHost 의 destination 등록은 NavController 내부에서 한 프레임 뒤에 반영되는 경우가 있다. 따라서 탭 시점에 graph 에 아직 destination 이 없을 수 있다 — 좁은 race window. 가장 낮은 리스크 픽스는 **Route 등록 자체는 항상 수행**하고, 진입 경로 (버튼 가시성) 만 gate 하는 것. 이렇게 하면 토글 ON 시점과 destination 존재 시점이 분리돼 race 가 원천 제거된다.

**Tech Stack:** Kotlin, Jetpack Compose Navigation, DataStore, Gradle unit tests.

---

## Product intent / assumptions

- 조건부 route 의 원래 의도는 "OFF 일 때 어떤 경로로도 도달 불가" — 딥링크 / 외부 intent 로도 못 들어오게 하는 것. 이 의도를 **버튼 람다 null 처리 + (필요 시) 딥링크 resolver 에서 가드** 로도 동등하게 달성할 수 있는지 확인하고, 가능하면 route 자체는 항상 등록한다.
- 만약 "딥링크로 직접 도달" 시나리오가 있어 route 자체를 숨겨야 하는 제품 요구가 남아 있다면, race 우회는 Route 상시 등록 대신 `navigate()` 를 `LaunchedEffect(key = showIgnoredArchive)` + request flag 로 다음 frame 에 밀어넣는 방식으로 대체한다. 이 판단은 Task 2 에서 확정.
- 픽스는 기존 journey 의 Observable steps 를 거의 바꾸지 않는다 — 사용자 관측 동작은 동일하며 race 만 사라진다.

---

## Task 1: Add failing test that pins the race [shipped via PR #199]

**Objective:** toggle OFF→ON 직후 같은 composition 에서 버튼 탭 시 `navigate(Routes.IgnoredArchive.route)` 가 안전해야 한다는 계약을 테스트로 고정.

**Files:**
- 신규: `app/src/androidTest/java/com/smartnoti/app/navigation/IgnoredArchiveNavRaceTest.kt` (Compose UI test 가능하면 여기)
- 차선: `app/src/test/java/com/smartnoti/app/navigation/IgnoredArchiveRouteGateTest.kt` — 순수 단위 테스트로 "버튼이 보이는 상태" 와 "route 가 그래프에 등록된 상태" 가 같은 recomposition tick 에서 관측되는지 가드하는 헬퍼 검증.

**Steps:**
1. Compose UI test 가 가능한 환경 (`androidx.compose.ui:ui-test-junit4`) 이면: `AppNavHost` 를 `SettingsRepository` fake 로 띄우고, `setShowIgnoredArchive(true)` 호출 직후 `onNodeWithText("무시됨 아카이브 열기").performClick()` — assertion 은 current destination 이 `ignored_archive` 가 되거나 최소한 exception 이 발생하지 않는 것.
2. UI test 셋업이 부담스러우면 단위 테스트로 대체: "버튼 노출 조건" 과 "route 등록 조건" 이 동일한 단일 boolean (또는 동일 source) 에서 파생되는지 contract 테스트.
3. `./gradlew :app:testDebugUnitTest` (또는 `:app:connectedDebugAndroidTest`) 로 **failing** 확인.

## Task 2: Fix the race in `AppNavHost` [IN PROGRESS via PR #199]

**Objective:** Task 1 의 테스트가 초록이 되게. 가능한 한 작은 diff.

**Files:**
- `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`

**Steps:**
1. **Option A (preferred):** `composable(Routes.IgnoredArchive.route) { ... }` 를 `if (showIgnoredArchive)` 블록 밖으로 이동해 **항상 graph 에 등록**. 버튼의 `onOpenIgnoredArchive` 람다만 `showIgnoredArchive` 에 따라 null/non-null 로 gate. 이 경우 외부 딥링크로 OFF 상태에서도 진입 가능해지므로 딥링크가 해당 route 를 사용하는지 확인 — 현재 `pendingDeepLinkRoute` 경로가 `ignored_archive` 를 쓰지 않으면 그대로 안전. 쓰면 Option B 로 전환.
2. **Option B (fallback):** route 는 조건부 유지하되, Settings 카드 버튼의 람다가 직접 `navigate()` 를 호출하지 않고 `var pendingArchiveOpen by remember { mutableStateOf(false) }` 에 request 만 기록. `LaunchedEffect(pendingArchiveOpen, showIgnoredArchive)` 에서 **둘 다 true 일 때만** `navigate()` 실행 + flag reset. 이러면 NavHost recomposition 이 route 를 반영한 뒤 next frame 에 navigate 가 돌아 race window 가 닫힌다.
3. 선택한 option 을 주석으로 명시 (현재 `AppNavHost` 의 "Plan `2026-04-21-ignore-tier-fourth-decision` Task 6" 주석 블록 갱신).
4. 전체 테스트 실행 — 기존 navigation 관련 테스트가 깨지지 않는지 확인.

## Task 3: Update journey doc and close the gap

**Files:**
- `docs/journeys/ignored-archive.md`

**Steps:**
1. Known gaps 의 race-condition 관련 bullet (2026-04-21 verification 끝부분) 을 제거.
2. Change log 에 라인 추가: `YYYY-MM-DD: toggle OFF→ON 직후 same-composition 탭 race 해소 — plan `docs/plans/2026-04-22-ignored-archive-first-tap-nav-race.md` (PR #<번호>, <commit>).` 형식.
3. Observable steps 가 Option A 로 바뀌었다면 step 2 ("`Routes.IgnoredArchive.route` 가 navigation graph 에 composable 로 추가됨") 를 현실과 맞게 조정 — "진입 버튼이 비가시 상태 → 가시 상태로 전환" 쪽으로 수정. Option B 라면 step 2 는 그대로 두고 진입 타이밍 묘사만 1줄 덧붙임.
4. `last-verified` 는 **건드리지 않음** — 실제 recipe 재실행은 구현 PR 머지 후 journey-tester 가 수행.

---

## Scope

**In:**
- `AppNavHost` 의 `Routes.IgnoredArchive` 등록/진입 타이밍 조정
- 관련 failing test 1건
- `docs/journeys/ignored-archive.md` Known gap 제거 + Change log

**Out:**
- 다른 route 의 조건부 등록 구조 (이 PR 은 IgnoredArchive 한정)
- 딥링크 resolver 의 route gating 전면 재설계
- IgnoredArchive 자체의 복구/bulk action UI (별도 Known gap)
- Settings 카드 자체의 리워크

## Risks / open questions

- **Option A 로 갈 때 "OFF 인데도 route 가 존재" 가 허용되는가?** 원래 의도 ("어떤 경로로도 도달 불가") 가 엄격한 보안 속성이 아니라 UX 단순화이면 Option A 로 충분. 외부 딥링크로 archive 에 진입되는 케이스가 실제로 있는지 확인 필요 — 현재 grep 상 `pendingDeepLinkRoute` 가 `ignored_archive` 를 쓰는 경로는 없어 보이지만 구현자가 재확인.
- **Option B 의 2-step navigate 가 화면 전환에 추가 지연을 만드는지:** 사용자 체감 1프레임 수준이지만, `LaunchedEffect` 가 실행되기 전에 사용자가 연타하는 경우 duplicate request 가 쌓이지 않도록 flag reset 순서 주의.
- **Compose UI test 인프라 부재:** 이 레포의 `androidTest` 기반 카테고리가 활발하지 않으면 Task 1 이 Option 2 (단위 테스트) 로 떨어질 수 있음. 그 경우 race 자체는 ADB 반복 탭 recipe 로 구현 PR 에서 수동 검증.

## Related journey

- [ignored-archive](../journeys/ignored-archive.md) — 2026-04-21 verification 에서 관측된 race 를 여기서 해소. 픽스 머지 시 해당 Known gap 엔트리 제거 + Change log 업데이트.
