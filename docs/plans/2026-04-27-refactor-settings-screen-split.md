---
status: planned
---

# Settings 화면을 sub-section composable 로 split Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Refactor — behavior must not change.

**Goal:** `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` (2244 lines) 를 의미 단위 sub-file 로 분해해 단일 파일이 ~600 lines 이하로 내려가게 한다. 현재 한 파일에 root composable 1개 + 18개 이상의 `private fun` (DuplicateThresholdEditorRow / QuietHoursWindowPickerRow / QuietHoursPackagesPickerSheet / DeliveryProfileSettingsCard / DeliveryProfileEditorSection ...) 이 동거해 IDE 탐색 / git blame / 후속 plan-implementer 의 작업 위치 파악이 모두 무거워진다. 동작은 변경하지 않는다 — 컴포저블 시그니처 / state hoisting / settings flow 구독 / preview 전부 유지.

**Architecture:**
- 같은 패키지 (`ui/screens/settings/`) 에 sub-file 4개 신설:
  - `SettingsDuplicateSection.kt` — `DuplicateThresholdEditorRow` + `DuplicateThresholdPicker` + `DuplicateWindowPicker` (~3 composables, ~140 lines)
  - `SettingsQuietHoursSection.kt` — `QuietHoursWindowPickerRow` + `QuietHoursHourPicker` + `QuietHoursPackagesPickerRow` + `QuietHoursPackagesPickerSheet` + `QuietHoursPackagesSelectedSection` + `QuietHoursPackagesCandidateSection` (~6 composables, ~370 lines)
  - `SettingsDeliveryProfileSection.kt` — `DeliveryProfileSettingsCard` + `DeliveryProfileSummaryRow` + `DeliveryProfileEditorSection` + `DeliveryProfileControlGroup` + `DeliveryProfileOptionRow` (~5 composables, ~310 lines)
  - `SettingsOperationalSection.kt` — `OperationalSummaryCard` + `OperationalSummaryRow` + `ReplacementAutoDismissDurationPicker` + `IgnoredArchiveSettingsCard` + `AdvancedRulesEntryCard` (~5 composables, ~200 lines)
- `SettingsScreen.kt` 는 `SettingsScreen` root composable + 화면-레벨 state hoisting 만 보유. 다른 sub-section composable 들은 `internal` 가시성으로 같은 패키지에서 호출.
- 신규 파일은 모두 같은 package `com.smartnoti.app.ui.screens.settings` — import 추가 불필요.

**Tech Stack:** Kotlin, Jetpack Compose Material3, JUnit characterization tests (Compose preview render snapshot 또는 Robolectric `composeTestRule.setContent` 의 hierarchy node assertion).

---

## Product intent / assumptions

- 본 plan 은 **순수 리팩터** — 사용자 가시 동작 / settings persistence / quiet hours / duplicate threshold / delivery profile / 숨김 아카이브 토글 모두 변경 없음.
- 기존 `SettingsScreen` composable 의 호출자 (`AppNavHost.kt`) 는 시그니처 변경 없음.
- 분해 단위는 "현재 파일 안에서 인접해 그룹된 composable 들" — file 안 줄 번호 90~370 root, 388~424 ignored-archive entry, 521~700 duplicate / quiet hours, 700~1064 quiet hours sheet, 1110~ delivery profile. 같은 영역끼리 묶는다.
- carving 후 root file 은 root composable + entry-card composables (`AdvancedRulesEntryCard` 같은 nav 진입 wrapper) 만 남는다. 본 plan 은 "root 파일이 600 lines 이하" 를 통과 기준으로 한다.
- preview 가 있으면 sub-file 로 함께 이동.

---

## Task 1: Pin behavior with characterization tests

**Objective:** Refactor 전에 현 화면의 가시 contract 를 테스트로 고정. 테스트 없이 2244-line composable 을 조각내면 사용자 동작 회귀를 잡을 방법이 없다.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/settings/SettingsScreenCharacterizationTest.kt`

**Steps:**
1. `composeTestRule.setContent { SettingsScreen(...) }` 로 다음 affordance 노출을 assertion:
   - "조용한 시간" 카드 + 시작/종료 시각 picker row.
   - "중복 알림 묶기 임계값" 카드 + threshold picker + window picker.
   - "전달 모드" (delivery profile) 카드 + summary row.
   - "고급 규칙 편집 열기" entry card.
   - "무시된 알림 아카이브 표시" 토글.
2. fake `SettingsRepository` 를 주입해 `quietHoursEnabled=true / start=23 / end=7`, `duplicateDigestThreshold=3`, `replacementAutoDismissEnabled=false` 등 알려진 state 로 렌더 후 텍스트 substring assertion (`"23시"`, `"7시"`, `"3회"` 등).
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.settings.SettingsScreenCharacterizationTest"` → 모두 GREEN (현 코드 기준).

## Task 2: Carve out Quiet Hours + Delivery Profile sub-files

**Objective:** 가장 큰 두 영역 (quiet hours 6개 composable / delivery profile 5개 composable) 을 같은 패키지의 신규 파일로 이동. 파일 사이즈를 즉시 ~700 lines 깎는다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsQuietHoursSection.kt`
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsDeliveryProfileSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt`

**Steps:**
1. quiet hours 영역 (현 line 700~1063, 6 composables) 을 `SettingsQuietHoursSection.kt` 로 컷-앤-페이스트. visibility 를 `private` 에서 `internal` 로 승격 (같은 패키지 root composable 이 호출).
2. delivery profile 영역 (현 line 1110~) 5 composables 도 동일 패턴으로 `SettingsDeliveryProfileSection.kt` 로 이동.
3. import 정리 — 이동한 파일의 imports 는 새 파일에 복제하고 root 에서는 unused 만 제거.
4. `./gradlew :app:assembleDebug` 통과 + Task 1 테스트 GREEN.
5. `wc -l SettingsScreen.kt` 가 ~1500 lines 이하인지 확인.

## Task 3: Carve out Duplicate + Operational sub-files

**Objective:** 남은 두 영역 (duplicate threshold/window 3 composables / operational summary + ignored archive entry 5 composables) 도 분리해 root 파일을 ~600 lines 이하로 마무리.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsDuplicateSection.kt`
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsOperationalSection.kt`
- 변경 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt`

**Steps:**
1. Task 2 와 동일 패턴으로 두 영역을 새 파일로 이동.
2. `wc -l SettingsScreen.kt` ≤ 600 인지 검증 — 초과 시 `OperationalSummaryRow` 같은 가장 큰 잔존 composable 을 `SettingsOperationalSection.kt` 로 추가 이동.
3. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 통과.

## Task 4: PR + journey doc note

**Objective:** Refactor 사실을 doc 에 한 줄로 기록 — 사용자 가시 동작 변경 없음을 명시해 journey 문서의 Verification recipe 가 영향받지 않음을 확인.

**Files:**
- (선택) 관련 journey 의 Code pointers 가 `SettingsScreen.kt:###` 로 줄번호를 박아두지 않았는지 확인 — `.claude/rules/docs-sync.md` 가 줄번호 박지 말라고 했으니 file path 만 있다면 갱신 불필요.

**Steps:**
1. `./gradlew :app:assembleDebug` GREEN.
2. PR 본문에 다음 명시:
   - 4개 신규 파일 + root file size before/after.
   - 동작 변경 없음 — Task 1 characterization test 가 그대로 GREEN 임을 명시.
   - 어떤 journey 문서도 본문 변경 없음 (Code pointers 가 줄번호를 박지 않으므로 자동 안전).
3. PR 제목 후보: `refactor(settings): split SettingsScreen.kt into per-section sub-files`.
4. frontmatter 를 `status: shipped` 로 flip + Change log 에 PR 링크.

---

## Scope

**In:**
- 4개 신규 sub-file 로 composable 분리.
- `SettingsScreen.kt` root file 사이즈 ≤ 600 lines.
- Characterization test (composable hierarchy assertion).

**Out:**
- 동작 변경 / state hoisting 재설계 / settings flow 구독 패턴 변경.
- Sub-section 의 추가 분해 (예: `QuietHoursPackagesPickerSheet` 안의 sub-composable).
- Settings 카드 배열 / 순서 / 카피 변경.

---

## Risks / open questions

- composable preview 가 root file 안에 있다면 sub-file 로 함께 옮긴다 — preview 가 동작하는지 Android Studio 에서 한 번 확인.
- `internal` 승격이 같은 module 의 다른 파일에서 의도치 않게 호출되지 않는지 grep — 현재는 모두 `private` 였으므로 외부 호출자 없음.
- Compose recomposition perf 영향 없음 — 파일 분해는 코드 위치만 바꾸지 graph 를 바꾸지 않는다.

---

## Related journey

본 plan 은 사용자 가시 동작을 바꾸지 않으므로 어떤 journey 의 Observable steps / Exit state 도 갱신하지 않는다. Code pointers 가 줄번호를 박지 않은 한 (현 doc 규칙대로) 자동 안전.
