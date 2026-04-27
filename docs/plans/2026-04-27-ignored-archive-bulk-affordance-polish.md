---
status: planned
---

# 무시됨 아카이브 — 헤더 bulk 버튼 폭 회귀 + per-row 복구 anchor 강화

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `IgnoredArchiveScreen` 의 두 가지 시각 회귀를 한 번에 정리한다. (1) 헤더 SmartSurfaceCard 안 두 OutlinedButton (`모두 PRIORITY 로 복구` / `모두 지우기`) 이 `Modifier.weight(1f)` 로 화면 폭을 절반씩 점유하면서, 첫 번째 버튼의 한·영 mix 라벨 (`모두 PRIORITY 로 복구`) 이 좁은 폭을 넘어 "복구" 글자가 두 번째 줄로 wrap 되어 한쪽 라벨만 깨져 보이는 affordance 위계 손상. (2) per-row `PRIORITY 로 복구` TextButton 이 NotificationCard 의 보더 바깥 (Column wrapper 안, 카드 우측 끝 + 8.dp gap) 에 떠 있어 어떤 카드에 연결된 액션인지 시각적 anchor 가 약함. 두 회귀는 모두 2026-04-27 ui-ux sweep 에서 발견되었으며 (`/tmp/ui-ignored-archive.png`), 아카이브 안의 bulk action 표면 (PR #433) 이 ship 된 직후 노출되었다. 본 plan 의 결과로 헤더 버튼 라벨이 한 줄에 안정적으로 들어가고, per-row 복구 액션이 카드와 시각적으로 한 묶음으로 보이도록 정렬된다 — 사용자가 "이 카드의 복구 버튼" 임을 한눈에 인지할 수 있게.

**Architecture:** 두 회귀 모두 `IgnoredArchiveScreen.kt` 안에서 닫힌 변경. 외부 contract (`NotificationRepository.observeIgnoredArchive` / `restoreAllIgnoredToPriority` / `deleteIgnoredByIds` / `IgnoredArchiveMultiSelectState` 의 long-press 진입) 는 변경 없음. (1) 헤더 bulk 버튼은 두 가지 후보 fix 중 하나를 선택 — 라벨 단축 (예: `PRIORITY 모두 복구` / `모두 지우기`) 또는 두 OutlinedButton 을 세로 stack (Column) 으로 전환. 단축은 한 줄에 안전하게 들어가고 시각 일관성을 유지하지만 product copy 판단이 필요하고, 세로 stack 은 copy 안전 (라벨 변경 없음) 이지만 헤더 카드 height 가 늘어나며 시각 위계 (primary action vs destructive) 가 동등하게 보이는 부작용이 있다. (2) per-row 복구는 카드 안쪽 trailing slot 으로 흡수 — `NotificationCard` 의 콘텐츠 슬롯에 trailing optional Composable 을 넘기거나, IgnoredArchiveScreen 측에서 카드와 버튼을 같은 `Surface`/`Card` 보더 안쪽으로 감싸 들여쓰기. 어느 쪽이든 long-press 진입 (`combinedClickable`) 과 multi-select 활성 시 trailing 슬롯을 숨기는 기존 분기 (`if (multiSelect.active)` 가드) 를 보존해야 한다. Pin behavior with characterization tests first — `IgnoredArchiveScreenAffordanceTest` (Compose UI test) 가 (a) 헤더 두 OutlinedButton 이 한 줄에 들어가는지 (또는 세로 stack 시 두 버튼이 모두 visible 한지), (b) per-row TextButton 이 long-press 진입 시 사라지는지, (c) per-row TextButton 이 NotificationCard 와 같은 트리 노드 (parent 또는 sibling) 에 위치하는지를 가드.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 OutlinedButton / TextButton / Card), 기존 `IgnoredArchiveMultiSelectState` 보존, JUnit + Compose UI test (`createComposeRule`).

---

## Product intent / assumptions

- `.claude/rules/ui-improvement.md` 의 다음 원칙 위반을 해소한다: "affordance clarity" (bulk action 라벨이 두 줄로 깨지면 사용자가 그것이 단일 버튼인지 두 액션인지 즉각 판단 못 함), "row hierarchy" (per-row 복구 액션이 카드 외부에 떠 있으면 어느 row 의 액션인지 anchor 약함), "card treatment" (action 은 카드 안쪽 trailing slot 으로 흡수하는 것이 일관됨).
- 헤더 카드의 두 OutlinedButton 은 의미가 비대칭이다 — "모두 PRIORITY 로 복구" 는 비파괴 (snackbar only, confirm 없음) 이고 "모두 지우기" 는 파괴 (AlertDialog confirm 필수). 시각 위계도 같이 차별되는 것이 옳다 (`.claude/rules/ui-improvement.md` "use accent color sparingly for selection, status, and primary actions"). 동일 weight 로 나란히 두는 현재 레이아웃은 destructive 액션을 primary 와 동등하게 보이게 만드는 부작용도 있다.
- per-row `PRIORITY 로 복구` 는 multi-select 활성 시 자동으로 숨겨진다 (PR #433 의 의도된 동작 — long-press 로 진입하면 bulk action bar 가 같은 역할을 흡수). 본 plan 의 어떤 fix 도 이 분기를 보존해야 하며, characterization test 가 가드한다.
- 본 화면은 opt-in (`SmartNotiSettings.showIgnoredArchive` 토글) 이라 사용자 노출 빈도가 낮은 편이지만, 토글을 켠 사용자는 IGNORE 룰의 효과를 점검하러 들어오는 명시적 의도를 가진 사용자이므로 affordance 의 정확성 가치는 높다.
- DRIFT 없음. 본 plan 은 행동 변경 없이 시각 polish 만 — `last-verified` 갱신은 별도 verification PR 로 위임하고, 본 plan 의 ship PR 은 Change log 에 한 줄만 추가한다.

### 사용자 판단이 필요한 결정 (Risks / open questions 으로 옮김)

- 헤더 bulk 버튼의 fix 방향: **(A) 라벨 단축** (`PRIORITY 모두 복구` / `모두 지우기` — 한글 어순 자연스러움 vs `모두 PRIORITY` — 영문 토큰 강조) vs **(B) 세로 stack** (라벨 변경 없음, 카드 height +56dp 가량). 권장: **(A) 라벨 단축**. 한 줄 안에 들어가야 destructive vs non-destructive 의 시각 위계 차별 (border weight, color tint 등 후속 polish) 도 가능하고, 세로 stack 은 카드가 너무 무거워져 위 helper line ("최신 순으로 정렬돼 있어요…") 의 비중이 묻힌다. 어느 단축 라벨을 쓸지 (`PRIORITY 모두 복구` vs `모두 PRIORITY` 등) 는 product judgment 필요.
- per-row TextButton 의 위치: **(C) `NotificationCard` 의 trailing slot 추가** (재사용성 ↑, NotificationCard 의 시그니처가 호출처 모두에 영향 → blast radius 검토 필요) vs **(D) IgnoredArchiveScreen 안에서 카드와 버튼을 같은 `Surface`/`Card` 보더로 wrap** (이 화면만 영향, 다른 화면의 NotificationCard 호출 무영향). 권장: **(D) wrap**. NotificationCard 의 시그니처 확장은 본 plan scope 를 넘어선다 (다른 4–6 호출처 모두에 default 슬롯 추가 + characterization 필요). 한 화면 wrap 으로 anchor 회복 후, NotificationCard 의 trailing slot 일반화는 후속 plan 으로 분리.

---

## Tasks

### Task 1 — characterization test 추가 (failing-first) [IN PROGRESS via PR #474]

- [ ] `IgnoredArchiveScreenAffordanceTest` (Compose UI test, `androidTest` 또는 `app/src/test` 의 `createComposeRule` 기반) 신설. 다음 3 케이스:
  1. 헤더 카드의 두 OutlinedButton (`모두 PRIORITY 로 복구` / `모두 지우기`) 이 모두 visible 하고, **각자 한 줄로 렌더된다** (Compose 의 `onAllNodes` + `assertTextEquals` 또는 layout 노드 height 비교로 가드). 라벨 단축 옵션을 채택하면 단축된 라벨이 사용된다.
  2. multi-select 비활성 상태에서 per-row `PRIORITY 로 복구` TextButton 이 NotificationCard 와 **동일한 parent semantics 노드 트리** 안에 있다 (현재 코드: `Column { NotificationCard(...) ; TextButton(...) }` → 트리상 sibling 이지만 부모 보더 없음. fix 후: `Card { NotificationCard(...) ; TextButton(...) }` 또는 NotificationCard 안 trailing slot — 어느 fix 든 같은 부모 노드).
  3. multi-select 활성 시 per-row `PRIORITY 로 복구` TextButton 이 트리에서 사라진다 (기존 분기 보존 회귀 가드).
- [ ] 본 테스트는 fix 적용 전 RED 여야 한다 (현 코드는 (1) 라벨 wrap, (2) 카드와 버튼이 같은 부모 보더 없음).

**파일:** `app/src/androidTest/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveScreenAffordanceTest.kt` (신규) 또는 `app/src/test/.../IgnoredArchiveScreenAffordanceTest.kt` (Compose runtime test 가 host JVM 에서 가능하면).

**검증:** `./gradlew :app:testDebugUnitTest --tests "*IgnoredArchiveScreenAffordanceTest"` (또는 connectedAndroidTest) 가 RED — 정확히 3 케이스 중 (1) + (2) 가 실패해야 한다.

### Task 2 — 헤더 bulk 버튼 라벨 단축 또는 세로 stack 적용

- [ ] Risks open question (A vs B) 의 product 판단 결과를 반영. 권장 (A) 채택 시: `IgnoredArchiveScreen.kt:233` (`Text("모두 PRIORITY 로 복구")`) 의 라벨을 product 결정값 (예: `"PRIORITY 모두 복구"`) 으로 교체. `Modifier.weight(1f)` 유지.
- [ ] 채택 (B) 시: 두 OutlinedButton 을 감싸던 `Row` 를 `Column` (verticalArrangement = `Arrangement.spacedBy(8.dp)`) 으로 교체, `Modifier.weight(1f)` → `Modifier.fillMaxWidth()` 로 전환.
- [ ] Task 1 의 (1) 케이스가 GREEN 으로 전환되는지 확인.

**파일:** `app/src/main/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveScreen.kt` (라벨 또는 layout 수정).

**검증:** `./gradlew assembleDebug` + Task 1 의 (1) 케이스 GREEN. 헤더 카드의 두 OutlinedButton 라벨이 모두 한 줄로 렌더되는지 emulator 에서 시각 확인 (`uiautomator dump` + `bounds` 검사).

### Task 3 — per-row 복구 TextButton 을 카드 안쪽 anchor 로 흡수

- [ ] Risks open question (C vs D) 의 product 판단 결과 반영. 권장 (D) 채택 시: 현재 `Column { NotificationCard(...) ; TextButton(...) }` 패턴을 `Card { NotificationCard(...) ; HorizontalDivider() ; TextButton(...) }` 또는 `Surface(border = ...) { Column { NotificationCard(...) ; TextButton(modifier = Modifier.align(Alignment.End)...) } }` 로 wrap. 카드와 버튼이 동일 보더 안쪽에 위치하도록 정렬.
- [ ] multi-select 활성 시 wrap 안의 TextButton 만 숨기는 기존 `if (!multiSelect.active)` 분기 보존.
- [ ] 채택 (C) 시: `NotificationCard` 의 시그니처에 `trailingSlot: (@Composable () -> Unit)? = null` 추가 + 다른 호출처 (4–6곳) 에 default null 전달. 본 화면만 trailing 에 TextButton 전달. **권장하지 않음** (Risks 참고).
- [ ] Task 1 의 (2) + (3) 케이스 모두 GREEN.

**파일:** `app/src/main/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveScreen.kt` (per-row layout 수정).

**검증:** `./gradlew assembleDebug` + Task 1 의 (2) + (3) GREEN. emulator 에서 sqlite 로 IGNORE row 3건 seed → 각 카드와 `PRIORITY 로 복구` 버튼이 같은 시각 보더 안에 묶여 보이는지 + long-press 시 버튼 사라지는지 확인.

### Task 4 — `ignored-archive` journey 갱신

- [ ] Known gaps 의 두 (2026-04-27 ui-ux sweep) bullet 제거 (헤더 button wrap + per-row TextButton anchor) — 본 plan 결과로 해소됨. 두 bullet 옆에 plan 링크 annotation 을 미리 박지 않고 ship 시점에 직접 제거 (resolution 서술은 Change log 로 이동).
- [ ] Change log 에 본 plan 의 ship 결과 한 줄 추가 (날짜 + "헤더 bulk 버튼 라벨 회귀 + per-row 복구 anchor 회복" + commit hash + "Pinned by `IgnoredArchiveScreenAffordanceTest`").
- [ ] `last-verified` 는 verification recipe 전체 재실행 시점에만 갱신 (per `.claude/rules/docs-sync.md`). 본 plan 은 시각 polish + behavior 무변경이라 `last-verified` 갱신은 분리.

**파일:** `docs/journeys/ignored-archive.md`.

**검증:** Known gaps 의 두 ui-ux bullet 부재 + Change log 의 plan 링크 + commit hash 확인.

---

## Scope

**In scope:**

- `IgnoredArchiveScreen.kt` 안 헤더 두 OutlinedButton 의 라벨 또는 layout 변경.
- `IgnoredArchiveScreen.kt` 안 per-row `PRIORITY 로 복구` TextButton 을 NotificationCard 와 같은 시각 보더 안쪽으로 묶기.
- `IgnoredArchiveScreenAffordanceTest` 신규 — 두 회귀를 영구 가드.
- `ignored-archive` journey 의 Known gaps + Change log 동기화.

**Out of scope:**

- `NotificationCard` 의 `trailingSlot` 일반화 (Risks (C) — 다른 4–6 호출처 모두에 영향, 별도 plan).
- 헤더 카드의 destructive vs non-destructive 시각 위계 차별 (border weight / color tint 변경) — Risks 의 후속 polish.
- IGNORE row retention 정책 (별도 plan, journey Known gaps 별도 bullet).
- Compose UI 테스트의 화면 단독 커버리지 보강 (별도 후속 — 본 plan 의 affordance test 만 추가).

---

## Risks / open questions

- **Q1 (헤더 fix 방향, product judgment 필수):** 라벨 단축 (A) vs 세로 stack (B). 권장 (A). 단축 후보: `PRIORITY 모두 복구` (한글 어순 자연스러움, 동사 후치) / `모두 PRIORITY` (영문 토큰 강조, 동사 생략) / `복구 모두` (한글만, "복구" 가 어떤 status 로의 복구인지 모호 — 비추천). 실제 사용자가 보는 문구이므로 product judgment 필요.
- **Q2 (per-row anchor fix 방향, product judgment 필수):** NotificationCard trailing slot (C) vs IgnoredArchiveScreen 안 wrap (D). 권장 (D). (C) 는 호출처 4–6곳의 default 슬롯 + characterization 비용이 본 plan scope 를 넘는다.
- **R1 (회귀 가능성):** Task 3 의 per-row wrap 이 LazyColumn item 의 height 계산을 바꿔 multi-select bar 등장 시 스크롤 위치가 점프할 수 있음. characterization test 가 detect 못 하므로 emulator 시각 회귀 확인 필수 (Task 3 검증 단계).
- **R2 (multi-select 분기):** Task 3 의 fix 가 `if (!multiSelect.active)` 분기를 정확히 보존해야 함. characterization test (Task 1 의 케이스 3) 가 가드.
- **R3 (NotificationCard re-use):** 본 plan 은 NotificationCard 의 시그니처를 바꾸지 않으므로 다른 화면 (priority / hidden / digest) 에 영향 없음 — 권장 (D) 채택 시 보장됨. (C) 채택 시 모든 호출처 회귀 확인 필요.

---

## Definition of done

- 헤더 카드의 두 OutlinedButton 라벨이 모두 한 줄로 렌더된다 (uiautomator dump 의 bounds height 가 단일 line 기준치 안).
- per-row `PRIORITY 로 복구` TextButton 이 NotificationCard 와 동일한 시각 보더 안에 위치한다 (시각 anchor 회복).
- multi-select 활성 시 per-row TextButton 이 사라지는 기존 분기 보존.
- `IgnoredArchiveScreenAffordanceTest` 의 3 케이스 모두 GREEN.
- `IgnoredArchiveBulkActionsWiringTest` (또는 동등 기존 wiring test) 회귀 없음.
- `ignored-archive` journey 의 Known gaps 에서 두 ui-ux bullet 제거 + Change log 한 줄 추가.

---

## Related journey

- [`docs/journeys/ignored-archive.md`](../journeys/ignored-archive.md) — Known gaps 의 두 (2026-04-27 ui-ux sweep) bullet 이 본 plan 의 ship 결과로 Change log 의 한 줄로 대체된다.

---

## Change log

- 2026-04-27: planned — 2026-04-27 ui-ux sweep (`/tmp/ui-ignored-archive.png`) 에서 발견된 두 회귀 (헤더 OutlinedButton 라벨 wrap + per-row TextButton anchor 약함) 를 한 plan 으로 묶음. characterization test (`IgnoredArchiveScreenAffordanceTest`) 를 RED 로 추가한 뒤 두 fix 적용 후 GREEN 으로 전환하는 tests-first 순서. Risks 의 product judgment 두 가지 (Q1 헤더 fix 방향 / Q2 per-row anchor 방향) 는 구현 PR 에서 결정.
