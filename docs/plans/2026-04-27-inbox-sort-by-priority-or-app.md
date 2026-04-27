---
status: planned
---

# Inbox sort selector — 시간순 / 중요도순 / 앱별

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 정리함 통합 탭 (`InboxScreen`) 의 세 서브탭 (`Digest / 보관 중 / 처리됨`) 모두에서 사용자가 정렬 모드를 명시적으로 고를 수 있다 — `최신순` (기본, 현재 동작) / `중요도순` (PRIORITY > DIGEST > SILENT, 같은 status 안에서는 시간 desc) / `앱별 묶기` (현재 packageName grouping 의 명시화 + 앱명 알파벳 정렬). 사용자가 선택한 정렬은 `SmartNotiSettings.inboxSortMode` 에 영속되며 다음 진입 시 그대로 복원. 본 plan 은 *DB query 레벨 변경 없이* 기존 `observeAllFiltered` / `observeDigestGroupsFiltered` 의 결과를 in-memory 변환하는 pure helper (`InboxSortPlanner`) 한 곳을 추가하고, 화면은 신규 `InboxSortDropdown` 컴포넌트만 wiring 한다. PRIORITY 알림은 정리함에 직접 노출되지 않으므로 (priority-inbox 별 화면) `중요도순` 모드는 DIGEST > SILENT 두 단계 정렬 + status 외 옵션 (Digest 안에서 PRIORITY-가까운 우선순위 reasonTags 로 추가 가중치) 은 본 plan scope 밖.

**Architecture:**
- (a) `SmartNotiSettings` 에 `inboxSortMode: String = InboxSortMode.RECENT.name` 추가. enum `InboxSortMode { RECENT, IMPORTANCE, BY_APP }` 신규.
- (b) Pure helper `InboxSortPlanner` (`domain/usecase/`) — 두 메서드:
  - `fun sortFlatRows(notifications: List<NotificationUiModel>, mode: InboxSortMode): List<NotificationUiModel>` — `RECENT` 면 `sortedByDescending { postedAtMillis }`, `IMPORTANCE` 면 `sortedWith(compareBy({ statusOrder(it) }, { -it.postedAtMillis }))` (PRIORITY=0, DIGEST=1, SILENT=2 의미), `BY_APP` 면 `sortedWith(compareBy({ it.appName.lowercase() }, { -it.postedAtMillis }))`.
  - `fun sortGroups(groups: List<DigestGroupUiModel>, mode: InboxSortMode): List<DigestGroupUiModel>` — 그룹 자체의 정렬 순서를 모드별로 결정. `RECENT` → 그룹의 `items.maxOf { postedAtMillis }` desc, `IMPORTANCE` → 그룹의 status (모든 row 동일 status 가정 — Digest/Hidden 그룹은 단일 status) 후 most-recent desc, `BY_APP` → `appName.lowercase()` asc.
- (c) `InboxScreen` 이 위 두 메서드를 collect 후 적용 — `digestGroups` 는 `sortGroups` 통과, `archivedGroups` / `processedGroups` (현재 `HiddenNotificationsScreen` 내부에서 계산) 는 모드를 prop 으로 흘려 받아 동일 helper 적용.
- (d) `InboxSortDropdown` 신규 컴포넌트 — `ScreenHeader` 와 `InboxTabRow` 사이에 작은 `Row` 로 노출 ("정렬:" + 현재 모드 라벨 + arrow icon). 탭 시 `DropdownMenu` 또는 `BottomSheet` 으로 3 옵션 표시 (`최신순 / 중요도순 / 앱별 묶기`). 선택 시 `SettingsRepository.setInboxSortMode(mode)` 호출.
- (e) `HiddenNotificationsScreen(mode = HiddenScreenMode.Embedded)` 호출부에 `sortMode: InboxSortMode` 파라미터 추가 — 화면 자체는 helper 호출 위치만 변경, 그룹 정렬 로직은 helper 가 책임.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`DropdownMenu`, `Row`, `IconButton`), Gradle JVM unit tests (pure helper).

---

## Product intent / assumptions

- 본 plan 은 다음을 plan 단계에서 고정한다 (implementer 추측 금지):
  - **세 서브탭 공통 정렬**: 단일 `inboxSortMode` 가 세 서브탭 모두에 동일 모드를 적용. 서브탭별 정렬을 분리하면 사용자 모델이 복잡해지고 settings field 가 3 배로 늘어나 default 값 관리 부담. 사용자가 "Digest 만 앱별, Hidden 은 시간순" 을 원하면 별도 plan.
  - **정렬은 in-memory only**: DAO query 의 `ORDER BY` 절은 변경하지 않음. 기존 `observeAll()` 이 이미 `postedAtMillis DESC` 로 정렬된 결과를 반환하므로 helper 가 추가 정렬을 적용. DB index 변경 / migration 무 — 본 plan 은 row 수 ~ 수백 단위까지 in-memory 정렬이 충분히 빠르다는 가정. 향후 row 수 폭증 시 별도 plan 으로 DB 단으로 이주.
  - **그룹 모드의 의미**:
    - `RECENT` (default) — 현재 동작. 그룹 정렬 순서는 그룹 안 가장 최근 row 의 시각 desc.
    - `IMPORTANCE` — Digest 서브탭 안에서는 모든 그룹이 status=DIGEST 이므로 사실상 RECENT 와 동일. Hidden 의 보관 중 / 처리됨 서브탭도 마찬가지. *cross-status 차이는 priority-inbox / Detail 같은 다른 화면에서만 의미가 있다.* 본 plan 은 IMPORTANCE 모드를 ship 하되 단일 서브탭 안에서는 RECENT 와 동일한 결과가 나온다는 한계를 README 에 명시.
    - `BY_APP` — 그룹 자체는 이미 packageName 으로 묶이므로 사실상 그룹 헤더 정렬 (`appName.lowercase()` asc) 만 영향. Digest / Hidden 의 묶음 카드 순서가 가나다순으로 바뀐다.
  - **PRIORITY 무관**: 정리함은 PRIORITY 알림을 노출하지 않으므로 (priority-inbox 별 화면이 담당) `IMPORTANCE` 모드의 PRIORITY > DIGEST > SILENT 우선순위는 정리함 안에서는 발현되지 않는다.
- **남는 open question (user 결정 필요, 본 plan 은 default 안 채택 후 PR 본문에서 confirm)**:
  - **`IMPORTANCE` 모드의 정리함 내 의미**: 위 설명대로 단일 서브탭 안에서는 RECENT 와 동일. 사용자가 "정리함 안에서도 status 의 의미 차이를 보고 싶다" 면, 세 서브탭을 하나로 합쳐 (현재의 sub-tab 분리 자체를 제거) cross-status mixed feed + IMPORTANCE 정렬을 적용해야 함. 그건 본 plan 의 architecture 변경이라 별도 plan 후속 — PR 본문에서 user 가 결정.
  - **`BY_APP` 모드의 정렬 키**: `appName.lowercase()` 가 한글 / 영어 혼합에서 어떻게 정렬되는지 (한글 자모 ↔ ASCII 비교) 의 사용자 expectation 을 PR 본문에서 confirm. 본 plan 은 `Locale.KOREAN` 명시 없이 default `Comparator.naturalOrder()` 사용.
  - **Dropdown 위치**: ScreenHeader 와 TabRow 사이가 default. "TabRow 옆 trailing icon 으로 통합" 안도 가능하지만 실 estate 가 좁아 dropdown 의 텍스트 라벨이 잘림. 본 plan 은 별도 Row.

---

## Task 1: Add `InboxSortMode` enum + `InboxSortPlanner` + failing tests [IN PROGRESS via PR #416]

**Objective:** 두 정렬 메서드의 계약을 JUnit 으로 고정.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/model/InboxSortMode.kt`
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/InboxSortPlanner.kt`
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/InboxSortPlannerTest.kt`

**Steps:**
1. enum:
   ```kotlin
   enum class InboxSortMode { RECENT, IMPORTANCE, BY_APP }
   ```
2. Helper:
   ```kotlin
   class InboxSortPlanner {
       fun sortFlatRows(rows: List<NotificationUiModel>, mode: InboxSortMode): List<NotificationUiModel>
       fun sortGroups(groups: List<DigestGroupUiModel>, mode: InboxSortMode): List<DigestGroupUiModel>
   }
   ```
3. 케이스 (`sortFlatRows`):
   - `RECENT` happy: 3건 (postedAtMillis 1, 3, 2) → desc (3, 2, 1)
   - `IMPORTANCE` happy: PRIORITY (t=1) + DIGEST (t=3) + SILENT (t=2) + DIGEST (t=4) → PRIORITY 먼저, 그 다음 DIGEST 두 건 (t=4, 3 desc), 마지막 SILENT
   - `BY_APP` happy: appName "Slack" / "Gmail" / "Coupang" + 같은 앱 안 두 건 → "Coupang" 먼저, "Gmail" 다음, "Slack" 마지막. 같은 앱 안 두 건은 시간 desc.
   - `RECENT` empty: 빈 리스트 → 빈 리스트
4. 케이스 (`sortGroups`):
   - `RECENT` happy: 그룹 A (max=10), B (max=5), C (max=8) → A, C, B
   - `BY_APP` happy: 그룹 A.appName="Slack", B.appName="Coupang", C.appName="Gmail" → C 가 아닌 B, C, A 순 (lowercase 기준)
   - `IMPORTANCE` 단일 status: 모든 그룹 DIGEST → RECENT 와 동일 결과
5. `./gradlew :app:testDebugUnitTest --tests "*.InboxSortPlannerTest"` → GREEN.

## Task 2: Add `inboxSortMode` to settings model + DataStore migration [IN PROGRESS via PR #416]

**Objective:** 사용자 선택을 영속화.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt`
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt`

**Steps:**
1. `SmartNotiSettings` 에 `val inboxSortMode: String = InboxSortMode.RECENT.name` 추가.
2. `SettingsRepository`:
   - DataStore Preferences key 추가 (`stringPreferencesKey("inbox_sort_mode")`)
   - `observeSettings()` mapper 에 `inboxSortMode = preferences[key] ?: InboxSortMode.RECENT.name` 추가
   - 신규 suspend 메서드 `setInboxSortMode(mode: InboxSortMode)` — DataStore edit 으로 `mode.name` 저장
   - `applyPendingMigrations()` 에 `if (key not present) put(InboxSortMode.RECENT.name)` 분기 (기존 boolean 토글 패턴 따라)
3. `./gradlew :app:assembleDebug` 빌드 통과.

## Task 3: Wire `InboxSortDropdown` + helper into `InboxScreen` + `HiddenNotificationsScreen`

**Objective:** UI 가 정렬 모드를 노출 + 선택 시 helper 가 적용.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxScreen.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxSortDropdown.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/hidden/HiddenNotificationsScreen.kt` (Embedded 모드의 그룹 정렬 helper 적용)
- `app/src/main/java/com/smartnoti/app/ui/screens/digest/DigestScreen.kt` (그룹 list 의 helper 적용)

**Steps:**
1. `InboxScreen`:
   - `val sortMode = InboxSortMode.valueOf(settings.inboxSortMode)` 계산
   - `ScreenHeader` 와 `InboxTabRow` 사이에 `InboxSortDropdown(currentMode = sortMode, onSelect = { scope.launch { settingsRepository.setInboxSortMode(it) } })` 호출
   - `digestGroups = sortPlanner.sortGroups(digestGroupsRaw, sortMode)` 변환 적용
   - `HiddenNotificationsScreen(mode = HiddenScreenMode.Embedded(...), sortMode = sortMode)` 로 mode 흘림
2. `InboxSortDropdown`:
   ```kotlin
   @Composable
   fun InboxSortDropdown(
       currentMode: InboxSortMode,
       onSelect: (InboxSortMode) -> Unit,
       modifier: Modifier = Modifier,
   )
   ```
   - `Row` 안에 "정렬:" `Text` + 현재 모드의 라벨 (`labelFor(currentMode)`) + chevron icon
   - 탭 시 `DropdownMenu` 노출, 3 `DropdownMenuItem`. 선택 시 onSelect callback + dismiss.
   - `labelFor` pure helper — `RECENT → "최신순"`, `IMPORTANCE → "중요도순"`, `BY_APP → "앱별 묶기"`.
3. `HiddenNotificationsScreen`:
   - 시그니처에 `sortMode: InboxSortMode = InboxSortMode.RECENT` 추가 (Standalone deeplink 호환 위해 default RECENT)
   - 그룹 정렬 호출 site 에 `sortPlanner.sortGroups(...)` 적용
4. `DigestScreen` (legacy `Routes.Digest` deeplink 호환):
   - `inboxSortMode` 를 `SmartNotiSettings` 에서 collect → 그룹 정렬 적용. 단, 본 화면은 standalone 모드일 때도 동일 정렬 적용. (helper 통합으로 코드 중복 제거.)
5. `./gradlew :app:assembleDebug` 빌드 통과 + `inbox` / `hidden` / `digest` 패키지의 기존 테스트가 여전히 GREEN.

## Task 4: Add wiring contract test

**Objective:** Future PR 에서 정렬 helper 호출이 누락되지 않도록 가드.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/inbox/InboxSortDropdownLabelTest.kt` — `labelFor(mode)` pure helper 의 3 모드 라벨 매핑 검증.
- 또는 (compose UI test 가능 환경) `InboxSortDropdownContractTest` — `currentMode = IMPORTANCE` 인 dropdown 이 `중요도순` 라벨을 표시 + 탭 시 `DropdownMenu` 가 3 옵션 노출.

**Steps:**
1. CI 가 Compose UI test 미지원이면 (선례 — `2026-04-25-android-queries-package-visibility` 가 Option B) pure helper `labelFor` 만 테스트.
2. `./gradlew :app:testDebugUnitTest --tests "*InboxSortDropdownLabelTest*"` GREEN.

## Task 5: Update journey docs

**Files:**
- `docs/journeys/inbox-unified.md`
- `docs/journeys/digest-inbox.md` (deprecated, deeplink 호환 차원 갱신)
- `docs/journeys/hidden-inbox.md` (deprecated, 동일)

**Steps:**
1. `inbox-unified.md`:
   - Observable steps 2 와 3 사이에 sub-step 추가 — `InboxSortDropdown` 이 `ScreenHeader` 아래 노출되며 사용자가 3 모드 (`최신순 / 중요도순 / 앱별 묶기`) 중 선택 가능. 선택 시 `SmartNotiSettings.inboxSortMode` 갱신, 다음 진입 시 복원.
   - Code pointers 에 `InboxSortDropdown`, `InboxSortPlanner`, `domain/model/InboxSortMode`, `SmartNotiSettings#inboxSortMode` 추가.
   - Out of scope 에 명시 — 단일 서브탭 안에서는 IMPORTANCE 와 RECENT 결과가 동일 (모든 row 가 같은 status), cross-status mixed feed 는 별도 plan.
   - Change log row 1줄 추가 (구현 PR 머지 시 commit 해시 채움).
2. `digest-inbox.md` / `hidden-inbox.md` (deprecated):
   - Change log row 1줄 추가 — Routes.Digest / Routes.Hidden deeplink 진입 경로도 동일 helper 를 공유하므로 정렬 모드가 적용된다 (settings 영속).

## Task 6: ADB end-to-end verification

**Objective:** 실기기 emulator-5554 에서 세 모드의 동작 확인 + 영속성 회귀 가드.

**Steps:**
```bash
# 사전: emulator-5554, debug APK, listener 권한 grant.

# 1. DIGEST 알림 3건 + SILENT 2건 seed (다양한 packageName)
for app in Coupang Slack Gmail; do
  adb -s emulator-5554 shell am broadcast \
    -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver \
    -a com.smartnoti.debug.INJECT_NOTIFICATION \
    --es title "${app}_dig" --es body "digest seed" \
    --es force_status DIGEST
  sleep 1
done
for app in Notion Linear; do
  adb -s emulator-5554 shell am broadcast \
    -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver \
    -a com.smartnoti.debug.INJECT_NOTIFICATION \
    --es title "${app}_sil" --es body "silent seed" \
    --es force_status SILENT
  sleep 1
done

# 2. 앱 진입 → 정리함 탭 → Digest 서브탭 default
adb -s emulator-5554 shell am force-stop com.smartnoti.app
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity

# 3. ScreenHeader 아래 "정렬: 최신순" dropdown 노출 확인
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s emulator-5554 exec-out cat /sdcard/ui.xml | grep -oE '정렬:|최신순|중요도순|앱별 묶기'
# expected: "정렬:" + "최신순" hit

# 4. 탭 → DropdownMenu → "앱별 묶기" 선택 → Digest 그룹 카드 순서가 알파벳 (Coupang, Gmail, Slack) 으로 변경 확인
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s emulator-5554 exec-out cat /sdcard/ui.xml | grep -oE 'Coupang|Gmail|Slack'
# expected: 위에서 아래로 Coupang, Gmail, Slack 순

# 5. 보관 중 서브탭 진입 → 같은 정렬 모드 적용 확인 (Linear, Notion 알파벳 순)

# 6. 앱 force-stop + 재진입 → "앱별 묶기" 가 그대로 복원되는지 (settings 영속) 확인
adb -s emulator-5554 shell am force-stop com.smartnoti.app
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s emulator-5554 exec-out cat /sdcard/ui.xml | grep -oE '앱별 묶기'
# expected: hit (정렬 모드 복원됨)

# 7. "최신순" 으로 회복 → Digest 그룹 카드 순서가 가장 최근 시각 desc 로 변경 확인.

# 8. baseline 복원 — sqlite3 DELETE 또는 디버그 reset.
```

---

## Scope

**In:**
- 신규 enum `InboxSortMode { RECENT, IMPORTANCE, BY_APP }`.
- Pure helper `InboxSortPlanner` (두 메서드 + 7+ 케이스 단위 테스트).
- `SmartNotiSettings.inboxSortMode` + DataStore migration.
- `InboxSortDropdown` 컴포넌트 + `labelFor` pure helper.
- `InboxScreen` / `HiddenNotificationsScreen` (Embedded) / `DigestScreen` 의 helper wiring.
- 가드 테스트 (`labelFor` 매핑 또는 dropdown contract).
- `inbox-unified.md` Observable step / Code pointers / Out of scope / Change log 갱신.
- `digest-inbox.md` / `hidden-inbox.md` (deprecated) Change log 갱신.
- ADB 검증 recipe.

**Out:**
- DAO query 의 `ORDER BY` 변경 / DB index 추가 — in-memory 정렬로 시작.
- 서브탭별 다른 정렬 모드 (Digest 만 BY_APP, Hidden 은 RECENT) — 단일 sortMode 로 시작.
- Cross-status mixed feed (세 서브탭 통합 + IMPORTANCE 의 PRIORITY > DIGEST > SILENT 발현) — 별도 plan.
- `priority-inbox.md` 화면의 정렬 — PRIORITY 는 그 자체로 단일 status 이므로 본 plan 적용 안 함.
- `Locale.KOREAN` 정밀 정렬 — default Comparator.naturalOrder() 로 시작.
- 사용자 임의 정렬 (drag-and-drop reorder) — 별도 plan.
- 정렬 변경 시 애니메이션 — 단순 list update 만.

---

## Risks / open questions

- **`IMPORTANCE` 모드의 정리함 내 가시적 효과 부재** — 모든 서브탭이 단일 status 이므로 IMPORTANCE 와 RECENT 가 동일 결과. 사용자가 "중요도순을 골랐는데 아무 변화가 없네" 로 오해할 수 있음. Mitigation: 라벨 옆에 작은 helper text "(현재 정리함 안에서는 최신순과 동일)" 또는 모드 자체를 정리함 dropdown 에서 숨김 — PR 본문에서 user 가 결정. **본 plan 은 default = 모든 모드 노출 + helper text 없음**.
- **In-memory 정렬 비용** — row 수 ~ 수백 단위까지는 negligible. 사용자가 수만 건의 보관 중 row 를 가지면 정렬이 frame drop 을 일으킬 수 있음. 향후 DB 단으로 이주 별도 plan.
- **`appName.lowercase()` 한글 vs 영어 혼합** — 한글 (`'카'` → `'카'`) 과 영어 (`'C'` → `'c'`) 가 ASCII 단위로 비교되면 영어가 먼저, 한글이 뒤로 정렬됨. 사용자 expectation 이 다르면 (한글 먼저 / Locale 기반) Risks 항목으로 PR 본문에서 confirm.
- **Settings UI 토글 vs Inbox 안 dropdown** — dropdown 위치가 ScreenHeader 와 TabRow 사이가 적절한지 PR 본문에서 confirm. Settings 의 별도 메뉴로 이주하는 안도 가능하지만 사용자가 "정렬을 자주 바꿈" 가정 시 Inbox 안에 두는 것이 마찰 ↓.
- **`HiddenNotificationsScreen` 의 Standalone 진입 호환** — `mode = Standalone` (deeplink) 호출 site 에서 sortMode 파라미터 default RECENT 로 fallback. Standalone 도 settings 의 inboxSortMode 를 collect 하도록 확장 가능 — 본 plan 은 Standalone fallback default 만, Standalone 에서도 settings collect 는 별도 plan.

---

## Related journey

- [`docs/journeys/inbox-unified.md`](../journeys/inbox-unified.md) — Observable step / Code pointers / Out of scope 갱신.
- [`docs/journeys/digest-inbox.md`](../journeys/digest-inbox.md) (deprecated) — Routes.Digest deeplink 의 정렬 적용.
- [`docs/journeys/hidden-inbox.md`](../journeys/hidden-inbox.md) (deprecated) — Routes.Hidden deeplink 의 정렬 적용.
- [`docs/journeys/priority-inbox.md`](../journeys/priority-inbox.md) — PRIORITY 화면은 본 plan 영향 무 (단일 status).
