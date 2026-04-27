---
status: planned
---

# Home 화면을 sub-section composable 로 split Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Refactor — behavior must not change.

**Goal:** `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeScreen.kt` (877 lines, over the 700-line critical threshold flagged by `code-health-scout` 2026-04-27) 를 의미 단위 sub-file 로 분해해 root 파일이 ≤ 350 lines 로 내려가게 한다. 현재 한 파일에 root composable 1개 + 12개의 `private fun` (`HomeRecentMoreRow` / `HomeNotificationAccessInlineRow` / `HomeNotificationAccessCard` / `InsightCard` / `ReasonBreakdownChart` / `InsightLinkText` / `TimelineCard` / `TimelineBarChart` / `TimelineBar` / `TimelineRangeChip` / `SmartTimelineCardContainer` / `StatPill`) 이 동거해 IDE 탐색 / git blame / plan-implementer 작업 위치 파악이 모두 무거워진다. 동작은 변경하지 않는다 — composable 시그니처 / state hoisting / repository flow 구독 / preview 전부 유지. 본 plan 은 직전 `2026-04-27-refactor-settings-screen-split.md` 에서 검증된 패턴 (characterization test → carve-out → 사이즈 검증) 을 그대로 재사용한다.

**Architecture:**
- 같은 패키지 (`ui/screens/home/`) 에 sub-file 3개 신설:
  - `HomeInsightSection.kt` — `InsightCard` + `ReasonBreakdownChart` + `InsightLinkText` (~3 composables, ~165 lines, 현 line 568→732)
  - `HomeTimelineSection.kt` — `TimelineCard` + `TimelineBarChart` + `TimelineBar` + `TimelineRangeChip` + `SmartTimelineCardContainer` + `StatPill` (~6 composables, ~265 lines, 현 line 733→877)
  - `HomeNotificationAccessSection.kt` — `HomeNotificationAccessInlineRow` + `HomeNotificationAccessCard` (~2 composables, ~135 lines, 현 line 469→567)
- `HomeScreen.kt` 는 root composable + 화면-레벨 state hoisting + `HomeRecentMoreRow` (root 와 결합도 높은 footer row) 만 보유. 다른 sub-section composable 들은 `internal` 가시성으로 같은 패키지에서 호출.
- 신규 파일은 모두 같은 package `com.smartnoti.app.ui.screens.home` — 외부 import 영향 없음.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit / Robolectric characterization tests (composable hierarchy node assertion).

---

## Product intent / assumptions

- 본 plan 은 **순수 리팩터** — 사용자 가시 동작 / Home insight 카드 / timeline 차트 / notification-access 안내 / StatPill 카운트 / `HomeRecentMoreRow` truncation footer 모두 변경 없음.
- 기존 `HomeScreen` composable 의 호출자 (`AppNavHost.kt`) 는 시그니처 변경 없음.
- 분해 단위는 "현재 파일 안에서 인접해 그룹된 composable 들" — 같은 영역끼리 묶는다 (Insight 3 / Timeline 6 / Access 2). 직전 SettingsScreen split 과 동일한 cut-and-paste 전략.
- carving 후 root 파일 = root composable + footer row (`HomeRecentMoreRow`) 만 남는다. 본 plan 은 "root 파일이 350 lines 이하" 를 통과 기준으로 한다 (직전 settings-split 의 ≤ 600 보다 공격적인 이유: HomeScreen 은 시작 시점 877 → settings 는 2244, 비례 목표).
- preview 가 있으면 sub-file 로 함께 이동 (현재 HomeScreen.kt 에 명시적 `@Preview` annotation 없음을 grep 으로 확인 — Task 1 에서 재검증).

---

## Task 1: Pin behavior with characterization tests [IN PROGRESS via PR #461]

**Objective:** Refactor 전에 현 화면의 가시 contract 를 테스트로 고정. 테스트 없이 877-line composable 을 조각내면 사용자 동작 회귀를 잡을 방법이 없다. SettingsScreen split 의 PR #457 패턴을 재사용.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/home/HomeScreenCharacterizationTest.kt`

**Steps:**
1. `composeTestRule.setContent { HomeScreen(...) }` 로 다음 affordance 노출을 assertion:
   - `StatPill` 3종 (`즉시` / `Digest` / `조용히`) 라벨 + 가짜 카운트 텍스트.
   - `HomeNotificationAccessCard` 또는 `HomeNotificationAccessInlineRow` 의 헤드라인 카피 (notification listener 권한 미허용 fake state).
   - `InsightCard` 의 카피 (예: "지난 7일 인사이트") + 적어도 한 개 link text.
   - `TimelineCard` 헤더 + `TimelineRangeChip` 라벨 (`24시간` / `7일` 등 현행 카피).
   - `HomeRecentMoreRow` "전체 N건 보기" footer (cap=5 시나리오).
2. fake `NotificationRepository` / `SettingsRepository` / `OnboardingRepository` 주입으로 알려진 state 로 렌더 후 텍스트 substring assertion.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.home.HomeScreenCharacterizationTest"` → 모두 GREEN (현 코드 기준).

## Task 2: Carve out Insight + Notification Access sub-files

**Objective:** 가장 결합도 낮은 두 영역 (insight 3 composable / notification-access 2 composable) 을 같은 패키지의 신규 파일로 이동. 파일 사이즈를 즉시 ~300 lines 깎는다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeInsightSection.kt`
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeNotificationAccessSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeScreen.kt`

**Steps:**
1. `InsightCard` + `ReasonBreakdownChart` + `InsightLinkText` 를 `HomeInsightSection.kt` 로 컷-앤-페이스트. visibility 를 `private` → `internal` 로 승격 (같은 패키지 root composable 이 호출).
2. `HomeNotificationAccessInlineRow` + `HomeNotificationAccessCard` 를 `HomeNotificationAccessSection.kt` 로 동일 패턴 이동.
3. import 정리 — 이동한 파일의 imports 는 새 파일에 복제하고 root 에서는 unused 만 제거.
4. `./gradlew :app:assembleDebug` 통과 + Task 1 테스트 GREEN.
5. `wc -l HomeScreen.kt` ≤ 580 인지 확인 (877 - 300 ≈ 577).

## Task 3: Carve out Timeline sub-file

**Objective:** 남은 가장 큰 영역 (timeline 6 composable + StatPill helper) 도 분리해 root 파일을 ≤ 350 lines 로 마무리. `StatPill` 은 root `HomeScreen` 도 직접 호출하므로 timeline 옆에 두고 `internal` 노출.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeTimelineSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeScreen.kt`

**Steps:**
1. `TimelineCard` + `TimelineBarChart` + `TimelineBar` + `TimelineRangeChip` + `SmartTimelineCardContainer` + `StatPill` 을 `HomeTimelineSection.kt` 로 cut-and-paste. visibility `private` → `internal`.
2. `wc -l HomeScreen.kt` ≤ 350 인지 검증 — 초과 시 `HomeRecentMoreRow` 도 같은 sub-file 또는 신규 `HomeRecentSection.kt` 로 이동 결정.
3. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 통과.

## Task 4: PR + journey doc note

**Objective:** Refactor 사실을 doc 에 한 줄로 기록 — 사용자 가시 동작 변경 없음을 명시해 home-overview journey 의 Verification recipe 가 영향받지 않음을 확인.

**Files:**
- (선택) 관련 journey 의 Code pointers 가 `HomeScreen.kt:###` 로 줄번호를 박아두지 않았는지 확인 — `.claude/rules/docs-sync.md` 가 줄번호 박지 말라고 했으니 file path 만 있다면 갱신 불필요. 본 plan ship 시 home-overview Known gaps 의 "code health (2026-04-27): HomeScreen 877 lines" bullet 을 Change log 의 한 줄 기록으로 대체.

**Steps:**
1. `./gradlew :app:assembleDebug` GREEN.
2. PR 본문에 다음 명시:
   - 3개 신규 sub-file + root file size before/after.
   - 동작 변경 없음 — Task 1 characterization test 가 그대로 GREEN 임을 명시.
   - home-overview journey 문서 본문 변경: Known gaps 의 "code health" bullet 제거 + Change log 에 "2026-04-27: HomeScreen split into 3 sub-files (insight / timeline / notification-access). Root file 877 → ≤ 350 lines. 동작 변경 없음."
3. PR 제목 후보: `refactor(home): split HomeScreen.kt into per-section sub-files`.
4. frontmatter 를 `status: shipped` 로 flip + Change log 에 PR 링크.

---

## Scope

**In:**
- 3개 신규 sub-file 로 composable 분리.
- `HomeScreen.kt` root file 사이즈 ≤ 350 lines.
- Characterization test (composable hierarchy assertion).
- home-overview journey 의 Known gap "code health (2026-04-27)" bullet 정리 + Change log 추가.

**Out:**
- 동작 변경 / state hoisting 재설계 / repository flow 구독 패턴 변경.
- Sub-section 내부 추가 분해 (예: `TimelineBarChart` 안의 sub-composable).
- Insight / timeline copy 변경 / 카드 순서 변경.
- 다른 큰 파일 (`SettingsRepository.kt` 705 lines, `NotificationDetailScreen.kt` 687 lines, listener `processNotification` 251 lines) — 별도 plan.

---

## Risks / open questions

- `HomeScreen.kt` 안에 `@Preview` annotation 이 있는지 — Task 1 직전에 grep 으로 재확인하고, 있으면 함께 sub-file 로 옮긴다. (현 시점 짧은 grep 으로 발견 안 됨 — Task 1 에서 binding 확인.)
- `internal` 승격이 같은 module 의 다른 파일에서 의도치 않게 호출되지 않는지 grep — 현재는 모두 `private` 였으므로 외부 호출자 없음. plan-implementer 가 cut-paste 후 `grep -r "InsightCard\|TimelineCard\|StatPill\|HomeNotificationAccessCard"` 로 cross-package 호출 부재 재확인.
- Compose recomposition perf 영향 없음 — 파일 분해는 코드 위치만 바꾸지 graph 를 바꾸지 않는다.
- characterization test 의 fake repository 주입 방식이 직전 SettingsScreen 과 다를 수 있음 (Home 은 더 많은 collaborator). 첫 task 에서 fake 빌더 패턴을 결정 — Settings 가 사용한 anonymous-object 패턴을 그대로 채택할지, 또는 in-memory fake class 를 새로 만들지는 plan-implementer 의 1차 PR 에서 결정 (저비용 결정).
- home-overview journey 의 Code pointers 가 줄번호를 박지 않았다는 가정 — `.claude/rules/docs-sync.md` 위반 여부를 plan-implementer 가 Task 4 직전에 한 번 확인.

---

## Related journey

[home-overview](../journeys/home-overview.md) — Known gaps 의 "code health (2026-04-27): HomeScreen 877 lines (over 700 critical threshold), root composable body spans ~270 lines (line 97→368), then 12 private composables ... Refactor candidate — split insight / timeline / access subsections into per-card files (mirrors the SettingsScreen split plan pattern)." 항목이 본 plan 의 source gap. ship 시 해당 bullet 을 Change log 한 줄로 대체.
