---
status: shipped
shipped: 2026-04-26
superseded-by: docs/journeys/quiet-hours.md
---

# Settings 화면에 Quiet Hours 시작/종료 시간 editor 추가 Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 Settings 화면의 "운영 상태" 섹션에서 `quietHoursStartHour` / `quietHoursEndHour` 를 직접 조정할 수 있도록 한다. 현재는 토글 (enabled) 만 노출되고 시작/종료 시간은 default 23/7 에 묶여 있어 — (a) 본인 라이프스타일에 맞게 조용한 시간대를 옮기고 싶은 사용자가 우회 경로 없이 막혀 있고, (b) `docs/journeys/quiet-hours.md` 의 verification recipe 가 명시한 "Settings 의 시작/종료 시간을 현재 시각을 포함하도록 조정" prereq 가 빌드에서 수행 불가능해 in-window DIGEST 분기를 라이브로 관측할 수 있는 경로 자체가 없다. 이 plan 이 ship 되면 사용자는 Settings 에서 두 시각 picker 를 탭해 임의 hour 로 변경 가능하고, journey-tester 는 default `[23,7)` 범위 밖에서도 in-window 분기를 ADB 없이 재현할 수 있다.

**Architecture:**
- `SettingsRepository` 에 `setQuietHoursStartHour(hour: Int)` / `setQuietHoursEndHour(hour: Int)` suspend setter 2개 추가. DataStore 키 `QUIET_HOURS_START_HOUR` / `QUIET_HOURS_END_HOUR` 는 이미 정의돼 있고 `observeSettings()` 도 이미 두 값을 읽어 `SmartNotiSettings` 에 채우고 있으므로 setter 만 누락된 상태.
- `SettingsScreen.kt` 의 `OperationalSummaryCard` 콜백 시그니처를 확장 — 현재 `onQuietHoursEnabledChange: (Boolean) -> Unit` 만 받지만, `onQuietHoursStartHourChange: (Int) -> Unit` + `onQuietHoursEndHourChange: (Int) -> Unit` 두 개 추가.
- `OperationalSummaryRow("Quiet Hours", ...)` trailing slot 의 Switch 만 있는 구조에서, Switch 가 ON 일 때만 row 아래에 picker 두 개 (시작 hour / 종료 hour) 를 렌더하는 구조로 확장. picker 는 Compose 의 단순 dropdown 또는 `NumberPicker` 등 — 디자인 시스템 일관성을 위해 0~23 range 를 가지는 dropdown 이 1차 안. (대안: TimePickerDialog wrapper. UI complexity 와 dark theme 호환을 우려해 dropdown 으로 시작.)
- 기존 `SettingsOperationalSummaryBuilder.formatHour(hour)` 헬퍼 (`"%02d:00"`) 재사용해 dropdown label 표시.

**Tech Stack:** Kotlin, DataStore, Jetpack Compose Material3 (`ExposedDropdownMenuBox` 또는 동등 picker), JUnit unit tests.

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `docs/journeys/quiet-hours.md` Known gaps 의 "신규 blocker (c) — Settings 화면이 `quietHoursStartHour` / `quietHoursEndHour` 를 사용자가 조정할 수 있는 editor 를 노출하지 않음 (토글만 존재; `OperationalSummaryCard` 콜백은 `onQuietHoursEnabledChange` 단일)" 을 해소.
- **결정 필요 (implementer 가 PR 본문에 명기)**: picker UI 형태 — 1차 안은 dropdown (24개 hour entry, `00:00`~`23:00` label). TimePickerDialog 를 쓰면 분 단위 조정 환상이 생기지만 모델은 hour 단위만 지원하므로 일관성 측면에서 dropdown 이 정직. 더 손쉬운 회전 picker 가 디자인적으로 낫다고 판단되면 `NumberPicker` 또는 자체 wheel composable 도 가능.
- **결정 필요**: editor 가 `quietHoursEnabled = false` 일 때도 노출돼야 하는가 — 본 plan 1차 안은 "Switch 가 ON 일 때만 picker 노출". 이유: OFF 상태에서 시간만 만지작거리는 것은 효과가 없어 사용자에게 false-affordance 가 된다. 단점: ON 으로 바꾼 직후 picker 가 갑자기 등장해 layout 점프. 대안 — 항상 노출하되 OFF 일 땐 picker 를 disabled 회색 처리. PR 본문에 채택 안과 사유 기록.
- **결정 필요**: 시작/종료 시간이 같을 때 (예: 12, 12) 의 의미 — 현재 `QuietHoursPolicy.isQuietAt` 의 same-day 분기는 `hourOfDay in startHour until endHour` 라 빈 range, overnight 분기는 `>= startHour || < endHour` 라 모든 시각. 즉 동일값 → quiet hours 가 사실상 24시간 또는 0시간으로 의미가 모호해진다. UI 가 같은 값을 허용하는지 / 방어할지 결정 필요. 1차 안: 같은 값을 허용하되 `OperationalSummaryRow` 의 `detail` 라인 ("자동 완화 사용 중") 옆에 작은 경고 카피 ("시작·종료가 같으면 적용 시각이 모호해요") 를 inline 으로 보강. 또는 picker 자체에서 동일값을 disable. 본 plan 은 "허용 + 경고 카피" 를 1차 안으로 두고 implementer 가 사용자 검토 후 조정.
- **결정 필요**: `formatHour` 표기 — 현재 `"%02d:00"` 형식 (`"23:00"`). 한국어 카피와 잘 어울리지만 picker dropdown label 도 동일 형식을 쓰는지, 별도 한국식 ("오후 11시") 으로 갈지 — 1차 안은 일관성을 위해 `"23:00"` 유지. 카피 전반 한국화는 별도 plan.
- **scope 외**: `digestHours` (`listOf(12, 18, 21)`) editor 는 본 plan 에 포함하지 않는다. quiet-hours window 만으로 verification blocker 를 풀고, digest 시간 편집은 별도 후속 plan (Known gap 으로 따로 트래킹).
- **scope 외**: classifier / `QuietHoursPolicy` 자체 변경 없음. 본 plan 은 view + repository setter 만.

---

## Task 1: Failing tests for `SettingsRepository` quiet-hours hour setters [IN PROGRESS via PR #346]

**Objective:** `setQuietHoursStartHour(hour)` / `setQuietHoursEndHour(hour)` 가 DataStore 에 영속되고 `observeSettings()` 가 반영하는지 단위 테스트로 고정.

**Files:**
- 보강 (또는 신규) `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryTest.kt` — 유사한 setter 테스트가 이미 있는지 확인하고 없으면 신규.

**Steps:**
1. `SettingsRepository.getInstance(context)` 로 fixture 인스턴스를 얻고, `setQuietHoursStartHour(2)` 호출 후 `observeSettings().first().quietHoursStartHour == 2` assertion.
2. 같은 패턴으로 `setQuietHoursEndHour(5)` → `quietHoursEndHour == 5` assertion.
3. 경계값: hour=0, hour=23 → 정상 저장. hour=-1 / hour=24 는 본 plan 에서는 호출 측 (UI dropdown) 이 0~23 만 emit 한다고 가정. repository 단계에서 추가 validation 은 하지 않는다 — invalid 입력의 격리는 picker 의 책임. (필요 시 별도 task 로 분리.)
4. `clearInstanceForTest()` + `clearAllForTest()` cleanup.
5. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.settings.SettingsRepositoryTest"` → 추가한 케이스 모두 RED (setter 부재).

## Task 2: Implement `SettingsRepository` setters [IN PROGRESS via PR #346]

**Objective:** Task 1 테스트 GREEN.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt`

**Steps:**
1. 기존 `setQuietHoursEnabled` 옆에 두 suspend 함수 추가:
   ```
   suspend fun setQuietHoursStartHour(hour: Int) { ... edit { prefs[QUIET_HOURS_START_HOUR] = hour } }
   suspend fun setQuietHoursEndHour(hour: Int) { ... }
   ```
2. 키 `QUIET_HOURS_START_HOUR` / `QUIET_HOURS_END_HOUR` 는 이미 companion object 에 정의돼 있으므로 신규 키는 없음.
3. 단위 테스트 재실행 → GREEN.

## Task 3: Failing UI test (or builder test) for picker visibility & callbacks [IN PROGRESS via PR #346]

**Objective:** `OperationalSummaryCard` 가 새 콜백을 받고 picker 가 Switch ON 일 때만 노출되는지 (또는 채택한 alternative — disabled 회색 처리) 를 테스트로 고정.

**Files:**
- 보강 또는 신규 `app/src/test/java/com/smartnoti/app/ui/screens/settings/OperationalSummaryCardTest.kt` — Compose UI test 가 이 모듈에서 가능한지 먼저 확인. 가능하지 않으면 picker 의 visibility 결정 자체를 pure function (`OperationalSummaryQuietHoursPickerSpec` 같은 작은 data class + builder) 으로 분리하고 그 builder 를 단위 테스트.

**Steps:**
1. Switch ON + start=23, end=7 → picker spec 의 visible=true, startHour=23, endHour=7.
2. Switch OFF → 채택 안에 따라 visible=false 또는 enabled=false.
3. 사용자가 startHour 를 14 로 바꾸면 callback 이 14 와 함께 호출됨 (콜백 invocation assertion).
4. `./gradlew :app:testDebugUnitTest` 신규 테스트 RED.

## Task 4: Wire pickers into `OperationalSummaryCard` [IN PROGRESS via PR #346]

**Objective:** Task 3 테스트 GREEN. 사용자가 Settings → 운영 상태 → Quiet Hours row 에서 picker 두 개로 시작/종료 시간을 변경할 수 있다.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` — `OperationalSummaryCard` 콜백 시그니처 확장 + picker 컴포저블 렌더, call site 에서 `repository.setQuietHoursStartHour(...)` / `repository.setQuietHoursEndHour(...)` 를 `scope.launch` 로 호출.
- 필요 시 `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsOperationalSummaryBuilder.kt` 에 hour list (`(0..23).toList()`) 또는 picker spec 헬퍼 추가.

**Steps:**
1. `OperationalSummaryCard(summary, onQuietHoursEnabledChange, onQuietHoursStartHourChange, onQuietHoursEndHourChange)` 시그니처로 확장.
2. `OperationalSummaryRow("Quiet Hours", ...)` 아래에 두 dropdown row (시작 / 종료) 를 conditional 로 렌더 — Switch checked 상태에 따라 visible/enabled 결정 (Product intent 의 채택 안 따름).
3. dropdown label 은 `"%02d:00".format(hour)`. selection 시 콜백 호출.
4. Settings ScreenHeader 아래 lazy column 의 `OperationalSummaryCard(...)` call site 에 두 개 람다 추가:
   ```
   onQuietHoursStartHourChange = { hour -> scope.launch { repository.setQuietHoursStartHour(hour) } },
   onQuietHoursEndHourChange = { hour -> scope.launch { repository.setQuietHoursEndHour(hour) } },
   ```
5. 채택한 동일값 처리 안 (경고 inline 카피 / picker disable) 도 함께 구현.
6. 신규 picker spec 단위 테스트 GREEN.
7. 전체 테스트 (`./gradlew :app:testDebugUnitTest`) 가 PASS — 기존 `SettingsScreen` 또는 navigation 경유 테스트가 있으면 콜백 시그니처 변경으로 깨지지 않는지 확인.

## Task 5: ADB / 라이브 검증 [IN PROGRESS via PR #346]

**Objective:** 사용자 관측 동작이 실제로 변경되는지 확인. 동시에 `quiet-hours.md` verification recipe 의 "Settings 에서 시작/종료 시간을 현재 시각을 포함하도록 조정" 단계가 build 에서 수행 가능해졌음을 입증.

**Steps:**
```bash
# 1. emulator 에서 빌드 설치 후 Settings 탭 → 운영 상태 카드 → Quiet Hours row.
# 2. Switch 가 ON 인 상태에서 시작 dropdown / 종료 dropdown 두 개가 보이는지 확인.
# 3. 현재 시각 (예: 12) 을 포함하도록 시작=11, 종료=13 으로 변경.
# 4. DataStore 가 영속됐는지 간접 확인 — 앱을 force-stop 후 재진입했을 때 동일 값 유지.
adb shell am force-stop com.smartnoti.app
adb shell am start -n com.smartnoti.app/.MainActivity
# 5. (옵션) `DebugInjectNotificationReceiver` 로 com.coupang.mobile 알림을 게시 → status=DIGEST + reasonTags 에 "조용한 시간" 포함 확인.
adb -s emulator-5554 shell am broadcast \
  -a com.smartnoti.debug.INJECT_NOTIFICATION \
  -p com.smartnoti.app \
  --es title "QuietHrsLive$(date +%s)" \
  --es body "쇼핑 알림 (in-window)" \
  --es package_name com.coupang.mobile \
  --es app_name "Coupang"
adb -s emulator-5554 exec-out run-as com.smartnoti.app sqlite3 \
  databases/smartnoti.db \
  "SELECT status,reasonTags FROM notifications WHERE title LIKE 'QuietHrsLive%' ORDER BY postedAtMillis DESC LIMIT 1;"
```

## Task 6: Update journey doc + plan frontmatter [IN PROGRESS via PR #346]

**Files:**
- `docs/journeys/quiet-hours.md` — Known gaps 의 신규 blocker (c) 를 `(resolved YYYY-MM-DD, plan ...)` 로 표기. Verification recipe 의 step 1 카피 ("Settings 에서 시작/종료 시간을 현재 시각을 포함하도록 조정") 가 이제 빌드에서 가능함을 Change log 에 기록 + `last-verified` 갱신 (verification 을 실제로 한 날짜).
- `docs/plans/2026-04-26-settings-quiet-hours-window-editor.md` — 본 plan 의 frontmatter 를 `status: shipped` + `shipped: <date>` + `superseded-by: ../journeys/quiet-hours.md` 로 flip.

**Steps:**
1. journey 의 Known gaps 항목 업데이트 (text 그대로 유지하고 prefix 만 추가).
2. Change log 에 `<date>: Plan 2026-04-26-settings-quiet-hours-window-editor ship — Settings 화면에 quietHoursStartHour / quietHoursEndHour picker 두 개 추가, blocker (c) 해소.` 한 줄 추가.
3. plan frontmatter flip.

## Task 7: Self-review + PR [IN PROGRESS via PR #346]

- 모든 단위 테스트 PASS.
- ADB 검증 결과 (Settings UI 스크린샷 + DataStore 재진입 후 값 유지 + 옵션 step 5 의 DB row) PR 본문 첨부.
- PR 제목: `feat(settings): add quiet-hours start/end hour pickers in operational summary`.

---

## Scope

**In:**
- `SettingsRepository` setter 2개.
- `SettingsScreen.OperationalSummaryCard` 의 picker UI (dropdown 형태 1차 안).
- `quiet-hours.md` Known gap (c) resolution annotation + Change log.

**Out:**
- `digestHours` editor (별도 후속).
- `quietHoursEnabled` 토글 동작 변경 (이미 ship 됨).
- `QuietHoursPolicy` 시간 분기 로직 변경.
- 다국어 / 카피 톤 전반 정리.
- TimePickerDialog 같은 분 단위 picker (모델이 hour 단위라 의도적 제외).

---

## Risks / open questions

- **picker UI 선택**: 채택 안 (dropdown vs NumberPicker vs custom wheel) 은 디자인 톤 기준이라 implementer 1차 결정 후 PR 본문에 사유 기록. Reviewer 가 TimePickerDialog 가 더 직관적이라고 보면 후속 PR 로 교체.
- **start == end 의미 모호성**: 본 plan 의 1차 안은 "허용 + 경고 inline 카피". 좀 더 strict 하게 같은 값을 picker 자체에서 disable 하는 안도 가능. UX 검토 필요.
- **OFF 상태 picker 가시성**: "Switch ON 일 때만 노출" 1차 안 vs "항상 노출 + disabled". 어느 쪽이 사용자 학습성에 더 좋은지 implementer 가 판단.
- **하위 호환성**: 기존 사용자가 default 23/7 로 운영 중이며, 이 PR ship 후에도 default 는 유지된다 — 새 setter 는 explicit 호출이 있을 때만 DataStore 키를 쓴다. 기존 row 영향 없음.
- **picker 와 system clock**: picker 는 system 시각을 직접 조회하지 않는다. 현재 시각 표시 / "지금이 quiet hours 안인가" 표시는 기존 `OperationalSummaryRow.detail` ("자동 완화 사용 중" / "꺼짐") 만 의존. picker 옆에 "지금은 X시" 같은 contextual hint 를 붙일지 여부는 별도 plan.
- **journey-tester 의 verification 재실행**: 본 plan ship 후 journey-tester 가 실제 in-window DIGEST 분기를 ADB 로 재현해 `last-verified` 를 갱신하는 별도 sweep 이 한 번 필요. plan-implementer 가 Task 5 에서 일부 검증을 수행하지만, journey 문서의 `last-verified` 는 recipe 전체를 다시 돌린 뒤 갱신.

---

## Related journey

- [`docs/journeys/quiet-hours.md`](../journeys/quiet-hours.md) — 본 plan 이 Known gaps 의 신규 blocker (c) 를 해소. ship 후 해당 bullet 은 `(resolved <date>, plan 2026-04-26-settings-quiet-hours-window-editor)` 로 prefix 표기되며, Change log 에 한 줄 추가됨.
