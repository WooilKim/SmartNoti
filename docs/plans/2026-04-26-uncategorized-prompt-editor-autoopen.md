---
status: shipped
shipped: 2026-04-26
superseded-by: ../journeys/home-uncategorized-prompt.md
---

# Home Uncategorized-Prompt — Auto-open Editor with App Preset

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** "**새 앱 N개가 알림을 보내고 있어요**" Home prompt 의 "분류 만들기" 버튼이 단순히 `Routes.Categories` 탭으로 이동만 시키는 현재 동작을 바꿔, 같은 탭으로 이동하면서 **즉시 `CategoryEditorScreen` (CategoryEditorTarget.New) 다이얼로그를 자동으로 띄우고**, prompt 가 감지한 첫 번째 uncovered app (`prompt.samplePackageNames.first()` + `prompt.sampleAppLabels.first()`) 으로 `appPackageName` 과 `name` 을 prefill 한다. 사용자는 카드 탭 → 분류 탭 진입 → FAB 한 번 더 탭 → 빈 다이얼로그 → 앱 dropdown 다시 선택 의 4-step 마찰을 거치지 않고 카드 탭 한 번으로 사전 채워진 editor 에 도달한다. "취소" / 다이얼로그 dismiss 시 비어있는 분류 탭에 그대로 머무른다 (ack 비용 0).

**Architecture:** `Routes.Categories` 가 현재 인자 없는 `data object` 라서 deep-link 로 prefill 을 흘릴 채널이 없다. 두 옵션 — (a) `Routes.Categories` 를 `categories?prefillPackage={pkg}&prefillLabel={label}` 형태로 query-arg 변형, (b) Routes 는 그대로 두고 `MainActivity` 와 동급의 in-process holder (가칭 `CategoriesPendingEditorTarget` SnapshotStateHolder) 를 두고 Home 에서 set, CategoriesScreen 의 `LaunchedEffect` 가 consume — 이 plan 은 (a) 를 채택한다. 이유: 프로세스 재시작 / config change 안전, deep-link / launcher shortcut 으로 동일 흐름을 재활용 가능, 기존 `Routes.Hidden` / `Routes.Rules` 가 같은 패턴을 이미 사용 (선례 일관). `CategoriesScreen` 은 nav arg 두 개를 받아 처음 composition 시 `editorTarget = CategoryEditorTarget.New` 로 set + `CategoryEditorPrefill(name = label, appPackageName = pkg, pendingRule = null, defaultAction = CategoryAction.PRIORITY, seedExistingRuleIds = emptyList())` 를 editor 에 전달. `HomeUncategorizedAppsPromptCard` 의 `onCreateCategory` 콜백은 prompt 자체를 받아 첫 sample 을 추출 후 새 시그니처의 `Routes.Categories.create(prefillPackage, prefillLabel)` 를 호출. 한 번 consume 한 prefill 은 `rememberSaveable` flag 로 lock 해서 dialog dismiss 후 navigation 재진입 (back stack pop / re-tap) 시 재발화하지 않는다 — 사용자가 명시적으로 닫은 다이얼로그가 매번 재오픈되면 회피 불가능한 modal trap.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Navigation (nav-args), Gradle JVM unit tests + Compose UI test (Robolectric or composeTestRule).

---

## Product intent / assumptions

- 기존 journey 의 Known gap: "`분류 만들기` 는 현재 `Routes.Categories` 만 열고 editor 자동 오픈은 하지 않음 — 사용자가 FAB 를 한 번 더 탭해야 editor 진입. 전용 wizard 가 shipped 되면 바로 app-preset 이 채워진 상태로 진입할 예정 (plan Task 10 wizard 부분은 scaffold 범위)." 이 plan 은 그 후속 wizard 의 **MVP** — 전용 multi-step wizard 가 아니라 기존 `CategoryEditorScreen` 다이얼로그를 prefill 된 채로 자동 오픈하는 가장 짧은 경로. 더 풍부한 wizard 는 별도 후속.
- prefill 의 첫 번째 sample 만 사용한다. 이유: prompt 는 "최근 7일 newest-first 로 3개" 를 노출하지만 사용자가 "이 N개 앱을 한 분류로 묶고 싶다" 고 가정할 수 없다 (앱마다 의도된 카테고리가 다를 가능성이 더 높다). 다중 선택 / 한 번에 N개 분류는 별도 plan — 본 plan 은 "3건 중 가장 최근 1건" 만 prefill 하고 사용자는 editor 의 multi-rule selector 로 더 추가 가능. 이 결정은 plan 단계에서 고정 — implementer 에게 넘기지 않는다.
- `defaultAction = CategoryAction.PRIORITY` 로 prefill — 사용자가 "이 앱을 챙겨야 한다" 고 판단해 카드를 탭한 시그널이라 PRIORITY 가 가장 안전한 default. 사용자는 dropdown 으로 즉시 변경 가능. 이 결정도 plan 단계 고정 — DIGEST default 면 "왜 묻지도 않고 조용히로 보내?" 회귀 위험.
- `pendingRule = null` 로 prefill — Detail 의 "새 분류 만들기" 경로와 다르게 Home prompt 는 **app 단위** 시그널이다 (특정 알림 row 가 아니라 packageName 빈도). app-pin 단독 Category 가 만들어지고 Rule 은 사용자가 editor 의 rule selector 에서 직접 추가 (필요 시 sender 키워드 룰을 그 자리에서 만들 수 있는 흐름은 이미 editor 에 존재 → 변경 없음).
- prefill 이 **단발성 consume** 이어야 하는 이유: 사용자가 다이얼로그를 dismiss → 분류 탭에 머무름 → 다른 카테고리 row 탭 → editor 가 그 row 로 열림. 만약 nav arg 가 살아있다면 dismiss 직후 재발화로 modal trap. `rememberSaveable("prefillConsumed")` 로 한 번 consume 후 lock.
- **남는 open question (user 결정 필요)**: prompt body 가 "외 N-3개 앱" 을 보여주는 상태에서 사용자가 "분류 만들기" 를 탭했을 때, 첫 sample 만 prefill 하고 editor 에 "다른 N-1개 앱은 분류 탭에서 별도로 만들어주세요" 같은 hint copy 를 띄울지. 본 plan 은 hint 없이 가구현 (editor 의 시각 노이즈 회피) 하고 PR 본문에서 사용자 확정 요청.

---

## Task 1: Add failing test for `Routes.Categories.create` with prefill args [IN PROGRESS via PR #409]

**Objective:** nav arg 인코딩 계약을 unit test 로 고정. 한글 label 이 들어오면 percent-encoded 로 흘러야 하고 인자 누락 시 기존 `"categories"` 를 그대로 반환해야 한다 (회귀 안전).

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/navigation/RoutesCategoriesPrefillTest.kt`

**Steps:**
1. 테스트 케이스:
   - `Routes.Categories.create()` (인자 없음) → `"categories"` (기존 호출부 회귀 없음).
   - `Routes.Categories.create(prefillPackage = "com.example.app", prefillLabel = "예시 앱")` → `"categories?prefillPackage=com.example.app&prefillLabel=%EC%98%88%EC%8B%9C%20%EC%95%B1"` (기존 `Hidden.create` 의 `encodeRouteParam` 과 동일 인코딩).
   - `Routes.Categories.create(prefillPackage = "  ", prefillLabel = "라벨")` → `"categories"` — blank package 는 absent 취급 (라벨 단독으로는 prefill 의미가 없음).
   - `Routes.Categories.create(prefillPackage = "com.example.app", prefillLabel = "")` → `"categories?prefillPackage=com.example.app"` — label 누락은 허용 (editor 의 이름 필드만 empty 로 둘 뿐 package 는 prefill).
2. `./gradlew :app:testDebugUnitTest --tests "*.RoutesCategoriesPrefillTest"` → RED 확인.

## Task 2: Extend `Routes.Categories` to accept prefill args [IN PROGRESS via PR #409]

**Objective:** Task 1 의 테스트를 GREEN 으로.

**Files:**
- `app/src/main/java/com/smartnoti/app/navigation/Routes.kt`

**Steps:**
1. `data object Categories : Routes("categories")` 를 `data object Categories : Routes("categories?prefillPackage={prefillPackage}&prefillLabel={prefillLabel}")` 로 변경.
2. `fun create(prefillPackage: String? = null, prefillLabel: String? = null): String` 추가 — 기존 `Hidden.create` 와 같은 패턴 (blank trim → absent → joinToString("&")). 인자 0개면 `"categories"` 반환.
3. 기존 호출부 (`AppNavHost.kt:216` `onCreateCategoryClick = { navigateToTopLevel(Routes.Categories.route) }`, `BottomNavItem.kt:28` `Routes.Categories.route`, `Phase A` deep-links 등) 가 `route` 프로퍼티만 쓰기 때문에 시그니처 변경 영향 없음 — `Routes.Categories.route` 는 여전히 `"categories?…"` 패턴 자체이고 NavController 가 default null 로 매칭한다. 단, `navigateToTopLevel` 헬퍼가 정확히 이 패턴 문자열을 destination 으로 처리하는지 확인 — 현행 코드 grep 으로 중복 호출부 인벤토리 (`Routes.Categories.route` literal usage 5건 미만이면 default 인자 변환만으로 충분).
4. `./gradlew :app:testDebugUnitTest --tests "*.RoutesCategoriesPrefillTest"` → GREEN.

## Task 3: Wire nav args into `AppNavHost` Categories composable

**Objective:** Routes 변경을 nav graph 가 따라가도록 `composable(Routes.Categories.route)` 에 `arguments` 선언 추가 + `CategoriesScreen` 호출부에 두 인자 전달.

**Files:**
- `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`

**Steps:**
1. 현재:
   ```kotlin
   composable(Routes.Categories.route) {
       CategoriesScreen(
           contentPadding = paddingValues,
           onOpenNotification = { notificationId ->
               navController.navigate(Routes.Detail.create(notificationId))
           },
       )
   }
   ```
   변경 후:
   ```kotlin
   composable(
       route = Routes.Categories.route,
       arguments = listOf(
           navArgument("prefillPackage") {
               type = NavType.StringType
               nullable = true
               defaultValue = null
           },
           navArgument("prefillLabel") {
               type = NavType.StringType
               nullable = true
               defaultValue = null
           },
       ),
   ) { backStackEntry ->
       CategoriesScreen(
           contentPadding = paddingValues,
           onOpenNotification = { ... },
           prefillPackage = backStackEntry.arguments?.getString("prefillPackage"),
           prefillLabel = backStackEntry.arguments?.getString("prefillLabel"),
       )
   }
   ```
2. `Rules` composable (`AppNavHost.kt:250-264`) 의 `highlightRuleId` 패턴을 시각적 reference 로 사용. 동일 import (`import androidx.navigation.navArgument`, `import androidx.navigation.NavType`) 가 이미 파일 상단에 있는지 grep — 있으면 신규 import 불필요.
3. 빌드만 확인 (`./gradlew :app:assembleDebug`); 동작 검증은 Task 6.

## Task 4: Add failing test for `CategoriesScreen` prefill auto-open

**Objective:** Compose UI test 로 prefill 인자가 들어오면 첫 composition 시 editor 가 자동 오픈되고, dismiss 후 같은 destination 으로 재진입해도 재오픈되지 않는 contract 를 고정.

**Files:**
- 신규 또는 보강: `app/src/androidTest/java/com/smartnoti/app/ui/screens/categories/CategoriesScreenPrefillTest.kt` (Robolectric 또는 composeTestRule 기반 — 기존 `CategoriesScreen` 의 테스트 인프라가 있다면 그쪽 패턴 재사용. JVM-only 라면 `app/src/test/...` 로 옮기되 Compose 관련 모듈 의존성 확인.)

**Steps:**
1. 테스트 케이스:
   - **auto-open with prefill**: `CategoriesScreen(prefillPackage = "com.example.app", prefillLabel = "예시")` 를 마운트하면, `CategoryEditorScreen` 의 다이얼로그 root (예: testTag `category_editor_dialog`) 가 visible. 이름 입력칸의 초기 값이 `"예시"`. 앱 dropdown 의 selected app 이 `com.example.app` 으로 표시 (editor 가 `effectivePrefill.appPackageName` 을 picker default 로 사용하는지는 기존 plan `2026-04-22-categories-runtime-wiring-fix` Task 2 가 보장 — 회귀 검증).
   - **single consume**: 위 상태에서 editor "취소" 탭 → editor 사라짐 → 같은 인자로 마운트된 CategoriesScreen 이 recomposition 되어도 editor 자동 재오픈되지 않음 (테스트는 manually `recompose()` or state drive). `rememberSaveable("uncategorizedPrefillConsumed")` 의 contract.
   - **no prefill = no auto-open**: `CategoriesScreen()` (인자 없음) 마운트 → editor 미표시. 기존 회귀 안전.
   - **save 도 single consume 에 포함**: prefill 로 자동 오픈된 editor 에서 "저장" 탭 → editor 사라짐 → recomposition 시 재오픈 없음.
2. `./gradlew :app:testDebugUnitTest --tests "*.CategoriesScreenPrefillTest"` 또는 `:app:connectedDebugAndroidTest` 중 적절한 sourceSet 으로 실행 → RED 확인.

## Task 5: Implement prefill auto-open in `CategoriesScreen`

**Objective:** Task 4 의 테스트를 GREEN 으로.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt`

**Steps:**
1. 시그니처 확장:
   ```kotlin
   fun CategoriesScreen(
       contentPadding: PaddingValues,
       onOpenNotification: (String) -> Unit,
       prefillPackage: String? = null,
       prefillLabel: String? = null,
   )
   ```
   default 값으로 호환 — 다른 호출부 (테스트, deep-link 미사용 진입 경로) 가 회귀 없이 컴파일.
2. `var editorTarget by remember { mutableStateOf<CategoryEditorTarget?>(null) }` 옆에 `var prefillConsumed by rememberSaveable { mutableStateOf(false) }` 추가.
3. `LaunchedEffect(prefillPackage, prefillLabel, prefillConsumed) { … }` 로 `if (!prefillConsumed && (prefillPackage?.isNotBlank() == true || prefillLabel?.isNotBlank() == true)) { editorTarget = CategoryEditorTarget.New; prefillConsumed = true }` 실행. consume 가 즉시 일어나기 때문에 dismiss 후 recomposition 에서 재발화 없음.
4. `CategoryEditorScreen` 호출부 (CategoriesScreen.kt 137 / 232 두 곳 — 상세 페이지 dialog wrapper 와 list FAB 호출부) 에 `prefill = if (editorTarget == CategoryEditorTarget.New && prefillConsumed && (prefillPackage != null || prefillLabel != null)) CategoryEditorPrefill(name = prefillLabel.orEmpty(), appPackageName = prefillPackage, pendingRule = null, defaultAction = CategoryAction.PRIORITY, seedExistingRuleIds = emptyList()) else null` 전달. **주의**: prefill 은 첫 auto-open 1회만 의미가 있어야 하므로, 사용자가 dismiss 후 다시 FAB 로 New 다이얼로그를 열면 빈 editor 가 떠야 한다. 이를 위해 `prefill` 계산 시 별도 `var prefillStillActive by remember { mutableStateOf(false) }` flag 를 두거나, 더 단순하게 `editorTarget` 을 처음 set 할 때 함께 `effectivePrefillForNew = CategoryEditorPrefill(...)` snapshot 해두고 dismiss/save 시 null 화. 구현은 implementer 가 가장 간결한 패턴 선택 — Task 4 의 4번째 case ("save 후 재진입 시 빈 editor") 가 회귀 detector.
5. `./gradlew :app:testDebugUnitTest --tests "*.CategoriesScreenPrefillTest"` → GREEN.

## Task 6: Wire `HomeUncategorizedAppsPromptCard.onCreateCategory` to send prefill

**Objective:** Home 의 prompt 카드가 첫 sample 을 추출해 `Routes.Categories.create(...)` 로 navigation.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/home/HomeUncategorizedAppsPromptCard.kt`
- `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`

**Steps:**
1. `HomeUncategorizedAppsPromptCard.onCreateCategory: () -> Unit` 시그니처는 그대로 둔다 (호출부 호환). 단, 내부에서 콜백을 invoke 하기 전에 첫 sample 을 추출할 책임은 **HomeScreen 호출부** 가 진다 — 카드 자체는 navigation 모름.
2. `HomeScreen` 의 `HomeUncategorizedAppsPromptCard(...)` 호출부 (HomeScreen.kt:208 부근) 에서:
   ```kotlin
   HomeUncategorizedAppsPromptCard(
       prompt = prompt,
       onCreateCategory = {
           val pkg = prompt.samplePackageNames.firstOrNull()?.takeIf { it.isNotBlank() }
           val label = prompt.sampleAppLabels.firstOrNull()?.takeIf { it.isNotBlank() }
           onCreateCategoryClick(pkg, label)
       },
       onSnooze = onSnoozeUncategorizedPrompt,
   )
   ```
3. `HomeScreen` 시그니처의 `onCreateCategoryClick: () -> Unit` 을 `onCreateCategoryClick: (prefillPackage: String?, prefillLabel: String?) -> Unit` 로 변경.
4. `AppNavHost.kt:216` 호출부:
   ```kotlin
   onCreateCategoryClick = { pkg, label ->
       navigateToTopLevel(Routes.Categories.create(prefillPackage = pkg, prefillLabel = label))
   },
   ```
5. `navigateToTopLevel` 가 destination 문자열을 `popUpTo` / `launchSingleTop` 정책으로 처리한다 — `Routes.Categories.route` (패턴) 가 아니라 `Routes.Categories.create(...)` (구체 URL) 를 받아도 정상 매칭하는지 확인 (현행 `Routes.Hidden.create(...)` / `Routes.Detail.create(...)` 호출부와 동일 — popUpTo 의 startDestination 매칭은 패턴 기반이므로 안전).
6. 빌드 + manual smoke (Task 7 verification recipe).

## Task 7: Verify end-to-end on emulator

**Objective:** prompt 카드 탭 → editor 자동 오픈 + prefill 채워짐 → 저장 → DataStore 에 새 Category persist + Home 에서 prompt 자동 사라짐.

**Steps:**
```bash
# 1. 사전: Categories DataStore 가 비어 있거나 sample app 들이 어느 Category 에도
#    pin 되지 않은 상태. journey doc Verification recipe 1번 항목 참조.

# 2. 서로 다른 3개 패키지로 알림 게시 — 실 device 또는 다중 패키지 환경 필요.
adb shell am force-stop com.smartnoti.app
adb shell cmd notification post -S bigtext -t "테스트앱A" T1 "알림1"
# (B, C 도 동일)

# 3. 앱 진입.
adb shell am start -n com.smartnoti.app/.MainActivity

# 4. Home 최상단 "새 앱 N개가 알림을 보내고 있어요" 카드 확인.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | grep -oE 'text="새 앱 [0-9]+개[^"]*"'

# 5. "분류 만들기" 탭.
adb shell input tap <coords from ui dump>

# 6. 다이얼로그가 즉시 뜨고, 이름 칸에 첫 sample 라벨이 채워져 있고,
#    앱 dropdown 의 selected app 이 첫 sample package 인지 확인.
adb shell uiautomator dump /sdcard/ui-editor.xml >/dev/null
adb shell cat /sdcard/ui-editor.xml | grep -oE 'text="테스트앱A"'

# 7. "취소" 탭. 다이얼로그가 사라지고 분류 탭의 빈 화면 / 기존 list 가 보이는지 확인.
#    Home 으로 back-press → "분류 만들기" 재탭 → 다이얼로그가 다시 사전 채워진 상태로
#    뜨는지 확인 (재진입은 새 nav arg 흐름 → 새 prefill consume).

# 8. 저장 → 분류 탭 list 에 새 row 추가 + Home prompt 가 다음 composition 에서
#    `coveredPackages` 갱신으로 비표시 (uncoveredCount 가 3 미만으로 떨어지면).
```

## Task 8: Update journey docs

**Files:**
- `docs/journeys/home-uncategorized-prompt.md` — Observable steps 4 ("분류 만들기" 탭 결과) 를 "Routes.Categories 로 이동 + editor 자동 오픈 (첫 sample app 으로 prefill)" 으로 갱신. Code pointers 에 `Routes.Categories.create(prefillPackage, prefillLabel)` + `CategoriesScreen.prefillPackage/prefillLabel` 추가. Known gaps 의 "전용 wizard 가 shipped 되면..." bullet 을 "(resolved YYYY-MM-DD, plan 2026-04-26-uncategorized-prompt-editor-autoopen) 첫 sample app prefill + editor 자동 오픈으로 4-step 마찰 해소. multi-app 동시 분류 / 전용 multi-step wizard 는 별도 후속." 로 수정. Change log 에 ship 일자 + commit/PR 추가.
- `docs/journeys/categories-management.md` — Code pointers / Trigger 섹션에 "외부 deep-link (Home prompt) 진입 시 nav arg 로 자동 editor 오픈 가능" 한 줄 추가. 다른 동작은 그대로 (FAB / row 탭 / Detail 진입 경로 모두 회귀 없음).

## Task 9: Annotate journey gap with plan link (gap-planner side-effect)

**Files:**
- `docs/journeys/home-uncategorized-prompt.md` — 해당 Known-gap bullet 옆에 `→ plan: docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md` 추가. (gap text 자체는 변경하지 않음 — docs-sync.md 규약.)

## Task 10: Self-review + PR

- 모든 단위 + UI 테스트 통과.
- ADB 시나리오 결과 (스크린샷 / ui dump fragment) PR 본문 첨부.
- PR 제목: `feat: auto-open category editor with app preset from Home uncategorized prompt`.
- frontmatter `status: planned → shipped` + `shipped: <ship date>` + `superseded-by: ../journeys/home-uncategorized-prompt.md`.

---

## Scope

**In:**
- `Routes.Categories` query-arg 두 개 (`prefillPackage`, `prefillLabel`) 추가 + `create(...)` 헬퍼.
- `AppNavHost` 의 `Routes.Categories` composable 에 `arguments` + `CategoriesScreen` 호출부 prefill 두 인자 전달.
- `CategoriesScreen` prefill 인자 + 첫 composition `LaunchedEffect` 자동 오픈 + `rememberSaveable("prefillConsumed")` lock.
- `CategoryEditorPrefill` 을 editor 호출부에 전달 (defaultAction = PRIORITY, pendingRule = null).
- `HomeUncategorizedAppsPromptCard` 호출부 (HomeScreen) 에서 첫 sample 추출 + 새 시그니처 콜백.
- 위 모든 contract 의 단위/UI 테스트.
- 두 journey doc 갱신.

**Out:**
- 전용 multi-step wizard (이름 단계 / app 단계 / rule 단계 / action 단계 분리) — MVP 가 다이얼로그 자동 오픈 + 첫 sample prefill 로 충분.
- Multi-app 동시 분류 ("3개 sample app 모두 한 분류로 묶기" 또는 "각 sample 별로 분류 1:1 일괄 생성") — 사용자 의도 추정 위험.
- prompt body 에 "외 N-3개 앱은 분류 탭에서 별도로 만들어주세요" 같은 hint copy.
- editor 의 sender 키워드 룰 자동 생성 (Home prompt 는 app-단위 시그널이므로 app-pin Category 만 prefill).
- 사용자가 editor 에서 저장하지 않고 dismiss 한 경우 prompt 의 snooze 자동 갱신 — dismiss 는 의도적 cancel 일 수 있어 자동 snooze 면 prompt 가 24h 사라지는 회귀.

---

## Risks / open questions

- **`navigateToTopLevel` 헬퍼의 destination 매칭**: `Routes.Categories.create("com.example.app", "예시")` 가 BottomNav 의 `popUpTo(startDestination)` 정책과 충돌하지 않는지 확인. NavController 가 패턴 기반 매칭을 하므로 query-arg 가 있는 구체 URL 은 패턴 destination 에 정상 매칭하지만, "이미 Categories 탭에 있는 상태에서 같은 destination 재진입" 시 nav arg 가 갱신되는지 (`launchSingleTop` 와 `restoreState` 의 상호작용) 검증 필요. 회귀가 보이면 `launchSingleTop = false` 로 강제하거나 별도 `popUpTo(Routes.Categories.route, inclusive = true)` 추가.
- **첫 sample 단일 prefill 의 UX 적합성**: prompt 가 "새 앱 5개" 를 보여주는데 사용자가 카드 탭 후 첫 1개만 prefill 된 editor 를 보고 "나머지 4개는?" 으로 혼란을 느낄 가능성. 대응: PR 본문 + Risks 에 명시 + 후속 plan 으로 multi-app wizard 검토. 본 plan 은 hint copy 추가하지 않음 (시각 노이즈 회피).
- **`defaultAction = PRIORITY` 의 안전성**: PRIORITY 가 default 면 사용자가 무심코 저장 시 새 앱이 PRIORITY 로 분류되어 알림이 그대로 뚫고 들어옴 (silence 기대 위배). 반대로 SILENT/IGNORE default 는 "조용히로 보내려고 카드 탭한 게 아닌데" 회귀. plan 단계에서 PRIORITY 로 고정한 근거: prompt 카드 탭 == "이 앱을 챙기겠다" 시그널. 사용자가 dropdown 으로 즉시 변경 가능. 이 결정이 회귀로 신고되면 후속 plan 에서 default 변경.
- **다이얼로그 dismiss 후 nav arg 잔존**: `rememberSaveable("prefillConsumed")` lock 으로 막지만, configuration change (rotation) 후 lock 이 saver 로 보존되는지 Task 4 의 case 로 검증 필수. composeTestRule 의 rotation 시뮬레이션 비용이 크면 단위 테스트는 lock 자체의 truth-table 만 검증하고 rotation 회귀는 manual smoke 에 위임.
- **Editor 의 prefill 처리가 plan 2026-04-22-categories-runtime-wiring-fix Task 2 와 정합**: 같은 `CategoryEditorPrefill` 시그니처를 재사용한다 — `pendingRule = null` 케이스가 editor 의 `effectivePrefill?.pendingRule?.id` 추출 분기에서 안전하게 short-circuit 되는지 (Code 본문 확인) — plan 2026-04-22 의 작업 산출물이므로 이미 안전할 것으로 추정하되 Task 5 에서 실증 필요.

---

## Related journey

- `docs/journeys/home-uncategorized-prompt.md` — 본 plan 이 해소하는 Known gap 의 owner. 본 plan ship 시 해당 Known-gap bullet 은 Change log entry 로 대체된다 (Task 8).
- `docs/journeys/categories-management.md` — Trigger / Code pointers 에 외부 deep-link 진입 경로가 추가됨을 반영 (Task 8).
