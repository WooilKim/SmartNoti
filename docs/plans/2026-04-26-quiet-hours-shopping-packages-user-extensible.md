---
status: planned
---

# Quiet hours 분기 대상 앱을 사용자가 직접 추가/제거할 수 있게 만드는 Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 Settings 안에서 "조용한 시간 동안 자동으로 모아둘 앱" 목록을 직접 추가/제거할 수 있게 한다. 현재 quiet-hours 분기는 classifier 생성자에 `setOf("com.coupang.mobile")` 단일 값으로 하드코딩돼 있어, 사용자가 자기 환경의 다른 쇼핑/배달/쿠폰 앱 (배민, 야놀자, 11번가, AliExpress 등) 을 quiet-hours 자동 DIGEST 대상에 넣을 수 없다. 결과적으로 Settings 의 "조용한 시간" 섹션은 켜고 시각만 바꿀 수 있을 뿐 **누구를 대상으로 할지** 는 빌드 타임에 고정돼 있어, default 쇼핑앱이 설치돼 있지 않은 사용자에게는 quiet-hours 기능 자체가 발화하지 않는 사실상의 dead-feature. 본 plan ship 후에는 사용자가 Settings 의 새 "조용한 시간 대상 앱" 섹션에서 앱을 자유롭게 추가/제거할 수 있고, 그 set 이 classifier 의 quiet-hours 분기에 그대로 주입된다.

**Architecture:**
- `SmartNotiSettings` 에 신규 필드 `quietHoursPackages: Set<String> = setOf("com.coupang.mobile")` 추가 — default 가 기존 하드코딩 값과 동일하므로 기존 사용자의 동작은 변하지 않는다 (행동 호환).
- `SettingsRepository` 에 신규 DataStore 키 `QUIET_HOURS_PACKAGES` (stringSet) + read 매핑 + `setQuietHoursPackages(Set<String>)` / `addQuietHoursPackage(String)` / `removeQuietHoursPackage(String)` suspend setter 3종 추가. 기존 `suppressedSourceApps` 의 setter pattern (`SettingsRepository.kt` 의 `setSuppressedSourceApps`) 을 모방.
- `SmartNotiNotificationListenerService` 의 `processor by lazy { NotificationCaptureProcessor(classifier = NotificationClassifier(... shoppingPackages = setOf("com.coupang.mobile") ...)) }` 단일 wiring 을 동적으로 변경. 두 후보:
  - **(A) classifier 재생성**: `processNotification` 호출 시 `settingsRepository.observeSettings().first()` 로 최신 `quietHoursPackages` 를 읽어 classifier 를 재생성 (기존 `categoriesRepository.currentCategories()` injection 패턴, plan `categories-runtime-wiring-fix` 와 동일).
  - **(B) classifier 시그니처 변경**: `NotificationClassifier.classify(input, rules, shoppingPackagesOverride)` 처럼 매번 set 을 넘기는 형태.
  - implementer 가 `NotificationProcessingCoordinator` (categories-runtime-wiring-fix Task 4 / #245 에서 도입된 단일 call site) 를 보고 둘 중 일관된 안 채택. 본 plan 은 default 권고로 (A) 를 선호 (다른 dynamic input 들이 이미 그 패턴이므로).
- `OnboardingQuickStartSettingsApplier` 의 `DEFAULT_SHOPPING_PACKAGES = setOf("com.coupang.mobile")` 도 새 settings 필드의 default 와 동일 값을 사용. 단, 본 plan 은 onboarding-quickstart 의 `inferSuppressedPackages` 보조 classifier wiring 은 손대지 않고 Default 만 신규 settings 필드로 끌어올림 (호환성).
- `SettingsScreen` 의 운영 상태 카드 — 기존 "조용한 시간" row 아래에 신규 sub-row "조용한 시간 대상 앱 (N개)" 추가. 탭 시 Bottom sheet 또는 sub-screen 으로 (a) 현재 set 의 패키지 → 라벨 + 우측 "제거" 버튼 리스트 + (b) "앱 추가" 버튼 (이미 존재하는 `AppPickerScreen` 또는 동등 컴포넌트 재사용) 노출. `digest-suppression` 의 "Suppressed apps" UI (`SettingsScreen` + `AppListPicker`) 가 거의 동일한 형태이므로 동일 컴포넌트를 재사용 가능 — 그 wiring 패턴을 모방.
- 기존 `quiet-hours.md` Observable steps 의 Precondition `"대상 알림의 packageName 이 classifier 의 shoppingPackages 집합에 포함됨"` 은 그대로 유지 — 단지 그 set 이 동적 settings 출처로 바뀐다.

**Tech Stack:** Kotlin, Jetpack DataStore Preferences (stringSet), Jetpack Compose Material3 (`Card` sub-row, Bottom sheet 또는 sub-screen, `AppListPicker` 재사용), JUnit unit tests.

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `quiet-hours` 의 첫 Known gap "`shoppingPackages` 가 classifier 생성자에 하드코딩 (`setOf(\"com.coupang.mobile\")`) — 사용자가 카테고리를 확장할 수 없음." 을 해소.
- **Default 호환성**: 신규 필드의 default 는 기존 하드코딩과 동일한 `setOf("com.coupang.mobile")` 로 둔다. 첫 빌드에 새 설정 화면을 한 번도 열어보지 않은 기존 사용자도 정확히 같은 분류 결과를 본다.
- **Empty set 의미**: 사용자가 모든 앱을 제거해 set 이 `emptySet()` 이 되면 quiet-hours 분기는 발화하지 않는다 (즉 quiet-hours toggle 은 켜져 있어도 대상 앱이 없으므로 어떤 알림도 자동 DIGEST 되지 않음). 이는 Settings 의 master switch 와 별개의 의도적 control point. UI 는 set 이 비었을 때 inline 경고 카피 ("대상 앱이 없어 조용한 시간이 어떤 알림도 모으지 않아요.") 를 보여준다 — `2026-04-26-settings-quiet-hours-window-editor` plan 의 `start == end` warning 패턴을 모방.
- **Section naming**: Settings 화면의 신규 sub-row 라벨은 "조용한 시간 대상 앱" 권장 (디자인 검토에서 더 좋은 카피 발견 시 implementer 가 바꿔도 무방, 단 PR 본문에 후보 비교 명시).
- **결정 필요 (open question, plan Risks 절 참조)**: Settings 의 신규 sub-row 가 기존 "Suppressed apps (출처 숨김 대상 앱)" sub-row 와 시각적으로 매우 유사할 것 — 사용자가 둘을 혼동할 수 있다. 카피 분리가 충분한지, 또는 하나의 통합 화면 안에 두 set 을 grouped section 으로 묶을지는 implementer 가 검토 후 PR 본문에 채택 안 명시. 본 plan 은 default 로 **별도 sub-row + 별도 picker** 를 권장 (두 set 의 의미가 직교 — quiet-hours 는 "분류 정책의 대상", suppressed-source 는 "사후 출처 숨김" 이라 합치면 책임이 흐려진다).
- **결정 필요**: classifier 재생성 vs 매번 set override (Architecture 의 (A) vs (B)) — production hot path 의 dynamic input 일관성 측면에서 (A) 권장. implementer 가 다른 결정을 내리면 PR 본문에 사유 명시.
- **결정 필요**: 사용자가 추가할 앱의 source — `digest-suppression` 의 `AppListPicker` 는 이미 단말 설치 앱 전체 목록 + 검색 + multi-select 를 지원한다. 그 컴포넌트를 재사용할지, 또는 "현재 분류 결과에서 자주 등장한 쇼핑/광고 앱" 같은 추천 list 를 별도로 제공할지는 본 plan scope 외 — 단일 picker 재사용을 권장, 추천 list 는 별도 plan.
- **결정 필요**: Onboarding quick-start 의 `OnboardingQuickStartSettingsApplier.DEFAULT_SHOPPING_PACKAGES` 와 신규 `SmartNotiSettings.quietHoursPackages` default 의 single-source-of-truth — implementer 가 둘을 같은 const 에서 끌어다 쓰도록 정리 (`object QuietHoursPackagesDefaults { val DEFAULT = setOf("com.coupang.mobile") }` 같은 helper) 하거나, 본 plan scope 에서는 문서 코멘트로만 일치 의무를 남기고 후속 plan 에서 합칠지 결정. PR 본문에 명시.
- 본 plan 은 classifier 의 quiet-hours **분기 우선순위** (예: 사용자 룰 vs quiet-hours) 는 변경하지 않는다.
- 본 plan 은 `QuietHoursPolicy.isQuietAt` (시각 판정) 도 변경하지 않는다.
- 본 plan 은 `QuietHoursExplainerBuilder` (Detail UI 카피) 도 변경하지 않는다.
- DB schema / reasonTags / migration 변경 없음 — 신규 추가는 settings DataStore 키 한 개 + UI 화면 한 개.

---

## Task 1: Failing tests for `SmartNotiSettings.quietHoursPackages` round-trip + DataStore key

**Objective:** 신규 settings 필드의 read/write/default 가 다른 필드와 충돌 없이 영속·복원됨을 unit test 로 고정.

**Files:**
- 신규 또는 기존 `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryTest.kt` (현재 동등 파일이 있으면 거기에 case 추가).

**Steps:**
1. 다음 case 작성 (failing 상태로):
   - 첫 read 시 `quietHoursPackages` == `setOf("com.coupang.mobile")` (default 보장).
   - `setQuietHoursPackages(setOf("com.baemin", "com.aliexpress"))` → 다음 read 에서 동일 set 반환.
   - `addQuietHoursPackage("com.coupang.mobile")` → 기존 set 에 dedup 추가 (이미 있으면 no-op).
   - `removeQuietHoursPackage("com.coupang.mobile")` → set 에서 제거. `emptySet()` 도 허용.
   - `setQuietHoursPackages(emptySet())` → empty set 이 그대로 영속.
   - 다른 stringSet 키 (`suppressedSourceApps`, `suppressedSourceAppsExcluded`) 와 cross-contamination 없음 — 한 쪽 set 변경이 다른 쪽에 영향 없음.
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.settings.SettingsRepositoryTest"` → 새 case 들이 RED.

## Task 2: Implement `SmartNotiSettings.quietHoursPackages` + repository setters

**Objective:** Task 1 GREEN.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt` (필드 추가)
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` (DataStore 키 + read 매핑 + setter 3종)

**Steps:**
1. `SmartNotiSettings` 에 필드 추가:
   ```
   // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 2:
   // Set of packageNames the classifier treats as quiet-hours-eligible.
   // Default mirrors the previously-hardcoded `setOf("com.coupang.mobile")`
   // in `NotificationClassifier`'s constructor wiring so existing users see
   // identical behavior; the UI lets them add/remove freely.
   val quietHoursPackages: Set<String> = setOf("com.coupang.mobile"),
   ```
2. `SettingsRepository` 에 stringSet key 추가:
   ```
   private val QUIET_HOURS_PACKAGES = stringSetPreferencesKey("quiet_hours_packages")
   ```
3. `observeSettings()` 의 `prefs[...] -> SmartNotiSettings(...)` 빌드 안에 `quietHoursPackages = prefs[QUIET_HOURS_PACKAGES] ?: defaults.quietHoursPackages` 추가.
4. setter 3종 (`setQuietHoursPackages` / `addQuietHoursPackage` / `removeQuietHoursPackage`) 추가 — 기존 `setSuppressedSourceApps` / `setSuppressedSourceAppExcluded` 패턴 모방. atomic `dataStore.edit { it[QUIET_HOURS_PACKAGES] = ... }`.
5. Task 1 의 unit test → 모두 GREEN.

## Task 3: Failing test for `NotificationClassifier` 가 dynamic `shoppingPackages` 를 받았을 때의 동작

**Objective:** classifier 가 새 set 으로 재생성되거나 dynamic 으로 주입됐을 때 quiet-hours 분기가 set 멤버에만 발화하는 것을 단위 테스트로 고정. 기존 `NotificationClassifierTest` 는 `setOf("com.coupang.mobile")` 만 검증.

**Files:**
- `app/src/test/java/com/smartnoti/app/domain/usecase/NotificationClassifierTest.kt` (case 추가)

**Steps:**
1. 추가 case:
   - `shoppingPackages = setOf("com.baemin")` + input.packageName == `"com.baemin"` + quietHours == true → DIGEST.
   - `shoppingPackages = setOf("com.baemin")` + input.packageName == `"com.coupang.mobile"` + quietHours == true → quiet-hours 분기 발화 안 함 (기본 분류 path).
   - `shoppingPackages = emptySet()` + 어떤 input 이라도 quiet-hours 분기 미발화.
   - `shoppingPackages = setOf("com.coupang.mobile", "com.baemin")` + 두 패키지 모두 발화.
2. RED.

## Task 4: Wire dynamic `quietHoursPackages` into the production classifier path

**Objective:** Task 3 GREEN. `SmartNotiNotificationListenerService` (또는 `NotificationProcessingCoordinator`) 가 매 process 호출에서 최신 settings 의 `quietHoursPackages` 를 classifier 에 주입.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- (해당하면) `app/src/main/java/com/smartnoti/app/notification/NotificationProcessingCoordinator.kt` 또는 동등한 단일 call site
- `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/OnboardingQuickStartSettingsApplier.kt` (DEFAULT 단일화 — Architecture 의 SSOT 결정에 따름)

**Steps:**
1. Architecture 절의 (A) 안 권장: `NotificationProcessingCoordinator` (또는 동등 위치) 가 이미 `categoriesRepository.currentCategories()`, settings 등 dynamic input 을 inject 하는 패턴이 있다면 그 함수 시그니처에 `quietHoursPackages: Set<String>` 추가하고 settings 에서 채워 넘김. classifier 인스턴스를 매번 재생성하거나, classifier 시그니처에 set 파라미터 추가 (Architecture (B)) 둘 중 코드베이스에 더 자연스러운 안 채택 — implementer 가 nearby pattern 을 본 후 결정, PR 본문에 명시.
2. 기존 lazy `processor` 의 `shoppingPackages = setOf("com.coupang.mobile")` 하드코딩 제거.
3. `OnboardingQuickStartSettingsApplier.DEFAULT_SHOPPING_PACKAGES` 와 신규 settings default 가 단일 source 에서 나오도록 정리 (`object QuietHoursPackagesDefaults` 또는 `SmartNotiSettings.DEFAULT_QUIET_HOURS_PACKAGES` const). 본 정리 자체는 제품 동작 무변경.
4. `./gradlew :app:assembleDebug` 통과.
5. Task 3 GREEN.

## Task 5: Failing UI test for the new "조용한 시간 대상 앱" picker (only if a Compose UI test layer exists for similar settings rows; otherwise skip and rely on Task 6 ADB)

**Objective:** Settings 화면의 신규 sub-row 가 (a) 현재 set 의 N 을 표시 (b) 탭 시 picker 를 연다 (c) picker 의 add/remove 가 repository setter 를 호출 — 의 가장 얇은 wiring 을 Compose test 로 묶는다.

**Files:**
- 신규 (또는 기존) `app/src/test/java/com/smartnoti/app/ui/screens/settings/SettingsScreenQuietHoursPackagesTest.kt`

**Steps:**
1. 기존 `SettingsScreen` 에 인접한 Compose UI test 가 있는지 grep — 있으면 그 패턴 모방. 없으면 본 task SKIP 하고 Task 6 의 ADB 검증으로 대체 (PR 본문에 SKIP 사유 명시).
2. 다음 case (가능하면):
   - 신규 sub-row 의 라벨에 `(N)` 또는 `N개` 가 settings 의 `quietHoursPackages.size` 와 일치.
   - 탭 → picker 노출.
   - picker 의 "추가" 액션 → `addQuietHoursPackage(...)` 호출.
   - picker 의 "제거" 액션 → `removeQuietHoursPackage(...)` 호출.
   - set 이 비었을 때 inline 경고 카피 가시.

## Task 6: Implement Settings UI sub-row + picker

**Objective:** 사용자가 Settings 안에서 set 을 add/remove 할 수 있게 한다.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt` (신규 sub-row + 시각 wiring)
- (재사용) `app/src/main/java/com/smartnoti/app/ui/screens/settings/AppListPicker.kt` 또는 동등한 picker 컴포넌트 — 새 callback 만 추가, 기존 컴포넌트 시그니처는 가능하면 변경하지 않는다.

**Steps:**
1. `SettingsScreen` 운영 상태 카드 안 — 기존 "조용한 시간" master 토글 row 아래, `SettingsQuietHoursWindowEditor` (`2026-04-26-settings-quiet-hours-window-editor`) 의 시간 picker 옆 (또는 그 아래) 에 신규 sub-row "조용한 시간 대상 앱 (N개)" 추가. master switch OFF 일 때는 sub-row 도 숨김 또는 disabled (구체 안은 implementer 결정, 시각 톤은 인접 sub-row 와 일치).
2. Sub-row 탭 → bottom sheet 또는 sub-screen 으로 picker 진입. picker 화면 구성:
   - 헤더 카피: "조용한 시간 동안 자동으로 모아둘 앱을 골라주세요."
   - 현재 set 의 패키지 → 라벨 표시 (이미 있는 `appLabelLookup` 헬퍼 재사용) + 각 행 우측에 "제거" IconButton.
   - 하단 "앱 추가" 버튼 → 단말 설치 앱 picker (`AppListPicker` 또는 동등) 호출, multi-select 결과를 `setQuietHoursPackages(currentSet + selected)` 로 commit.
   - set 이 비었을 때 inline 경고 카피: "대상 앱이 없어 조용한 시간이 어떤 알림도 모으지 않아요."
3. master switch state, sub-row, picker 모두 `SettingsRepository.observeSettings()` 의 `collectAsState()` 로 구독.
4. 기존 "Suppressed apps" sub-row 와 시각 톤이 일관하게: AssistChip / Card padding / typography 동일.
5. `./gradlew :app:assembleDebug` 통과.

## Task 7: Manual ADB verification on emulator-5554

**Objective:** Task 4 의 dynamic wiring + Task 6 의 UI 가 end-to-end 로 발화하는지 확인.

**Steps:**
1. APK 빌드 후 설치: `./gradlew :app:installDebug`.
2. **Add 경로**:
   - Settings → 운영 상태 → "조용한 시간 대상 앱" sub-row 탭 → picker 에서 `com.smartnoti.testnotifier` 추가 → 저장.
   - DataStore 직접 확인: `run-as com.smartnoti.app cat files/datastore/smartnoti_settings.preferences_pb` 에 `quiet_hours_packages` 키와 `com.smartnoti.testnotifier` substring 존재 확인.
   - Settings 의 quiet-hours 시간 창을 현재 시각 포함하도록 조정 (`2026-04-26-settings-quiet-hours-window-editor` editor 사용).
   - `am broadcast -a com.smartnoti.debug.INJECT_NOTIFICATION -p com.smartnoti.app --es package_name com.smartnoti.testnotifier --es app_name "TestNotifier" --es title "QuietHrsExtTest" --es body "shopping body"` (debug-injector — `2026-04-26-debug-inject-package-name-extra` plan 으로 packageName extra 지원).
   - DB row 확인: `run-as com.smartnoti.app sqlite3 databases/smartnoti.db "SELECT packageName,status,reasonTags FROM notifications WHERE title='QuietHrsExtTest' ORDER BY postedAtMillis DESC LIMIT 1;"` → `status=DIGEST, reasonTags` 에 `조용한 시간` 포함.
3. **Remove 경로**:
   - 위 picker 에서 `com.coupang.mobile` 제거 → DataStore 에서 사라짐.
   - 동일 시각 창에서 `--es package_name com.coupang.mobile` 로 broadcast → 이번엔 quiet-hours 분기 발화 안 함 (status 가 DIGEST 가 아니거나, reasonTags 에 `조용한 시간` 없음). 정확한 fallback status 는 구현 detail (분류기 다른 분기) — 핵심은 quiet-hours 가 더 이상 발화하지 않는 것.
4. **Empty set 경로**:
   - picker 에서 모든 항목 제거 → 신규 inline 경고 카피 가시.
   - `addQuietHoursPackage` 한 번도 호출 안 한 상태에서 `--es package_name com.coupang.mobile` broadcast → quiet-hours 분기 미발화.
5. screenshot/uiautomator dump 캡처해 PR 본문 첨부.

## Task 8: Update `quiet-hours` journey + cross-link

**Objective:** Observable steps + Code pointers + Known gaps + Tests + Change log 동기화.

**Files:**
- `docs/journeys/quiet-hours.md`

**Steps:**
1. Observable steps — `shoppingPackages` 의 출처 한 줄 갱신: "..." (현재) → "현재 시각 + classifier 의 `shoppingPackages` 집합 (Settings 의 'quietHoursPackages' 에서 동적 로드, default `setOf(\"com.coupang.mobile\")`)". Out-of-scope 절의 "사용자가 shoppingPackages 를 수정하는 UI 는 없음 (현재 classifier 생성자에 하드코딩)." 줄은 **삭제** 또는 **(resolved YYYY-MM-DD)** 마킹 (`docs-sync.md` 가 본문 변경 금지 규칙은 Known gaps 한정 — Out-of-scope 절은 일반 contract 영역이라 본문 갱신 가능; implementer 가 한 번 더 검토).
2. Known gaps 의 첫 bullet (`shoppingPackages` 가 classifier 생성자에 하드코딩...) 을 **(resolved YYYY-MM-DD, plan `2026-04-26-quiet-hours-shopping-packages-user-extensible`)** prefix 로 마킹. 본문 텍스트 변경 금지.
3. Code pointers 에 다음 추가:
   - `data/settings/SmartNotiSettings#quietHoursPackages` (신규 필드)
   - `data/settings/SettingsRepository#setQuietHoursPackages` / `addQuietHoursPackage` / `removeQuietHoursPackage`
   - `ui/screens/settings/SettingsScreen` 의 신규 sub-row + picker
   - `notification/NotificationProcessingCoordinator` (또는 wiring site) 의 dynamic injection
4. Tests 에 신규 case 항목 (`SettingsRepositoryTest` quiet-hours-packages round-trip + `NotificationClassifierTest` dynamic shoppingPackages) 추가.
5. Change log 에 본 PR 항목 append (날짜는 implementer 가 작업한 실제 UTC 날짜 = `date -u +%Y-%m-%d`, plan link, PR link).
6. `last-verified` 는 verification recipe 를 처음부터 다시 돌렸다면 실제 실행 일자로 갱신 — Task 7 ADB 검증이 recipe 의 step 1-3 + step 4 (positive-case) 를 모두 새 settings 로 재현했으므로 갱신 가능. (`docs-sync.md` 의 verification recipe 실행 규칙 준수.)
7. README.md Verification log 에도 한 행 추가 (Task 7 결과).

## Task 9: Self-review + PR

- `./gradlew :app:testDebugUnitTest` GREEN (모든 새 case 포함).
- `./gradlew :app:assembleDebug` 통과.
- PR 본문에 다음 명시:
  1. Architecture (A) classifier 재생성 vs (B) 매번 set override 중 어느 안을 채택했는지, nearby pattern 어디를 모방했는지.
  2. `OnboardingQuickStartSettingsApplier.DEFAULT_SHOPPING_PACKAGES` 와 신규 settings default 의 single-source-of-truth 처리 방식.
  3. Settings UI 의 picker 컴포넌트 — `AppListPicker` 재사용했는지 / 새 컴포넌트 만들었는지.
  4. ADB 검증 결과 (positive add / positive remove / empty 각 1건 screenshot 또는 DB row dump).
  5. "Suppressed apps" sub-row 와 신규 sub-row 시각 비교 — 사용자 혼동 방지가 충분한지 한 줄 자기 평가.
- PR 제목 후보: `feat(quiet-hours): expose shoppingPackages set as user-editable Settings list`.
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/quiet-hours.md` 로 flip — plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- `SmartNotiSettings.quietHoursPackages: Set<String>` 신규 필드 + DataStore 키 + 3종 setter.
- `NotificationClassifier` 의 `shoppingPackages` 가 production hot path 에서 dynamic settings 출처를 받도록 wiring 변경 (Architecture (A) 권장, (B) 도 허용).
- `OnboardingQuickStartSettingsApplier.DEFAULT_SHOPPING_PACKAGES` single-source-of-truth 정리.
- `SettingsScreen` 의 신규 sub-row + picker (기존 컴포넌트 재사용).
- 신규 unit test (settings round-trip + classifier dynamic set + 가능하면 Compose UI test).
- `quiet-hours` journey 의 Observable steps / Code pointers / Known gaps annotate / Tests / Change log / `last-verified` 갱신 + README Verification log 행 추가.

**Out:**
- 단말 설치 앱 picker 컴포넌트 자체의 신규 구현 — `AppListPicker` (또는 동등) 재사용. picker 가 없거나 너무 다르면 PR 본문에 명시 후 별도 plan 으로 위임.
- 추천 list ("가장 자주 분류된 쇼핑/광고 앱 N개") 같은 자동 추천 — 별도 plan.
- quiet-hours 분기 우선순위 변경 (사용자 룰 vs quiet-hours).
- `QuietHoursPolicy.isQuietAt` (시각 판정) 변경.
- `QuietHoursExplainerBuilder` (Detail 카피) 변경.
- 다국어 / locale 별 카피.
- DB schema migration (신규 stringSet key 는 DataStore 자체 default 로 흡수).

---

## Risks / open questions

- **Suppressed-source 와의 시각·기능 혼동**: 두 sub-row 가 모두 "앱 set" 을 다루므로 사용자가 "여기에 추가하면 분류되나? 숨겨지나?" 헷갈릴 수 있음. 카피로 차이를 충분히 드러낼 수 있는지, 아니면 통합 화면 안의 grouped section 으로 묶을지 implementer 가 한 번 검토하고 PR 본문에 결정 명시.
- **Architecture (A) classifier 재생성 vs (B) 매번 set override**: production hot path 의 dynamic input 일관성 측면에서는 (A) 가 인접 패턴 (`categories-runtime-wiring-fix`) 과 같지만, classifier 가 stateless 가 아니라면 재생성 비용이 우려. implementer 가 nearby code 를 본 후 결정.
- **Onboarding quick-start 의 second classifier**: `OnboardingQuickStartSettingsApplier.inferSuppressedPackages` 가 별도 `NotificationClassifier` 인스턴스를 보조용으로 만든다 (`shoppingPackages = DEFAULT_SHOPPING_PACKAGES`). 이쪽도 새 settings 출처를 따르게 하려면 첫 진입 시 settings flow 가 아직 채워지지 않았을 가능성 — implementer 가 이 호출 경로의 timing 을 확인. 본 plan 의 default 권고는: onboarding 보조 classifier 는 default const 그대로 두고 (`SmartNotiSettings.DEFAULT.quietHoursPackages` 를 직접 사용해도 결과 동일), production listener 의 hot path 만 dynamic. PR 본문에 명시.
- **Empty set 의 의도성**: 사용자가 모든 앱을 제거했을 때 quiet-hours 가 silently no-op 되는 것을 명확히 알 수 있어야 한다. inline 경고 카피 + master switch row 의 "켜져 있음 / 대상 앱 0개" 같은 두 줄 표시가 있어야 함. UI 검토에서 한 번 더 보강.
- **`com.coupang.mobile` default 의 globalization**: 한국 사용자 기준 hard-coded default. 본 plan 은 default 를 그대로 유지 (행동 호환) 하지만, locale 별 default 분기는 별도 plan 가능 (예: 일본 locale → 라쿠텐 등). 본 plan 의 scope 외.
- **DataStore migration 순서**: 새 stringSet key 추가는 default 흡수로 무 migration 으로 안전. 기존 `applyPendingMigrations()` chain 에 새 항목 추가 불필요. 하지만 implementer 가 동일 commit 에서 다른 settings migration 을 건드리지 않도록 주의.
- **Picker UX 의 검색 가능성**: `AppListPicker` 가 단말의 모든 설치 앱을 보여줄 텐데, 사용자가 한국 쇼핑/배달 앱만 빠르게 찾으려면 검색 input 이 필요. `digest-suppression` 의 picker 가 이미 검색을 지원하면 그대로 흡수, 아니면 본 plan 은 검색 없이 ship 하고 별도 plan 으로 follow-up.

---

## Related journey

- [quiet-hours](../journeys/quiet-hours.md) — Known gaps 첫 bullet ("`shoppingPackages` 가 classifier 생성자에 하드코딩 (`setOf(\"com.coupang.mobile\")`) — 사용자가 카테고리를 확장할 수 없음.") 을 해소. 본 PR ship 후 해당 bullet 을 `(resolved YYYY-MM-DD, plan ...)` 로 마킹하고 Out-of-scope 절의 동치 줄도 정리. Change log 에 PR 링크 추가. [notification-capture-classify](../journeys/notification-capture-classify.md) 의 listener wiring 단락에도 신규 settings 출처를 한 줄 cross-link.
