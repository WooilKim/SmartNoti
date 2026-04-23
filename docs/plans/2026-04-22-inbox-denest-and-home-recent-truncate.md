---
status: shipped
related-journeys:
  - docs/journeys/home-overview.md
  - docs/journeys/inbox-unified.md
superseded-by:
  - ../journeys/home-overview.md
  - ../journeys/inbox-unified.md
---

> **Status note (2026-04-22 triage):** All Tasks shipped via PR #265 (`85fd8b3` — Task 0 RED tests + Task 1 Home recent truncation + Task 2 Inbox de-nest via `HiddenScreenMode.Standalone/Embedded`). Frontmatter flipped from `planned` to `shipped`.

# 정리함 nested-tab 제거 + 홈 "방금 정리된 알림" 길이 제어

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 두 가지 UX 마찰을 한 PR 로 동시에 해결한다.

1. **정리함 (`InboxScreen`) 의 nested-tab 모순:** outer 탭이 `Digest / 보관 중 / 처리됨` 으로 명시적으로 선택을 받았는데도 `보관 중` / `처리됨` 진입 시 `HiddenNotificationsScreen` 이 자체 `HiddenTabRow` (ARCHIVED / PROCESSED) 를 또 렌더한다. 사용자가 "보관 중" 을 골라도 내부에서 또 골라야 하고, 외부 "보관 중" + 내부 "처리됨" 같은 모순 조합도 가능. → outer 탭 선택만으로 의도가 명확히 전달되도록 내부 탭/헤더를 숨기고 outer 선택으로 pre-resolve.
2. **홈 (`HomeScreen`) "방금 정리된 알림" 의 무한 스크롤:** 현재 `items(recent, ...)` 가 `observeAllFiltered(...)` **전체** 피드를 그대로 렌더한다. 알림이 쌓일수록 Home 이 inbox 의 사본이 되어 "한 화면 요약" 이라는 정체성을 잃는다. → 상위 N (기본 5) 건만 노출 + 마지막에 `전체 N건 보기 →` 행 추가, 탭 시 정리함으로 navigate.

두 변경은 자연스럽게 묶인다 — 홈의 "전체 보기" 의 destination 이 정리함이므로 정리함 UX 가 명확해야 홈 truncate 가 의미를 가진다.

**Architecture:** 두 가닥의 변경:

1. **`HiddenNotificationsScreen` 에 `mode` 파라미터 추가** (sealed class):
   - `Standalone(initialFilter: SilentGroupKey?)` — 기존 동작 (legacy `Routes.Hidden` deep link 호환). ScreenHeader + HiddenTabRow 모두 렌더, ARCHIVED 기본.
   - `Embedded(silentMode: SilentMode)` — outer 화면이 헤더와 탭 선택을 이미 표시하므로 자체 ScreenHeader + HiddenTabRow 를 렌더하지 않음. 본문은 `silentMode` 에 해당하는 그룹 리스트만.
   - `InboxScreen` 은 `InboxTab.Archived` → `Embedded(SilentMode.ARCHIVED)`, `InboxTab.Processed` → `Embedded(SilentMode.PROCESSED)` 로 매핑해 위임.

2. **`HomeRecentNotificationsTruncation` 순수 함수 + UI 적용**: builder 가 `(recent, capacity)` → `RecentNotificationsView(visible: List<NotificationUiModel>, hiddenCount: Int)` 를 반환. Home 의 LazyColumn 이 `visible` 만 `items(...)` 로 렌더하고, `hiddenCount > 0` 일 때 `HomeRecentMoreRow` ("전체 N건 보기 →") 를 마지막에 추가. 탭 → `onSeeAllRecent` 콜백 → `navigateToTopLevel(Routes.Inbox.route)`.

**카피 / capacity 결정:**
- Home truncate 기본값 `RECENT_CAP = 5` — StatPill (3) + 카드 4–5 개가 첫 화면 viewport 에 한 번에 잡히는 분량. 내부 상수로 두고 후속 a/b polish 여지 남김.
- "전체 보기" 행 카피: `전체 N건 보기 →` (N = `hiddenCount + visible.size`). hiddenCount 가 정확히 보일수록 사용자가 inbox 의 깊이를 가늠할 수 있다.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit 4 + Compose UI Test (`createComposeRule` 가 repo 에 부재 → headless 단위 테스트 + ADB smoke 로 검증).

---

## Product intent / assumptions

- **Home 은 "요약" 이고 정리함은 "검토" 다.** Home 에 전체 inbox 를 펼치면 두 화면의 역할이 무너진다. 5건 cap 은 "방금" 이라는 카피와 정합 — 정확한 정의는 "최신순 5건" 으로, 시간 cutoff 는 의도적으로 단순화 (cutoff 룰은 후속 polish 에서 도입 가능).
- **정리함 outer 탭이 truth source 다.** outer 탭에서 "보관 중" 을 골랐으면 본문은 ARCHIVED 만 보여야 한다. 두 번 묻지 않는다. 외부 "보관 중" + 내부 "처리됨" 같은 모순 조합은 사용자 mental model 을 흐리고 버그처럼 느껴지는 신호.
- **레거시 `Routes.Hidden` deep-link 는 유지한다** (tray group-summary contentIntent + 온보딩 흐름). `HiddenNotificationsScreen` 의 standalone 모드가 그 호환을 그대로 책임진다 — 이번 변경은 standalone 동작을 건드리지 않는다.
- **"전체 보기" 가 정리함으로 가는 게 자연스럽다 vs Digest 탭으로 직접 가는 게 자연스럽다 — 후자는 너무 좁은 가정.** 사용자가 보고 싶은 건 "방금 정리된 모든 알림" 이지 "방금 묶인 Digest 만" 이 아님. 정리함의 기본 sub-tab (Digest) 으로 진입해 사용자가 다시 outer 탭으로 ARCHIVED/PROCESSED 를 고르는 흐름이 가장 안전.
- **`HomeRecentNotificationsTruncation` 은 pure function** — Home 의 무거운 viewmodel-less 조립과 어울리고, 유닛 테스트가 쉬움.

---

## Task 0: Add failing unit tests for both pure-function contracts (RED) [IN PROGRESS via PR pending]

**Objective:** TDD-first. 구현 전에 두 contract 테스트를 추가해 RED 상태로 두고, Task 1 / Task 2 가 GREEN 으로 전환.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/home/HomeRecentNotificationsTruncationTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/inbox/InboxToHiddenScreenModeMapperTest.kt`

**Steps:**

1. `HomeRecentNotificationsTruncationTest`:
   - `truncate(emptyList(), capacity = 5)` → `visible.isEmpty()`, `hiddenCount == 0`
   - `truncate(list of 3, 5)` → `visible.size == 3`, `hiddenCount == 0`
   - `truncate(list of 5, 5)` → `visible.size == 5`, `hiddenCount == 0`
   - `truncate(list of 12, 5)` → `visible.size == 5`, `hiddenCount == 7`, visible 은 입력 head 5개와 동일
   - `truncate(list of 6, 0)` → `visible.isEmpty()`, `hiddenCount == 6` (capacity 0 edge)
2. `InboxToHiddenScreenModeMapperTest`:
   - `mapToMode(InboxTab.Archived)` → `HiddenScreenMode.Embedded(SilentMode.ARCHIVED)`
   - `mapToMode(InboxTab.Processed)` → `HiddenScreenMode.Embedded(SilentMode.PROCESSED)`
   - `mapToMode(InboxTab.Digest)` → throws / returns null (Digest 는 Hidden 으로 위임하지 않으므로 호출 자체가 잘못)
3. `./gradlew :app:testDebugUnitTest --tests "*HomeRecentNotificationsTruncationTest*"` + `--tests "*InboxToHiddenScreenModeMapperTest*"` 모두 컴파일 실패 (대상 클래스 미존재) → RED.

## Task 1: Implement `HomeRecentNotificationsTruncation` builder + Home UI 적용 [IN PROGRESS via PR pending]

**Objective:** Home "방금 정리된 알림" 을 5건 + 전체 보기 행으로 축소.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeRecentNotificationsTruncation.kt`
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeScreen.kt`
- 수정: `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt` (Home 의 `onSeeAllRecent` 콜백 wiring)

**Steps:**

1. `HomeRecentNotificationsTruncation` object 작성:
   ```kotlin
   internal object HomeRecentNotificationsTruncation {
       const val DEFAULT_CAPACITY = 5

       data class View(
           val visible: List<NotificationUiModel>,
           val hiddenCount: Int,
       )

       fun truncate(
           recent: List<NotificationUiModel>,
           capacity: Int = DEFAULT_CAPACITY,
       ): View {
           val safeCapacity = capacity.coerceAtLeast(0)
           val visible = recent.take(safeCapacity)
           val hiddenCount = (recent.size - visible.size).coerceAtLeast(0)
           return View(visible, hiddenCount)
       }
   }
   ```
2. `HomeScreen` 의 `items(recent, ...) { NotificationCard(...) }` 블록을:
   ```kotlin
   val recentView = remember(recent) { HomeRecentNotificationsTruncation.truncate(recent) }
   items(recentView.visible, key = { it.id }) { ... }
   if (recentView.hiddenCount > 0) {
       item {
           HomeRecentMoreRow(
               totalCount = recent.size,
               onClick = onSeeAllRecent,
           )
       }
   }
   ```
   로 교체. `EmptyState` 분기는 `recentView.visible.isEmpty() && recentView.hiddenCount == 0` 으로 갱신 (recent 가 정확히 비어 있을 때만).
3. `HomeScreen` signature 에 `onSeeAllRecent: () -> Unit` 파라미터 추가. `AppNavHost` 가 `navigateToTopLevel(Routes.Inbox.route)` 를 넘김.
4. `HomeRecentMoreRow` composable 작성 — 다른 Home row 와 일관된 톤 (`SmartSurfaceCard` 또는 `OutlinedButton`-스타일 1행). 카피: `전체 N건 보기 →`. `Routes.Inbox` 의 기본 sub-tab (Digest) 로 진입.
5. `./gradlew :app:testDebugUnitTest --tests "*HomeRecentNotificationsTruncationTest*"` GREEN 확인.

## Task 2: Implement `HiddenScreenMode` + Inbox 위임 변경 [IN PROGRESS via PR pending]

**Objective:** `HiddenNotificationsScreen` 이 외부에서 호출될 때 자체 ScreenHeader + HiddenTabRow 를 숨기고, outer 탭 선택을 그대로 따른다.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/hidden/HiddenNotificationsScreen.kt`
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxScreen.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxToHiddenScreenModeMapper.kt`

**Steps:**

1. `HiddenNotificationsScreen.kt` 에 sealed class 정의:
   ```kotlin
   sealed class HiddenScreenMode {
       data class Standalone(val initialFilter: SilentGroupKey? = null) : HiddenScreenMode()
       data class Embedded(val silentMode: SilentMode) : HiddenScreenMode()
   }
   ```
2. `HiddenNotificationsScreen` signature 변경: `initialFilter: SilentGroupKey?` 파라미터를 `mode: HiddenScreenMode = HiddenScreenMode.Standalone()` 으로 교체. 기존 호출자 (`AppNavHost` 의 deep-link route) 는 `mode = HiddenScreenMode.Standalone(initialFilter = ...)` 로 한 줄 마이그레이션.
3. 본문 분기:
   - `Standalone` → 기존 동작 그대로 (ScreenHeader + HiddenTabRow + body). `selectedTab` 은 `rememberSaveable` 그대로 유지하고 `LaunchedEffect(initialFilter)` 도 유지.
   - `Embedded` → ScreenHeader 와 HiddenTabRow 를 렌더하지 않음. `selectedTab` 은 `mode.silentMode` 에서 한 번에 도출 (rememberSaveable 불필요). `pendingClearAll` / bulk action / preview 카드 등 본문 로직은 동일.
4. `InboxToHiddenScreenModeMapper` object 작성:
   ```kotlin
   internal object InboxToHiddenScreenModeMapper {
       fun mapToMode(tab: InboxTab): HiddenScreenMode.Embedded = when (tab) {
           InboxTab.Archived -> HiddenScreenMode.Embedded(SilentMode.ARCHIVED)
           InboxTab.Processed -> HiddenScreenMode.Embedded(SilentMode.PROCESSED)
           InboxTab.Digest -> error("Digest is not delegated to HiddenNotificationsScreen")
       }
   }
   ```
5. `InboxScreen` 의 `when (selectedTab)` 분기 변경:
   - `InboxTab.Digest` → `DigestScreen(...)` 그대로
   - `InboxTab.Archived`, `InboxTab.Processed` → `HiddenNotificationsScreen(contentPadding = innerPadding, onNotificationClick = onNotificationClick, onBack = onBack, mode = InboxToHiddenScreenModeMapper.mapToMode(selectedTab))`
6. `./gradlew :app:testDebugUnitTest --tests "*InboxToHiddenScreenModeMapperTest*"` GREEN. 전체 단위 테스트 초록 유지.

## Task 3: ADB smoke + journey docs sync + ship [IN PROGRESS via PR pending]

**Objective:** 실 디바이스에서 두 변경 모두 의도대로 동작 확인 + journey 문서 두 개 동기화 + PR 마감.

**Files:**
- 수정: `docs/journeys/home-overview.md`
- 수정: `docs/journeys/inbox-unified.md`
- 수정: `docs/journeys/README.md` (Verification log 항목 — 본 PR 머지 후 journey-tester 의 verification 결과 기록)

**Steps:**

1. `./gradlew :app:assembleDebug` + `adb -s emulator-5554 install -r ...` + 앱 실행. 알림 12건 정도 게시 (`com.smartnoti.testnotifier` 도움 권장):
   - Home → "방금 정리된 알림" 섹션이 5건 카드 + `전체 12건 보기 →` 행으로 끝나는지 확인.
   - 행 탭 → 정리함 탭으로 이동, 기본 Digest sub-tab 선택 상태인지 확인.
   - 정리함 → "보관 중" 탭 탭 → 본문이 곧바로 ARCHIVED 카드만 렌더, 내부 ARCHIVED/PROCESSED row 가 사라졌는지 확인.
   - 정리함 → "처리됨" 탭 탭 → 본문이 PROCESSED 카드만 렌더.
   - tray deep-link (`Routes.Hidden`) 시뮬레이션 — Hidden 화면이 standalone 모드로 ScreenHeader + 자체 탭과 함께 그대로 뜨는지 확인 (기존 동작 회귀 없음).
2. `docs/journeys/home-overview.md`:
   - Observable step 10 의 "최상단 몇 건" 표현을 정확히 갱신: "최신 5건 + 그 이상이면 `전체 N건 보기 →` 행이 마지막에 마운트, 탭 시 정리함 진입".
   - Code pointers 에 `HomeRecentNotificationsTruncation` + `HomeRecentMoreRow` 추가.
   - Known gaps 의 "스크롤이 길어지면 위치 복원 UX 미검증" 항목 정리 — truncate 으로 스크롤 길이 자체가 짧아져 해당 gap 의 시급성이 사라짐. 한 줄 footnote 로 "5건 cap 으로 해소" 명시.
   - Change log 에 오늘 (2026-04-22) 행 추가: PR 번호 + 요약.
3. `docs/journeys/inbox-unified.md`:
   - Observable step 4 의 "현재 구현: `HiddenNotificationsScreen` 이 자체 HiddenTabRow 에서 Archived/Processed 를 다시 선택 가능 — 이는 의도된 중첩이며 후속 polish 대상" 문장을 갱신: "Inbox 에서 진입할 때는 `HiddenScreenMode.Embedded` 로 위임되어 자체 ScreenHeader + HiddenTabRow 가 숨겨지고 outer 탭 선택만으로 본문이 결정된다. Standalone 진입 (`Routes.Hidden` deep link) 은 기존대로 자체 헤더/탭 유지."
   - Code pointers 에 `HiddenScreenMode` + `InboxToHiddenScreenModeMapper` 추가.
   - Known gaps 의 첫 번째 항목 ("nested tab UX") 제거.
   - Change log 에 오늘 (2026-04-22) 행 추가: PR 번호 + 요약.
4. `docs/journeys/README.md` 의 Verification log 는 머지 후 journey-tester 가 채움 (본 plan 의 실행자가 작성하지 않음).
5. PR 제목: `feat(home,inbox): truncate Home recent list and de-nest Inbox sub-tabs`. 본문에 plan path + 두 journey 링크.
6. 본 plan 의 frontmatter: 머지 후 `status: shipped` + `superseded-by:` 두 journey 링크로 플립 (별도 follow-up commit 또는 PM 머지 직후 직접).

---

## Scope

**In:**
- `HomeScreen` 의 "방금 정리된 알림" 영역을 `HomeRecentNotificationsTruncation` 으로 5건 cap + 전체 보기 행.
- `HiddenScreenMode` sealed class + `HiddenNotificationsScreen` 의 standalone/embedded 분기.
- `InboxScreen` 이 outer 탭 선택을 `InboxToHiddenScreenModeMapper` 로 매핑해 위임.
- 두 journey 문서 동기화.
- 단위 테스트 2개 (RED-first).
- ADB smoke (uiautomator 또는 시각 확인).

**Out:**
- 정리함 outer 탭의 구조 자체 변경 (예: `Digest / 조용히` 2탭으로 줄이고 ARCHIVED/PROCESSED 를 chip 으로 강등) — 별도 plan. 본 PR 은 nested-tab 제거에만 한정.
- 시간 버킷 ("방금 / 오늘 / 이전") 같은 Home recent 의 의미 그룹핑 — 별도 plan.
- Digest sub-tab 의 진입점 (PassthroughReviewCard, StatPill 칩 등) 재배선 — 별도 plan.
- `HomeRecentMoreRow` 에 hidden hidden-count breakdown (Priority / Digest / Silent 비율) — 후속 polish.
- HiddenNotificationsScreen 의 PROCESSED 탭에 PROCESSED-only bulk action — 별도 plan.

---

## Risks / open questions

- **Risk: deep-link 회귀.** `Routes.Hidden` (tray group-summary contentIntent) 이 standalone 모드로 잘 들어가는지가 핵심. Task 3 ADB smoke 에서 명시 검증.
- **Risk: rememberSaveable 동작.** Embedded 모드에서 `selectedTab` 이 rememberSaveable 을 사용하지 않으므로 Inbox outer 탭이 바뀔 때마다 본문도 재계산된다 — 이건 의도된 동작 (outer 가 truth source). 다만 PROCESSED → ARCHIVED → PROCESSED 빠르게 토글 시 scroll position 회복은 별도 검증 필요. 실패 시 후속 issue 로 넘김.
- **Open: `RECENT_CAP = 5` 가 적절한가?** 5 는 직관적 추정. 사용자가 "더 많이 보고 싶다" 피드백을 내면 7 또는 10 으로 a/b. 현재로서는 internal const 로 두고 후속 polish 에서 설정 노출 여지.
- **Open: "전체 보기" 의 destination 이 정리함 Digest 가 아니라 다른 sub-tab 이어야 하는가?** 현재 가정은 Digest (가장 흔한 "정리됨" 상태). 사용자가 "보관 중을 보고 싶다" 가 다수면 destination 을 outer 탭 자동 선택 (예: "전체 보기 → 보관 중 N건") 으로 분기 가능. 본 plan 은 단순화를 우선.
- **Risk: testRule 부재.** 현재 repo 에 Compose UI 테스트 인프라가 부재 — 단위 테스트는 pure function 만 커버하고 UI 행위는 ADB smoke 에 의존. 회귀 가드는 약함.

---

## Change log

- 2026-04-22: 사용자 UX 피드백 (Home 스크롤 길이 + 정리함 nested-tab 모순) 을 받아 단일 plan 으로 묶음. 두 변경이 destination 으로 연결돼 있어 분리 PR 보다 묶음 PR 이 사용자 검증을 한 번에 가능하게 함.
