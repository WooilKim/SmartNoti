---
status: planned
---

# Insight Drill-down Range Selection Survives Detail Round-trip

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Start with Task 1 (failing tests) before any production code change.

**Goal:** 사용자가 인사이트 드릴다운 화면에서 `최근 3시간 / 최근 24시간 / 전체` 범위 칩으로 필터를 좁힌 뒤 알림 카드를 탭해 Detail 로 이동하고 시스템 back 으로 돌아오면, 직전에 선택했던 범위가 그대로 유지된다. 또한 같은 화면에서 `이유 네비게이션` 으로 다른 reason 인사이트로 이동할 때도 사용자가 명시적으로 바꾼 범위가 새 화면의 초기값으로 전달되어 "방금 보던 시간 창" 이 일관되게 따라간다. 사용자는 더 이상 detail 한 번 들어갔다 나올 때마다 칩을 다시 탭할 필요가 없다.

**Architecture:** 두 층의 상태 분리로 해결. (1) 화면 안에서는 `rememberSaveable` 의 key 를 `initialRange` 가 아니라 정적 키로 바꿔, 인자 변화로 인한 state reset 을 막고 process death + back-stack 복원 시 마지막 사용자 선택을 그대로 복구한다 (process-death 회복은 `rememberSaveable` 의 SavedStateHandle 위임이 담당). (2) 사용자가 칩으로 범위를 바꿀 때 `NavBackStackEntry.savedStateHandle` 에 마지막 선택을 기록해, 같은 backstack entry 가 다시 RESUMED 될 때 (Detail → back) 동일 값을 복원하고, 새 deep-link 생성 시 (`reasonNavigation` 클릭) URL 의 `range` 파라미터에도 사용자 선택값이 반영되도록 한다. 변경은 `InsightDrillDownScreen` + `AppNavHost` 의 wiring 한 군데로 국한되며, 신규 도메인 모델은 추가하지 않는다 — `InsightDrillDownRange` enum + `Routes.Insight.createForApp/createForReason` 의 기존 시그니처를 재사용.

**Tech Stack:** Kotlin, Jetpack Compose, androidx.navigation Compose, Gradle unit tests, Robolectric (이미 있는 화면-수준 테스트 기반).

---

## Product intent / assumptions

- 사용자는 "지금 보고 있는 시간 창" 을 한 번 결정하면 같은 인사이트 세션 내내 그 창이 유지되기를 기대한다 — Detail 진입은 "잠깐 카드 한 장 들여다본 것" 일 뿐, 필터 의사결정의 reset 신호가 아니다.
- 다른 reason 인사이트로 이동할 때 (`InsightReasonBreakdownChart` 의 `Routes.Insight.createForReason`) 사용자의 직전 range 선택을 그대로 들고 가는 것은 이미 현재 코드가 의도하던 동작 (`currentRangeRouteValue` 가 클릭 핸들러에 전달됨) — 단, `currentRangeRouteValue` 의 source-of-truth 가 reset 되면 의도가 깨진다. 이 plan 은 그 source-of-truth 를 안정화한다.
- Deep-link 신규 진입 (Home 칩 / Settings 칩) 시에는 호출자의 `range` 파라미터가 우선해야 한다 — 그게 진입자의 의도. 기존 backstack entry 로의 복귀일 때만 사용자의 직전 선택을 우선한다.
- Process death (system kill) 후 복원에서는 사용자의 마지막 선택을 보존한다 (`rememberSaveable` 가 이미 보장; 이 plan 의 key 변경이 그 보장을 회복).
- range 외 다른 인자 (filterType / filterValue / source) 의 변경은 "다른 화면 인스턴스" 로 본다 — 그 경우는 새 backstack entry 가 push 되므로 본 plan 의 변경 범위 밖.

---

## Task 1: Add failing tests for range state survival

**Objective:** 두 가지 회귀 시나리오를 테스트로 고정해 fix 검증의 grounding 을 만든다.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/detail/InsightDrillDownRangeStateTest.kt`

**Steps:**
1. **Test A — pure state holder contract**: 새 helper `InsightDrillDownRangeState` (Task 2 에서 추가될 pure-Kotlin holder) 의 4 케이스를 고정한다.
   - 초기 진입: `initialRouteValue = "recent_3_hours"` → `currentRange == RECENT_3_HOURS`.
   - 사용자 선택 후 동일 holder 에 `select(RECENT_24_HOURS)` → `currentRange == RECENT_24_HOURS`.
   - "동일 인스턴스 + initialRouteValue 가 새로 들어옴 (예: 다른 인자로 화면 인자 갱신)": `onRouteArgsChanged(initialRouteValue = "all")` 가 사용자 선택 (`RECENT_24_HOURS`) 을 덮어쓰지 **않음** — `currentRange` 그대로.
   - "신규 backstack entry (다른 filterValue)": 별도의 holder 인스턴스 → 호출자의 initialRouteValue 가 그대로 적용.
2. **Test B — `routeValue` 전파 contract**: holder 의 `currentRange.routeValue` 가 `Routes.Insight.createForReason(...)` 의 `range` 파라미터로 들어갔을 때, 빌드된 URL 이 사용자가 마지막에 선택한 값을 query 로 포함함 확인. 4 range 값 × 2 source 값의 조합으로 parameterize.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.detail.InsightDrillDownRangeStateTest"` 가 RED 인 상태로 commit (helper 미구현이라 컴파일 실패가 정상 — RED).

## Task 2: Extract pure-Kotlin range state holder

**Objective:** Compose 외부에서 단위 테스트 가능한 pure holder 를 추출해 `rememberSaveable` 회귀를 다시는 막는다.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/detail/InsightDrillDownRangeState.kt`
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/detail/InsightDrillDownScreen.kt`

**Steps:**
1. `InsightDrillDownRangeState` 를 추가:
   - `class InsightDrillDownRangeState(initialRouteValue: String)` — 생성자에서 `InsightDrillDownRange.fromRouteValue(initialRouteValue)` 로 초기화.
   - `var currentRange: InsightDrillDownRange` — getter/setter 노출 (Compose 가 `MutableState` 로 hoist 가능하도록 별도 mutator 만 두고 read 는 read-only 도 가능; 테스트 가독성 우선해서 일반 var 로). 단순 implementation 으로는 `private val _state = mutableStateOf(...)` 를 wrap.
   - `fun select(range: InsightDrillDownRange)` — 사용자 선택; 내부 `_userOverridden = true` flag set.
   - `fun onRouteArgsChanged(initialRouteValue: String)` — `_userOverridden == false` 인 경우에만 새 인자값으로 갱신. true 면 ignore (사용자 선택 우선).
   - `companion object Saver` — `rememberSaveable` 가 직렬화 / 복원할 수 있도록 `Saver<InsightDrillDownRangeState, List<Any>>` 제공 (`currentRange.routeValue` + `_userOverridden` 두 필드만). process death 후에도 사용자 의도 복원 보장.
2. `InsightDrillDownScreen` 안에서 기존 `var selectedRange by rememberSaveable(initialRange) { mutableStateOf(...) }` 를:
   ```kotlin
   val rangeState = rememberSaveable(
       saver = InsightDrillDownRangeState.Saver,
   ) { InsightDrillDownRangeState(initialRouteValue = initialRange) }
   LaunchedEffect(initialRange) { rangeState.onRouteArgsChanged(initialRange) }
   val selectedRange = rangeState.currentRange
   ```
   로 교체. `FilterChip` 의 `onRangeSelected` 콜백은 `rangeState.select(it)` 로 분기.
3. `currentRangeRouteValue` 등 selectedRange 를 참조하던 모든 derived 값 (`remember(... selectedRange ...)`) 는 그대로 — `currentRange` 는 `MutableState` 로 wrap 되어 있으므로 Compose 가 read 를 추적.
4. Task 1 의 RED 테스트가 GREEN 으로 전환되는 것 확인.

## Task 3: Add Compose-level smoke test for back navigation round-trip

**Objective:** "Detail 갔다 와도 range 유지" 회귀를 화면 수준에서 1건 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/detail/InsightDrillDownRangeRoundTripTest.kt` (Robolectric + ComposeTestRule)

**Steps:**
1. Compose UI 테스트로 `InsightDrillDownScreen` 을 마운트, `onNotificationClick` 콜백은 단순 `var clickedId` 캡처로 stub. 실제 NavController 회귀는 instrumentation 영역 — 여기서는 동일 composable 인스턴스를 dispose 없이 유지한 채 `selectedRange` 의 internal 상태가 유지되는지를 검증.
2. (1) 초기 `initialRange = "recent_24_hours"` 로 mount → `최근 24시간` chip selected. (2) `최근 3시간` chip click → selected 변경. (3) `setContent` 의 외부 key 를 흔들어 recomposition 을 유도하되 `initialRange` 인자를 동일하게 다시 넘김 → `최근 3시간` 그대로 유지 확인. (4) 추가로 `initialRange` 를 `"all"` 로 바꿔 다시 push → 사용자 선택 우선 정책에 따라 여전히 `최근 3시간` 유지 (Task 2 의 `onRouteArgsChanged` 분기 검증).
3. NavController 까지 포함한 진짜 round-trip 검증은 Verification recipe 단계에서 ADB 로 수행 (Task 5).

## Task 4: Persist user selection into NavBackStackEntry savedStateHandle

**Objective:** Process 가 살아있고 단순 `popBackStack()` 으로 돌아오는 경로에서, Compose 의 `rememberSaveable` 가 backstack 복원 cycle 을 정확히 hit 하지 못하는 일부 케이스 (예: NavHost 가 entry 의 `Lifecycle` 을 STOPPED 까지 내렸다가 다시 STARTED 로 올리는 transition) 도 안전하게 커버.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/detail/InsightDrillDownScreen.kt`

**Steps:**
1. `AppNavHost` 의 `composable(Routes.Insight.route)` 블록에서 `backStackEntry.savedStateHandle` 을 hoist. `InsightDrillDownScreen` 에 `savedRangeRouteValue: String?` + `onRangeSelected: (String) -> Unit` 두 추가 인자를 전달:
   ```kotlin
   val handle = backStackEntry.savedStateHandle
   InsightDrillDownScreen(
       ...,
       savedRangeRouteValue = handle.get<String>(KEY_INSIGHT_RANGE),
       onRangeSelected = { handle[KEY_INSIGHT_RANGE] = it },
   )
   ```
   `KEY_INSIGHT_RANGE` 는 navigation 패키지 내 `private const val` 로 정의.
2. `InsightDrillDownScreen` 의 `rangeState` 초기값 결정 우선순위:
   - `savedRangeRouteValue` 가 non-null → 그 값으로 초기화 + `_userOverridden = true` (사용자 선택 회복).
   - 없으면 `initialRange` (URL 인자) 로 초기화 + `_userOverridden = false`.
3. `rangeState.select(...)` 호출과 함께 `onRangeSelected(it.routeValue)` 도 같이 호출 — savedStateHandle 의 source-of-truth 가 두 인스턴스 (rememberSaveable + savedStateHandle) 동시에 갱신되도록.
4. Task 1 / Task 3 의 단위 테스트가 여전히 GREEN. savedStateHandle 분기는 `AppNavHost` wiring 단이라 Robolectric 단위 테스트 범위 외 — Verification recipe (Task 5) 로 end-to-end 검증.

## Task 5: ADB end-to-end verification recipe

**Objective:** journey 의 Verification recipe 에 round-trip 시나리오 1건 추가 + 한 번 실행 후 PASS 기록.

**Files:**
- 수정: `docs/journeys/insight-drilldown.md`

**Steps:**
1. Verification recipe 에 다음 4 step 추가 (#4 ~ #7):
   ```bash
   # 4. 범위 칩 "최근 3시간" 탭 → reason chart / 카드 리스트가 3시간 윈도우로 좁혀지는지 확인
   adb shell input tap <x_3h_chip> <y_3h_chip>

   # 5. 첫 번째 NotificationCard 탭 → NotificationDetailScreen 진입 확인
   adb shell input tap <x_first_card> <y_first_card>

   # 6. 시스템 back → InsightDrillDown 으로 복귀
   adb shell input keyevent KEYCODE_BACK

   # 7. 범위 칩이 여전히 "최근 3시간" 으로 selected 인지 확인 (uiautomator dump)
   adb shell uiautomator dump /sdcard/ui.xml >/dev/null
   adb shell cat /sdcard/ui.xml | grep -A 1 'text="최근 3시간"' | grep -i 'selected="true"'
   ```
2. Recipe 실행 후 PASS / FAIL 결과를 journey 의 Change log 에 기록. PASS 면 `last-verified` 를 오늘 날짜로 갱신.
3. (Optional) Reason 네비게이션 round-trip — 사용자가 `최근 3시간` 선택 후 reason 카드 탭 → 새 인사이트 화면이 `최근 3시간` 으로 mount 되는지 확인.

## Task 6: Update journey doc

**Objective:** Known gap 을 resolved 로 마킹하고 Code pointers / Tests 섹션에 신규 holder 와 테스트를 등록.

**Files:**
- 수정: `docs/journeys/insight-drilldown.md`

**Steps:**
1. Known gaps 의 `range 선택이 URL 인자에 반영되어 back-stack 복원 시 초기값으로 돌아감.` bullet 을 다음 형식으로 교체:
   `- (resolved YYYY-MM-DD, plan 2026-04-26-insight-drilldown-range-state-survival) range 선택이 Detail 진입 후 back 으로 돌아오면 초기값으로 reset 되던 문제 — \`InsightDrillDownRangeState\` (rememberSaveable Saver) + \`NavBackStackEntry.savedStateHandle\` 이중 보존으로 사용자 선택 우선.`
2. Code pointers 에 추가:
   - `ui/screens/detail/InsightDrillDownRangeState` — pure holder + Saver
   - `navigation/AppNavHost#KEY_INSIGHT_RANGE` — savedStateHandle key (private const)
3. Tests 섹션에 추가:
   - `InsightDrillDownRangeStateTest` — pure holder 4 케이스 + URL 빌드 contract
   - `InsightDrillDownRangeRoundTripTest` — Compose 수준 round-trip
4. Change log 에 한 줄 추가 (PR 머지 시 commit 해시까지 첨부).

## Task 7: Self-review + PR

**Steps:**
- 모든 단위 테스트 통과 (`:app:testDebugUnitTest`).
- ADB Verification recipe 결과 (uiautomator dump 의 selected="true" 라인) 를 PR 본문에 첨부.
- PR 제목: `fix(insight): preserve range selection across detail round-trip`.
- 브랜치: `fix/insight-range-state-survival`.
- frontmatter `status: planned` → `status: in-progress` (구현 시작 시) → `status: shipped` + `superseded-by: docs/journeys/insight-drilldown.md` (PR 머지 시).

---

## Scope

**In:**
- `InsightDrillDownScreen` 의 `selectedRange` state 안정화 (rememberSaveable Saver + savedStateHandle 이중 보존).
- `InsightDrillDownRangeState` pure holder 신설 + Saver.
- `AppNavHost` 의 Insight composable wiring 갱신 (savedStateHandle key 1개).
- 단위 테스트 2종 + ADB recipe 1건.
- `docs/journeys/insight-drilldown.md` Known gap / Code pointers / Tests / Change log 갱신.

**Out:**
- Insight 화면의 다른 UX 개선 (PRIORITY 드릴다운 추가, 다른 reason 카테고리 chart 추가 등).
- `Routes.Insight` URL 스키마 변경 (back-stack 복원이 URL 갱신에 의존하지 않도록 설계).
- `InsightDrillDownBuilder` / `InsightDrillDownCopyBuilder` 등 도메인 빌더 변경.
- 다른 화면의 유사한 `rememberSaveable(initialArg)` 회귀 — 별도 sweep PR 로 분리 (insight-drilldown 한 곳만 우선 fix).

---

## Risks / open questions

- **Saver round-trip 의 enum 직렬화 안정성**: `InsightDrillDownRange` enum 의 `routeValue` 가 String 이므로 Saver 가 `listOf(routeValue, userOverridden)` 형태로 저장하면 안전. `enum.name` 을 직접 쓰지 않는 것은 enum 이름이 바뀌어도 routeValue 만 stable 하면 backward-compat 유지하기 위함.
- **`onRouteArgsChanged` 의 race**: `LaunchedEffect(initialRange)` 가 Composition 첫 진입에도 한 번 발화하므로, 생성자에서 이미 같은 값으로 초기화한 직후 같은 값으로 한 번 더 호출이 돌아간다. `_userOverridden` 가 false 인 동안엔 멱등이라 문제없음. Test A 의 첫 케이스가 이 멱등성을 간접 보장.
- **savedStateHandle 의 lifecycle**: `NavBackStackEntry` 가 destroy 되면 (사용자가 시스템 back 으로 InsightDrillDown 자체를 빠져나가면) savedStateHandle 도 사라짐. 이는 의도된 동작 — 다음에 같은 인사이트로 새로 진입하면 호출자의 URL `range` 가 진입자의 의도이므로 그것이 우선해야 함.
- **두 source-of-truth 의 일관성**: rememberSaveable 의 holder 와 savedStateHandle 두 군데에 같은 값을 쓰는 게 약간의 중복. holder 의 Saver 만으로 충분한지 vs. savedStateHandle 도 필요한지는 ADB 검증 (Task 5) 에서 확실히 분기. 만약 Saver 단독으로 모든 케이스가 PASS 라면 Task 4 의 savedStateHandle wiring 을 생략 가능 — 이 경우 Risks 에 "단일-source-of-truth 단순화" 로 후속 plan 등록.
- **다른 `rememberSaveable(initialArg)` 회귀**: 같은 안티패턴이 다른 화면에도 있을 가능성 (`HiddenNotificationsScreen` 의 initialFilter, `Routes.Rules` 의 highlightRuleId 등). 이 plan scope 밖이지만 implementer 가 발견하면 Known gap 으로만 추가 (별도 sweep PR 로 위임).

---

## Related journey

- [insight-drilldown](../journeys/insight-drilldown.md) — Known gap "range 선택이 URL 인자에 반영되어 back-stack 복원 시 초기값으로 돌아감." 가 본 plan 의 ship 으로 resolved 처리됨.
