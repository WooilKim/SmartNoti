# Rules DataStore Dedup Fix (Launch Crash)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. This is a production crash hotfix — keep scope tight.

**Goal:** 앱 실행 즉시 발생하는 `IllegalStateException: There are multiple DataStores active for the same file: .../smartnoti_rules.preferences_pb` 크래시를 제거해 Home 화면이 정상 렌더되게 한다. 사용자가 관측하는 결과는 "앱을 켜면 앱이 뜬다" — 회귀 이전 동작의 복원.

**Architecture:** Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 에서 `LegacyRuleActionReader` 가 한 번만 읽어야 하는 legacy payload 를 위해 `preferencesDataStore(name = "smartnoti_rules")` delegate 를 **두 번째로** 선언했다 (Kotlin file-top 레벨 property). AndroidX `preferencesDataStore` 는 같은 파일명을 두 번 생성하면 `IllegalStateException` 을 던진다. 수정은 `RulesRepository` 가 `smartnoti_rules` DataStore 의 단일 소유자가 되고 (`RulesRepository.dataStore` 를 internal 로 노출), `LegacyRuleActionReader` 가 그 handle 을 생성자 주입으로 받도록 한다. Option (a) ownership model — `LegacyRuleActionReader` 는 `RulesRepository` 의 reader 역할이므로 의존 방향이 자연스럽다. Option (b) (공유 Context extension) 은 reject — 파일명이 여러 모듈에 흩어지면 다음 회귀가 숨기 쉽다.

**Tech Stack:** Kotlin, AndroidX DataStore Preferences, JUnit4, Robolectric (이미 쓰고 있는 경우) — 없다면 순수 JVM 로 `ApplicationProvider` 없이 `Context` 를 mock 하지 말고 진짜 instrumentation 필요 여부를 Task 1 에서 판단.

---

## Product intent / assumptions

- 이 PR 은 **크래시 fix only**. `LegacyRuleActionReader` 의 동작 (one-shot legacy action scan) 은 바뀌지 않는다 — 단지 어느 DataStore handle 을 쓰는지만 바뀐다.
- `MigrateRulesToCategoriesRunner.create(context)` 는 내부에서 `RulesRepository.getInstance(appContext)` 를 이미 호출하고 있으므로, `LegacyRuleActionReader` 생성 시 같은 instance 로부터 handle 을 받는 경로는 자연스럽게 존재한다.
- Categories rollout (P1/P2) 의 migration 순서 — `LegacyRuleActionReader.readRuleActions()` 가 `RuleStorageCodec` 의 7/8-column rewrite 이전에 호출되어야 한다는 invariant — 는 유지. 단일 DataStore handle 로 바꿔도 `readRuleActions()` 는 여전히 첫 `data.first()` 호출이라 같은 payload 를 본다.
- Plan #248 (implementer 가 발견) 는 이 크래시를 해결할 때까지 블록. 이 plan 이 shipped 된 뒤 재개.

---

## Task 1: RED test — both classes can be instantiated without DataStore collision

**Objective:** 회귀를 테스트로 고정. `RulesRepository` 와 `LegacyRuleActionReader` 가 같은 `Context` 위에서 함께 live 할 때 `smartnoti_rules.preferences_pb` 를 건드려도 `IllegalStateException` 이 나지 않아야 한다.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/data/rules/RulesDataStoreSingleOwnerTest.kt`

**Steps:**
1. Robolectric 기반 (`@RunWith(RobolectricTestRunner::class)`) unit test. 기존 테스트 중 `ApplicationProvider.getApplicationContext()` 를 쓰는 곳이 있는지 확인하고 그 패턴을 차용. 없으면 Robolectric dependency 추가 여부를 PR body 의 open question 으로 올리고 일단 instrumentation test (`app/src/androidTest/...`) 로 작성.
2. Test:
   - `val ctx = ApplicationProvider.getApplicationContext<Context>()`
   - `val repo = RulesRepository.getInstance(ctx)`
   - `val reader = LegacyRuleActionReader(ctx)` (또는 새 생성자 시그니처에 맞춰)
   - `runBlocking { repo.observeRules().first(); reader.readRuleActions() }` — 둘 다 DataStore 를 터치하게 강제.
   - Assertion: no exception thrown. 추가로 `RulesRepository` 의 DataStore 가 최소 한 번 emit (빈 map 이어도 OK) 되는지.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.rules.RulesDataStoreSingleOwnerTest"` 실행 → `IllegalStateException` 으로 실패 확인. 이 RED 가 Task 2 의 근거.
4. 현재 `RulesRepository.getInstance` 가 process-wide singleton 이므로 기존 테스트의 singleton 누수를 막기 위해 `@After` 에서 `RulesRepository` 의 `instance` 를 reflection 으로 리셋하거나, 내부 테스트용 `resetForTests()` 를 최소 노출. 후자가 안전 — 상세는 구현 시 결정하고 Risks 에 기록.

## Task 2: Consolidate to a single DataStore owner

**Objective:** Task 1 의 테스트가 초록. 앱이 정상 실행되고 Home 이 렌더.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/rules/RulesRepository.kt` — DataStore handle 을 internal property 로 노출 (또는 reader API 를 직접 제공).
- `app/src/main/java/com/smartnoti/app/data/categories/LegacyRuleActionReader.kt` — top-level `preferencesDataStore(name = "smartnoti_rules")` delegate 제거, 생성자에 `DataStore<Preferences>` 주입 받도록 시그니처 변경.
- `app/src/main/java/com/smartnoti/app/data/categories/MigrateRulesToCategoriesRunner.kt` — `LegacyRuleActionReader` 생성 시 `rulesRepository` 의 handle 을 전달하도록 wiring 수정 (`RulesRepository.getInstance(appContext)` 가 이미 `create(context)` 안에 있으므로 한 줄 수정).

**Steps:**
1. `RulesRepository` 에 `internal val dataStore: DataStore<Preferences>` 형태로 DataStore delegate 를 프로퍼티로 승격. 기존 top-level `Context.rulesDataStore` delegate 는 `RulesRepository` 안으로 이동하되 file-level 접근이 필요한 다른 사용처가 있는지 grep 으로 확인 (현재 없음).
2. `LegacyRuleActionReader` 생성자 변경: `class LegacyRuleActionReader(private val dataStore: DataStore<Preferences>)`. `context.legacyRulesDataStore.data.first()` → `dataStore.data.first()`. top-level `legacyRulesDataStore` delegate + import 제거.
3. `MigrateRulesToCategoriesRunner.create` 에서 `LegacyRuleActionReader(appContext)` → `LegacyRuleActionReader(rulesRepository.dataStore)` (변수 이름은 실제 구현 맞춰).
4. `./gradlew :app:testDebugUnitTest` 전체 통과 확인 — 기존 `RulesRepositoryDeleteCascadeTest` 등이 깨지지 않는지.
5. **ADB smoke** — 크래시 재현 경로 검증:
   ```bash
   # 빌드 + 설치
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk

   # 앱 강제 종료 후 cold-launch
   adb shell am force-stop com.smartnoti.app
   adb logcat -c
   adb shell am start -n com.smartnoti.app/.MainActivity

   # logcat 에서 IllegalStateException + smartnoti_rules.preferences_pb 메시지가 안 나오는지 확인
   adb logcat -d | grep -E "IllegalStateException|smartnoti_rules.preferences_pb"

   # Home 이 렌더됐는지 dump
   adb shell uiautomator dump /sdcard/ui.xml >/dev/null
   adb pull /sdcard/ui.xml /tmp/smoke_home.xml
   grep -oE 'text="[^"]*"' /tmp/smoke_home.xml | head -20
   ```
   기대치: crash stack 없음, `ui.xml` 에 Home 화면 expected text (예: "조용히", "중요") 렌더.
6. PR body 에 `adb logcat -d` 결과 (또는 "grep 결과 empty") 와 Home dump 일부 첨부.

---

## Scope

**In**
- 단일 `smartnoti_rules` DataStore ownership 정리.
- `LegacyRuleActionReader` 생성자 시그니처 + 그 호출부 1곳 (`MigrateRulesToCategoriesRunner.create`).
- RED test + ADB cold-launch smoke.

**Out**
- `CategoriesRepository` / `SettingsRepository` 의 DataStore handle — 파일명이 서로 달라 충돌 없음. 건드리지 않음.
- Plan #248 의 Categories 기능 자체 — 이 fix merge 후 재개.
- DataStore 소유권의 일반화 리팩터 (DI 컨테이너, Hilt 도입 등) — out of scope. Risks 에 "후속 정리 여지" 로만 기록.
- `MigrateRulesToCategoriesRunner` 의 migration 로직 (7/8-column codec, Category upsert 순서 등). 이 fix 는 그 전 단계.

---

## Risks / open questions

- **`RulesRepository.dataStore` 를 `internal` 로 노출하는 것이 캡슐화 원칙에 거슬리는지.** 대안은 `RulesRepository` 에 `readRawPayload(key: Preferences.Key<String>): String?` 같은 좁은 read-through API 를 만드는 것. 이 PR 에서는 "hotfix 우선, 표면은 최소" 기준으로 `internal val dataStore` 를 택하되, 후속 plan 에서 좁힐지 결정. **Open question for user:** hotfix 는 `internal val dataStore` 로 가고, 좁히는 리팩터는 별도 plan 으로 분리해도 되는가?
- **Robolectric 의존성.** 현재 `app/src/test/` 가 Robolectric 을 쓰고 있는지 Task 1 에서 확인 필요. 안 쓰면 (a) instrumentation test 로 옮기거나 (b) Robolectric 도입. (a) 가 CI 비용이 싸다면 (a). 이 판단은 구현 시 빠르게 — Risks 에만 기록하고 보고로 올림.
- **Singleton reset.** `RulesRepository.getInstance` 가 process-wide 이므로 테스트 간 누수 가능. `resetForTests()` 같은 internal 함수로 해결하되, 프로덕션 코드에 test-only 표면이 늘어나는 trade-off 있음. 대안: test 가 각자 새 Context 를 쓰게 (Robolectric 은 test 마다 fresh application). 구현 시 선택.
- **회귀 재발 방지.** 이 크래시가 다시 들어오지 않으려면 "한 파일명에 두 delegate 선언" 을 막는 lint / codestyle 규칙이 필요할 수 있음. 이 PR 에서는 테스트로만 고정 — 규칙화는 후속.
- **Categories rollout 순서.** Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 이 이미 main 에 merged 되어 있으므로 이 fix 는 그 이후 cherry-pick / follow-up 형태. rebase 충돌 여지는 낮지만 `LegacyRuleActionReader.kt` 가 최근 수정된 파일이라 conflict 발생 시 신중히 수동 해결.

---

## Related journey

이 plan 은 기존 journey 의 Known gap 이 아닌 **implementer 가 plan 구현 중 발견한 production crash** 에서 비롯됨. shipped 후:

- `docs/journeys/rules-management.md` Change log 에 "2026-04-2x: `smartnoti_rules` DataStore 단일 소유자 정리 — `LegacyRuleActionReader` 가 `RulesRepository.dataStore` 주입으로 이동. P1 회귀로 인한 launch crash 해소." 추가.
- `docs/journeys/onboarding-bootstrap.md` 도 "P1 migration runner 가 launch crash 없이 정상 수행" 으로 Observable steps 가 회복됨을 Change log 에 병기.

Plan #248 (Categories rollout implementer) 는 이 PR merge 후 재개. PR body 에 `Blocks: #248` 명시.
