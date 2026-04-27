---
status: shipped
shipped: 2026-04-27
---

# Settings 화면의 suppression 클러스터 sub-section split Plan

## Change log

- 2026-04-27 — Task 1 (28 characterization tests) shipped via PR #471.
  Tasks 2-4 shipped: extracted `SettingsSuppressionInsightSection.kt`,
  `SettingsSuppressionManagementSection.kt`,
  `SettingsAppSelectionSection.kt`,
  `SettingsNotificationAccessSection.kt`, plus
  `SettingsSectionScaffold.kt` for the three reusable scaffold
  composables (Task 3 fallback to clear the ≤ 400 line root target).
  Root `SettingsScreen.kt` 1118 → 307 lines. Behavior unchanged —
  `SettingsScreenCharacterizationTest` GREEN (28 tests) before/after
  every extraction; full `:app:testDebugUnitTest` and
  `:app:assembleDebug` GREEN at completion. Journey `quiet-hours.md`
  Known-gap bullet flipped from "(partially resolved)" to
  "(resolved)" with both plan links.

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Pure refactor — behavior must not change. Pin with characterization tests before moving any composable.

**Goal:** `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` (현재 1118 lines) 의 잔여 ~750-line suppression-management / app-selection / notification-access / suppression-insight 클러스터를 같은 패키지의 sub-file 들로 카브아웃해 root 파일을 ≤ 400 lines 로 내린다. 직전 `2026-04-27-refactor-settings-screen-split.md` 가 quiet hours / delivery profile / duplicate / operational 4 영역을 떼어내 2244 → 1118 lines 로 압축했으나, 본문 Change log 가 명시한 대로 suppression 영역은 follow-up 으로 위임된 상태. 본 plan 은 그 follow-up 이다. 사용자 가시 동작 (suppression insight 카드 / suppression management editor / app picker / notification access 안내) 은 그대로 유지.

**Architecture:**
- 같은 패키지 (`ui/screens/settings/`) 에 sub-file 4개 신설:
  - `SettingsSuppressionInsightSection.kt` — `SuppressionInsightMetricStrip` + `SuppressionInsightMetricCard` + `SuppressionBreakdownList` + `SuppressionBreakdownRow` + `SuppressedAppInsightsList` + `SuppressedAppInsightRow` (~6 composables, 현 line 459→612 + 1061→1118, ~210 lines)
  - `SettingsSuppressionManagementSection.kt` — `SuppressionManagementCard` + `SuppressedSourceAppChips` (~2 large composables, 현 line 613→886, ~275 lines)
  - `SettingsAppSelectionSection.kt` — `AppSelectionGroup` + `AppSelectionRow` (~2 composables, 현 line 887→975, ~90 lines)
  - `SettingsNotificationAccessSection.kt` — `NotificationAccessCard` (~1 composable, 현 line 976→1060, ~85 lines)
- `SettingsScreen.kt` 는 root composable + `ExpandableSettingsSectionHeader` / `SettingsSubsection` / `ExpandableSettingsSubsection` (3개 reusable scaffold composable, 현 line 363→458) 만 보유. scaffold 들은 다른 sub-section 도 호출하므로 root 에 두거나 별도 `SettingsSectionScaffold.kt` 로 옮길지는 Task 4 에서 결정 (root 에 두는 편이 import 줄임).
- 신규 파일은 모두 같은 package `com.smartnoti.app.ui.screens.settings` — 외부 import 영향 없음.
- 직전 plan (`2026-04-27-refactor-settings-screen-split`) 이 검증한 패턴 (characterization test → carve-out → 사이즈 검증 → behavior unchanged) 을 그대로 재사용.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit / Robolectric characterization tests (composable hierarchy node assertion).

---

## Product intent / assumptions

- 본 plan 은 **순수 리팩터** — 사용자 가시 동작 / suppression insight 메트릭 / suppression management editor / app picker 동작 / notification access 카드 / settings persistence 모두 변경 없음.
- 기존 `SettingsScreen` composable 의 호출자 (`AppNavHost.kt`) 는 시그니처 변경 없음.
- 분해 단위는 "현재 파일 안에서 인접해 그룹된 composable 들" — 같은 영역끼리 묶는다 (insight 6 / management 2 / app selection 2 / access 1). 직전 SettingsScreen / HomeScreen split 과 동일한 cut-and-paste 전략.
- carving 후 root 파일 = root composable + 3개 reusable scaffold (`ExpandableSettingsSectionHeader`, `SettingsSubsection`, `ExpandableSettingsSubsection`) 만 남는다. 본 plan 은 "root 파일이 400 lines 이하" 를 통과 기준으로 한다 (root composable 자체가 ~270 lines + scaffold 3개 ~90 lines 합계 잔존 가정).
- preview 가 있으면 sub-file 로 함께 이동 (현재 SettingsScreen.kt 에 명시적 `@Preview` annotation 은 Task 1 에서 grep 으로 재검증).
- `internal` 가시성 승격은 같은 모듈 외부 호출자가 없음을 grep 으로 사전 확인 (현재 모두 `private` — 직전 settings-split 과 동일한 안전 가정).

판단 필요 (Risks / open questions 에 escalate):
- `SuppressionInsightMetricCard` / `SuppressedAppInsightRow` 가 현재 root composable 의 동일 카드 박스 안에서 직접 호출되는지, 헤더 scaffold 를 거쳐 호출되는지에 따라 분리 후 import 그래프가 달라진다. Task 1 의 grep 단계에서 확정.
- `SuppressionManagementCard` 는 ~165 lines 단일 composable 로 가장 큼 — 추가 분해 (예: editor row 만 별도) 는 본 plan 의 out-of-scope. 사이즈 단독으론 여전히 moderate 이지만 행동 단위가 한 카드 안에 응집되어 있어 추가 split 은 후속 plan 에서 가시 contract 변화 동반 시에만.

---

## Task 1: Pin behavior with characterization tests [IN PROGRESS via PR #471]

**Objective:** Refactor 전에 잔여 suppression 클러스터의 가시 contract 를 테스트로 고정. 직전 plan 의 `SettingsScreenCharacterizationTest` 가 quiet hours / duplicate / delivery / operational entry 만 cover — suppression insight / management / app picker / notification access 카드는 미커버. 테스트 없이 750-line 영역을 조각내면 회귀를 잡을 방법이 없다.

**Files:**
- 변경 또는 신규 `app/src/test/java/com/smartnoti/app/ui/screens/settings/SettingsScreenCharacterizationTest.kt`
  (직전 plan 이 만든 파일을 확장하는 편이 자연스러움 — 동일 fixture 재사용)

**Steps:**
1. fake `SettingsRepository` / `SuppressionInsightProvider` (또는 동등 source) 를 주입해 다음 affordance 노출을 assertion:
   - `SuppressionInsightMetricStrip` 의 카피 (예: "정리됨 N건" / "차단된 앱 K개" 같은 현행 라벨, 코드에서 정확 문자열 추출).
   - `SuppressionBreakdownList` 의 row 1건 이상 (예: "DIGEST · n건").
   - `SuppressedAppInsightsList` 의 app 1건 이상 (앱 라벨 + "n건 정리" copy).
   - `SuppressionManagementCard` 의 헤더 카피 + 토글 / chip 영역.
   - `SuppressedSourceAppChips` 의 chip 1건 이상 + "모두 해제" / "추가" 같은 액션 버튼.
   - `NotificationAccessCard` 의 권한 안내 헤드라인 + CTA 버튼 라벨 (현재 빌드 카피).
2. 빈 상태 (insight 0건 / suppressed app 0건) 분기도 별도 assertion — empty-state copy 가 현행 그대로 유지되는지 확인.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.settings.SettingsScreenCharacterizationTest"` → 모두 GREEN (현 코드 기준).
4. 추가로 `grep -n "@Preview" SettingsScreen.kt` 로 잔존 preview 여부 확인 — 있으면 Task 2/3 에서 sub-file 과 함께 이동.

## Task 2: Carve out suppression insight + suppression management sub-files

**Objective:** 가장 큰 두 영역 (insight 6 composables ~210 lines + management 2 composables ~275 lines) 을 같은 패키지의 신규 파일로 이동. root 사이즈를 즉시 ~485 lines 깎는다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsSuppressionInsightSection.kt`
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsSuppressionManagementSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt`

**Steps:**
1. suppression insight 6 composables (현 line 459→612 + 1061→1118) 를 `SettingsSuppressionInsightSection.kt` 로 컷-앤-페이스트. visibility 를 `private` 에서 `internal` 로 승격 (같은 패키지 root composable 이 호출).
2. suppression management 2 composables (현 line 613→886) 도 `SettingsSuppressionManagementSection.kt` 로 동일 패턴 이동.
3. import 정리 — 이동한 파일에 imports 복제, root 에서 unused 제거.
4. `./gradlew :app:assembleDebug` 통과 + Task 1 테스트 GREEN.
5. `wc -l SettingsScreen.kt` ≤ 700 인지 확인.

## Task 3: Carve out app selection + notification access sub-files

**Objective:** 남은 두 영역 (app selection 2 composables / notification access 1 composable) 도 분리해 root 파일을 ≤ 400 lines 로 마무리.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsAppSelectionSection.kt`
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsNotificationAccessSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt`

**Steps:**
1. Task 2 와 동일 패턴으로 두 영역을 새 파일로 이동.
2. `wc -l SettingsScreen.kt` ≤ 400 인지 검증 — 초과 시 reusable scaffold 3개 (`ExpandableSettingsSectionHeader` / `SettingsSubsection` / `ExpandableSettingsSubsection`) 를 별도 `SettingsSectionScaffold.kt` 로 추가 이동 (Architecture section 의 옵션).
3. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 통과.

## Task 4: PR + journey doc note

**Objective:** Refactor 사실을 doc 에 한 줄로 기록 — 사용자 가시 동작 변경 없음을 명시해 journey 문서의 Verification recipe 가 영향받지 않음을 확인.

**Files:**
- 변경 직전 plan `docs/plans/2026-04-27-refactor-settings-screen-split.md` Change log 에 "잔여 suppression cluster 는 plan `2026-04-27-refactor-settings-suppression-cluster-split.md` 로 후속 처리됨" 한 줄 추가.
- (선택) 관련 journey (`quiet-hours.md` / `digest-suppression.md` / `protected-source-notifications.md`) 의 Code pointers 가 `SettingsScreen.kt:###` 로 줄번호를 박아두지 않았는지 확인 — `.claude/rules/docs-sync.md` 가 줄번호 박지 말라고 했으니 file path 만 있다면 갱신 불필요.
- `docs/journeys/quiet-hours.md` Known gap 의 "(partially resolved 2026-04-27 …)" bullet 에 본 plan 링크 추가 (gap 본문은 변경하지 않음, "→ plan: …" 만 추가).

**Steps:**
1. `./gradlew :app:assembleDebug` GREEN.
2. PR 본문에 다음 명시:
   - 4개 신규 파일 + root file size before/after.
   - 동작 변경 없음 — Task 1 characterization test 가 그대로 GREEN 임을 명시.
   - 어떤 journey 문서도 본문 변경 없음 (Code pointers 가 줄번호를 박지 않으므로 자동 안전).
3. PR 제목 후보: `refactor(settings): split suppression / app-selection / access cluster into sub-files`.
4. frontmatter 를 `status: shipped` 로 flip + Change log 에 PR 링크.

---

## Scope

**In:**
- 4개 신규 sub-file 로 composable 분리 (insight / management / app selection / notification access).
- `SettingsScreen.kt` root file 사이즈 ≤ 400 lines.
- Characterization test 확장 (직전 plan 의 fixture 재사용).
- 직전 plan 의 Change log 에 follow-up 링크 한 줄.

**Out:**
- 동작 변경 / state hoisting 재설계 / settings flow 구독 패턴 변경.
- `SuppressionManagementCard` 같은 단일 큰 composable 의 추가 분해.
- Settings 카드 배열 / 순서 / 카피 변경.
- `SettingsRepository.kt` (705 lines, 별도 code-health Moderate finding) 의 split — 본 plan 은 화면 composable 분해만 다룸. SettingsRepository 분해는 별도 plan.

---

## Risks / open questions

- composable preview 가 root file 안에 있다면 sub-file 로 함께 옮긴다 — Task 1 grep 에서 사전 확인.
- `internal` 승격이 같은 module 의 다른 파일에서 의도치 않게 호출되지 않는지 grep — 현재는 모두 `private` 였으므로 외부 호출자 없음.
- Compose recomposition perf 영향 없음 — 파일 분해는 코드 위치만 바꾸지 graph 를 바꾸지 않는다.
- `SuppressionManagementCard` 단일 composable 이 ~165 lines 로 모듈 분해 후에도 가장 큼 — 후속 plan 에서 가시 contract 변경 동반 (예: suppression management 자체의 UX 재설계) 시 추가 분해. 본 plan 단독으론 건드리지 않음.
- 직전 plan 의 ≤ 600 lines 통과 기준이 실제로는 1118 lines 에 머물렀다 — 본 plan 의 ≤ 400 목표도 보수적으로 잡았으나, root composable 자체가 ~270 lines + scaffold 3개 ~90 lines = ~360 lines 합산이라 통과 가능 추정. 미달 시 scaffold 분해 fallback (Task 3 step 2).

---

## Related journey

본 plan 은 사용자 가시 동작을 바꾸지 않으므로 어떤 journey 의 Observable steps / Exit state 도 갱신하지 않는다. `docs/journeys/quiet-hours.md` 의 "code health (2026-04-27): … 후속 plan should address" Known gap bullet 에 본 plan 링크가 한 줄 추가된다 (gap 본문 미변경). plan ship 시점에 해당 Known gap bullet 은 "(resolved 2026-04-27, plan `…suppression-cluster-split`)" prefix 로 갱신되며, journey Change log 에 한 줄 추가된다.
