---
status: shipped
created: 2026-04-22
updated: 2026-04-22
superseded-by: ../journeys/rules-feedback-loop.md
---

# CategoryEditor Save Wiring Fix — "추가" 가 persist 되도록

> **For Hermes:** Use subagent-driven-development skill. RED tests first for both editor entry points (Detail "새 분류 만들기" prefill path AND standalone 분류 tab path). Then trace the save race — strong hypothesis: `rememberCoroutineScope()` in `CategoryEditorScreen` is cancelled by the synchronous `onSaved(...)` teardown before the DataStore write completes. Do not declare GREEN until `smartnoti_categories.preferences_pb` is observed on disk after ADB save, for *both* entry paths.

**Goal:** 사용자가 Detail 의 "분류 변경 → 새 분류 만들기" 또는 분류 탭의 "새 분류 추가" 로 CategoryEditor 를 열어 "추가" 를 탭하면, 신규 Category (및 prefill path 에서는 동반 Rule) 가 DataStore 에 실제로 persist 되고, 재시작 후에도 분류 목록에 남아 있으며, 동일 sender/앱의 후속 알림이 그 Category 의 action 으로 분류된다. 현재는 "추가" 탭이 UI 상 닫히지만 `smartnoti_categories.preferences_pb` 가 아예 생성되지 않아 Category 가 영속되지 않는다.

**Architecture:**
- Suspect #1 (가장 유력): `CategoryEditorScreen` 의 save onClick 는 `rememberCoroutineScope().launch { rulesRepository.upsertRule(...); categoriesRepository.upsertCategory(...) }` 로 비동기 persist 를 띄우고, **같은 블록의 다음 줄에서 동기적으로 `onSaved(persisted.id)` 를 호출**한다. Detail 경로 (`NotificationDetailScreen`) 의 `onSaved = { pendingPrefill = null }` 과 분류 탭 경로 (`CategoriesScreen`) 의 `onSaved = { editorTarget = null; ... }` 은 둘 다 호출 즉시 editor 를 composition 에서 제거한다. composition 이탈 → `rememberCoroutineScope` 취소 → DataStore `edit { }` 블록이 실행 전에 잘려 preferences_pb 가 생성되지 않는다.
- Fix 1안: save onClick 을 `suspend fun saveCategory()` 로 묶고 `scope.launch { try { persist...; onSaved(id) } }` 순서로 await 후 콜백. scope 는 여전히 editor 의 것이지만 **persist 완료 후** 콜백이 composition 을 tear down 하므로 race 가 사라진다.
- Fix 2안: persist 로직을 editor 밖 (caller 가 소유한 viewModelScope 또는 Application scope) 으로 끌어올린다. Editor 는 draft 를 조립해서 `onSaved(DraftCategory)` 로 넘기고, caller 가 suspend 보존된 scope 에서 persist. 가장 깔끔하지만 편집 경로 (Edit target) 의 repository 호출 패턴도 함께 옮겨야 하므로 범위가 커진다.
- 구현 task 에서는 **1안을 기본**으로 채택하되, `NotificationDetailScreen` 의 prefill persist 가 아직 composition-bound scope 에 남아 있으면 동일 패턴으로 스코프를 `CoroutineScope(Dispatchers.Default + SupervisorJob)` 이나 viewModel/Service 수준으로 격상하는 옵션을 오픈으로 남긴다.

**Tech Stack:** Kotlin coroutines, Jetpack Compose, DataStore Preferences, Gradle unit tests + Robolectric (또는 `runTest` + in-memory fake repos).

---

## Product intent / assumptions

- **새 Category 생성은 Categories rollout 의 주 onboarding 경로** — Detail 에서 "이 알림은 새 분류" 를 만드는 것이 대부분의 사용자 첫 체험. 여기가 깨지면 전체 P2/P3 기능이 관측되지 않는다.
- **persist 성공 기준은 preferences_pb 파일 존재** — UI 상 dismiss 는 필요조건이지만 충분조건이 아니다. 테스트/검증은 저장 뒤 파일이 생성됐고 바이트가 Category proto 를 디코딩 가능한 payload 인지까지 본다.
- **양쪽 entry path 모두 동일 race 를 가진다고 가정** — 분류 탭 경로에서 실제로 persist 되어 왔는지도 의심해야 한다. tester 가 본 드리프트는 Detail 경로였지만, 두 경로 공통의 `scope.launch` 패턴을 공유하므로 테스트로 확인 필요.
- **Rule+Category 의 persist 순서는 Rule 먼저** — prefill path 에서 Category 의 `ruleIds` 가 참조하는 rule.id 가 Rules DataStore 에 먼저 써진 뒤 Category 가 저장돼야 classifier 가 dangling 을 만나지 않는다 (기존 코드 순서 유지).
- **취소/닫기 경로는 여전히 nothing-persisted** 를 유지 — "추가" 가 아닌 모든 경로는 DataStore 에 흔적이 남지 않아야 한다. Fix 가 실수로 dismiss 에도 persist 를 붙이지 않도록 테스트로 고정.

---

## Task 1: Failing tests — save button persists via both entry paths  [SHIPPED]

**Objective:** "추가" 탭 → DataStore 에 Category (및 prefill path 의 동반 Rule) 가 실제 쓰이는지를 두 entry path 모두에 대해 red 로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoryEditorSaveWiringTest.kt` — Compose UI 테스트 (Robolectric 또는 `createComposeRule`). 두 path 모두 커버.
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoryEditorSaveRaceTest.kt` — 순수 로직 테스트: save onClick 의 persist 호출이 `onSaved` 콜백보다 **먼저 완료**되는지 `TestDispatcher` 로 검증 (composition 을 흉내낸 취소 시나리오 포함).

**Steps:**
1. **Test A — standalone editor (분류 탭) persists new Category:**
   - Given 빈 CategoriesRepository + 빈 RulesRepository (fake or in-memory).
   - Compose `CategoryEditorScreen(target=New, categories=[], rules=[existingRule], capturedApps=[], onSaved={...})`.
   - 사용자 입력 시뮬레이션: `draftName="테스트분류"`, ruleIds 에 existingRule 포함, action=PRIORITY.
   - "추가" 클릭 → `advanceUntilIdle()` 후 `categoriesRepository.observeCategories().first()` 가 새 Category 1건을 반환해야 함.
   - Assert `onSaved(id)` 가 해당 id 로 정확히 한 번 호출됨.
2. **Test B — Detail prefill path persists Rule + Category:**
   - Given prefill `CategoryEditorPrefill(name="Alice", appPackageName=null, pendingRule=RuleUiModel(id="person:Alice", type=PERSON, matchValue="Alice"), defaultAction=PRIORITY)`.
   - 빈 레포 + `rules=[]`.
   - Compose with prefill, "추가" 클릭 → `advanceUntilIdle()`.
   - Assert: RulesRepository 에 `person:Alice` rule 저장됨. CategoriesRepository 에 Category 저장됨 + `ruleIds` 에 `person:Alice` 포함.
3. **Test C — save race: onSaved 이 persist 완료를 기다린다:**
   - scope-cancel 시나리오를 `TestDispatcher` 로 재현: launch 된 persist 가 한 step 만 돌고, 그 뒤 호스트 composable 이 사라진다 (scope.cancel()) 고 가정.
   - 기존 코드에서는 upsert 가 미완료 → 실패.
   - Fix 후에는 persist 완료 후 `onSaved` 가 호출 → 테스트가 require 하는 순서대로 진행.
4. **Test D — cancel/dismiss 은 아무 것도 persist 하지 않는다:**
   - 사용자가 draft 를 채운 뒤 "닫기" 또는 back → RulesRepository / CategoriesRepository 모두 비어 있어야 함.
5. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.CategoryEditor*Test"` 로 A/B/C 실패 (혹은 A/B 중 분류 탭이 이미 우회적으로 동작하면 B/C 만 실패) 확인.

## Task 2: Fix the save coroutine race  [SHIPPED]

**Objective:** persist 가 `onSaved` 호출 *이전에* 완료되게 재배선.

**Files:**
- 변경: `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorScreen.kt`
- 필요 시 변경: `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt` — prefill path 의 `onSaved = { pendingPrefill = null }` 호출 시점도 동일 원칙 (persist 완료 후) 을 따르도록. Editor 쪽에서 순서를 보장하면 caller 는 변경 불필요.

**Steps:**
1. Save onClick 의 `scope.launch { ... }` 블록을 재배치:
   ```
   scope.launch {
       if (pendingRule != null && pendingRule.id in persisted.ruleIds) {
           rulesRepository.upsertRule(pendingRule)
       }
       categoriesRepository.upsertCategory(persisted)
       onSaved(persisted.id)   // ← launch 블록 안으로 이동
   }
   ```
2. `onClick` 자체는 비어 있는 trailing 으로 남기거나 disable 토글 (중복 탭 방지) 만 남긴다. Coroutine 바깥에서 `onSaved` 호출하지 않는다.
3. scope 는 여전히 composable 것이므로, launch 된 코루틴이 첫 `upsert...` 의 DataStore `edit { }` 블록에 진입한 뒤에야 composition 이 이탈될 수 있도록 테스트로 확인. 만약 Compose 가 첫 suspension point 이전에 teardown 하면 (이론상 가능) scope 를 Application 수준으로 격상하는 2안 필요 — 이 경우 `CategoriesRepository.getInstance(context).applicationScope` 같은 seam 을 도입. Risks 섹션 참조.
4. 중복 탭 방지: `var saving by remember { mutableStateOf(false) }` + `enabled = canSave && !saving`, launch 시작 직전 `saving = true`.
5. Task 1 의 A/B/C 테스트 green.

## Task 3: ADB end-to-end verification + journey sync  [PARTIAL — blocked by orthogonal crash]

> **Implementer note (2026-04-22):** Tasks 1-2 shipped with single `CategoryEditorSaveOrchestratorTest.kt` (6 tests — standalone path persist, prefill rule-before-category ordering, scope-cancel ⇒ no onSaved, dismiss-is-noop, dedup, suspend-await). Journey Known-gap DRIFT bullet removed and Change log row added. The ADB verification recipe below could not be executed end-to-end because every fresh launch of `com.smartnoti.app` on `emulator-5554` crashes with `IllegalStateException: There are multiple DataStores active for the same file: …/smartnoti_rules.preferences_pb`, triggered by `LegacyRuleActionReader` (`data/categories/LegacyRuleActionReader.kt`) and `RulesRepository` (`data/rules/RulesRepository.kt`) both declaring `preferencesDataStore(name = "smartnoti_rules")` at the top level. This is a pre-existing P1 migration-plumbing defect (landed in #236), orthogonal to the save-wiring race this plan fixes. `last-verified` on rules-feedback-loop is deliberately NOT bumped. Follow-up plan should consolidate to a single DataStore handle.

**Objective:** 실제 디바이스에서 두 entry path 가 preferences_pb 를 쓰는지 관측하고, 관련 journey 의 Known-gap bullet 을 Change log 행으로 이관.

**Files:**
- 변경: `docs/journeys/rules-feedback-loop.md` — Known gaps 에 드리프트 bullet 이 있다면 Change log 로 이동 ("2026-04-XX: CategoryEditor 저장 race 수정, Detail "새 분류 만들기" 경로에서 persist 안 되던 드리프트 해소 (plan: ...)"). `last-verified` 는 이번 ADB recipe 를 돌린 날짜로만 bump.
- 변경: `docs/journeys/categories-management.md` — 관련 Known gap 있으면 동일 처리.
- 변경: 이 plan → `status: shipped` + `superseded-by: ../journeys/rules-feedback-loop.md`.

**Steps:**
```bash
# 1. 앱 data 클리어로 preferences_pb 부재 상태에서 시작
adb shell pm clear com.smartnoti.app
adb shell cmd notification post -S bigtext -t "CatSaveTest_0422" CatSave "테스트"

# 2. Detail 진입 → "분류 변경" → "새 분류 만들기" → 기본값 그대로 "추가"
# 3. preferences_pb 존재 확인
adb shell run-as com.smartnoti.app ls -la files/datastore/
# expected: smartnoti_categories.preferences_pb 가 목록에 있고 size > 0
adb shell run-as com.smartnoti.app cat files/datastore/smartnoti_categories.preferences_pb | od -c | head -20
# expected: Category id 키와 직렬화된 payload 바이트가 관측됨

# 4. 앱 재시작 후 분류 탭에 새 Category 보이는지 확인
adb shell am force-stop com.smartnoti.app
adb shell am start -n com.smartnoti.app/.MainActivity

# 5. 분류 탭 경로 회귀 테스트: 분류 탭 → "새 분류 추가" → 임의 이름 + 임의 rule → "추가"
#    → 목록에 즉시 보이고 앱 재시작 후에도 보이는지 확인

# 6. 후속 알림 자동 분류 검증
adb shell cmd notification post -S bigtext -t "CatSaveTest_0422" CatSave2 "두 번째"
# → Home StatPill / 해당 탭에서 신규 Category 의 action 으로 집계되는지 확인
```
- 모든 단계 PASS 후 journey 의 `last-verified` 를 오늘 날짜로 bump + Change log 추가.

---

## Scope

**In:**
- CategoryEditor save onClick 의 coroutine 순서 수정 (persist → onSaved).
- 두 entry path (Detail "새 분류 만들기" prefill / 분류 탭 "새 분류 추가") 의 persist 를 테스트로 고정.
- 중복 탭 방지 `saving` flag 추가.
- Journey Known-gap bullet → Change log 이관.

**Out:**
- Editor UI 리디자인 / 레이아웃 변경.
- Rule editor 의 유사 저장 경로 감사 (다음 plan 으로 분리 — 현재 드리프트는 Category 쪽에서만 관측).
- `CategoriesRepository.applicationScope` seam 도입 (2안) — Fix 1안이 충분하지 않을 때만 다음 plan 으로.
- 저장 완료 토스트/스낵바 등 확인 UI.
- Edit-existing Category 경로의 race (같은 scope 패턴이지만 이번 드리프트의 직접 증거 없음 — 테스트로 회귀만 잡음).

---

## Risks / open questions

- **Compose teardown 이 첫 suspension point 보다 빠르면 1안 부족:** `Button.onClick` 에서 `scope.launch` 가 스케줄되기만 하고 실제 `upsert...` 의 DataStore edit 에 들어가기 전에 composable 이 이탈하면 launch 자체가 즉시 취소될 수 있다. 이론상 가능성. Task 1 의 Test C 가 그 상황을 재현하므로, red 가 유지되면 Fix 2안 (Application scope seam) 로 전환. 그 경우 별도 plan 으로 분리하지 말고 본 plan Task 2 범위 내에서 해결 — 대신 리뷰어에게 2안으로 바꿨음을 알림.
- **분류 탭 경로는 실제로 persist 되고 있었는가:** tester 는 Detail 경로만 관측했고 분류 탭 경로의 현재 동작은 **가정**이다. Task 1 의 Test A 가 red 라면, 이 드리프트는 P1 시점부터 내내 존재했던 심각한 회귀. 그 경우 Known-gap 이관이 아니라 별도 Change log 에 "Categories 기능 자체가 persist 되지 않던 regression" 으로 기록해야 한다. 구현 시 판단.
- **`CoroutineScope.cancel` 중 DataStore edit 의 원자성:** 1안으로 고쳤을 때도 기기/프로세스 kill 같은 외부 취소로 edit 중간에 잘릴 가능성은 남는다. DataStore 는 commit-or-nothing 이 보장되므로 부분 쓰기 걱정은 없음. 실패 시 다음 save 시 다시 쓰이면 됨 — 재시도 UI 는 이 plan scope 밖.
- **중복 탭 방지 `saving` flag 해제:** persist 실패 (예외) 시 `saving=false` 로 복구해야 editor 가 잠기지 않는다. `try/finally` 또는 `runCatching` 으로 묶는다. 실패 알림 UI 는 이 plan scope 밖 (로그만 남김).
- **Robolectric vs instrumented test:** `CategoryEditorScreen` 은 `LocalContext.current` 로 `CategoriesRepository.getInstance(context)` 를 잡는다. Robolectric 에서 DataStore 는 실파일 sandbox 로 동작하므로 가능. 단, 테스트 격리를 위해 매 테스트마다 임시 디렉토리로 context 분리 필요. 기존 `CategoriesRepositoryTest` 의 setup 을 재사용.
- **Edit-existing 경로:** 같은 race 를 공유하지만 현재 드리프트 보고에는 없음. Task 1 의 Test A 를 Edit 경로도 커버하도록 파라미터화하면 동일 fix 로 회귀도 잡힌다 — 시간 허락 시 포함, 아니면 follow-up.

---

## Related journey

- [rules-feedback-loop](../journeys/rules-feedback-loop.md) — "분류 변경 → 새 분류 만들기" 경로의 persist race 로 Category 가 저장되지 않던 드리프트의 owner. Fix 가 shipped 되면 해당 journey 의 관련 Known-gap bullet 을 Change log 로 이관하고 이 plan 의 frontmatter 를 `status: shipped` + `superseded-by:` 링크로 갱신한다.
- [categories-management](../journeys/categories-management.md) — 분류 탭 "새 분류 추가" 경로의 persist 회귀가 Task 1 에서 확인되면 동일하게 Change log 이관.
