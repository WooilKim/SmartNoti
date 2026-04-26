---
status: shipped
shipped: 2026-04-26
superseded-by: docs/journeys/rules-management.md
---

# Rules — Bulk Assign Unassigned Rules to a Category

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** RulesScreen 의 "미분류" 영역 (`작업 필요` + `보류` 두 sub-section) 에 N 건이 쌓인 사용자가, 카드별로 시트를 N 번 거치지 않고 **여러 미분류 Rule 을 한 번에 한 분류로 합류** 시킬 수 있다. 한 sub-section 안에서 multi-select 모드 → Category pick → 단일 sheet → 단일 snackbar (`"규칙 N개를 '<카테고리명>' 분류에 추가했어요"`) 로 흐름이 끝나고, 선택된 모든 Rule 의 `draft` flag 가 false 로 flip 된다. 작업 필요/보류 sub-section 의 두 헤더 + 각 row + classifier 동작 (`Rule orphan → SILENT fall-through`) 의 기존 계약은 변경 없음.

**Architecture:** 기존 `AssignRuleToCategoryUseCase.assign(ruleId, categoryId)` (Plan `2026-04-26-rule-explicit-draft-flag.md` Task 3) 을 N 건 ordered loop 로 호출하는 신규 `BulkAssignRulesToCategoryUseCase` 를 도메인 layer 에 추가. UI 는 `RulesScreen` 의 미분류 sub-section row 에 long-press → multi-select 모드 진입을 추가하고, AppBar 영역에 `MultiSelectActionBar` (선택 카운트 + "분류 추가" CTA + 취소) 를 띄운다. "분류 추가" 탭 시 기존 `CategoryAssignBottomSheet` 를 selectedRuleIds 컨텍스트로 재오픈 (기존 single-rule sheet 코드를 변경하지 않고, 호출 사이트에서 `pendingBulkRuleIds: List<String>` 를 분기). Sheet 의 "기존 분류에 포함" 행 탭 → `BulkAssignRulesToCategoryUseCase.assign(ruleIds, categoryId)` → snackbar. "새 분류 만들기" terminal row 도 prefill 흐름 (CategoryEditorPrefill 로 selectedRuleIds 를 동봉) 으로 동일 use case 를 거치도록 wiring. 두 sub-section 간 cross-section 선택은 막는다 (각 모드는 sub-section local — UX 단순화).

**Tech Stack:** Kotlin, Jetpack Compose Material3, DataStore (`smartnoti_rules` / `smartnoti_categories`), Gradle JVM unit tests.

---

## Product intent / assumptions

- 현 흐름은 미분류 N 건 처리에 N 회의 sheet open + N 회의 Category 선택을 강요한다. 사용자가 quick-start 셀렉션 후 추가로 만든 keyword/sender Rule 이 누적되면 (rules-management Known gap 의 마지막 bullet — "**bulk 미분류 분류** 미지원") drainage 가 사실상 멈춘다.
- 다음 결정은 implementer 에게 맡기지 않고 plan 단계에서 고정한다:
  - **Multi-select scope**: "작업 필요" 와 "보류" 는 **별도 모드** 로 운용. 사용자가 보류 row 를 의도적으로 보류했다면 같은 일괄 액션에서 작업 필요 와 섞어 처리하면 의도가 흐려진다.
  - **Bulk assign 후 draft flip**: selected Rule 모두 `draft=false` 로 flip. "보류" sub-section 에서 bulk assign 한 row 도 합류와 함께 draft=false 가 정상 (이미 false 였으므로 no-op storage) — 두 sub-section 모두 동일한 use case 경로.
  - **Snackbar 카피 단일화**: 경로 A (기존 분류) `"규칙 ${N}개를 '${categoryName}' 분류에 추가했어요"`, 경로 B (새 분류) `"새 분류 '${categoryName}' 만들고 규칙 ${N}개를 추가했어요"`. 0 건 합류는 sheet 진입 자체가 차단되므로 발생 불가.
- **남는 open question (user 결정 필요)**: 새 분류 만들기 경로 B 에서 editor `defaultAction` 의 seed 값 — 단일 rule 시트는 현재 Category action 의 dynamic-opposite 를 쓰지만, bulk 케이스에는 "현재 Category" 가 없다. 두 후보:
  1. `defaultAction = PRIORITY` (사용자가 명시적으로 다중 합류시키려는 의도는 보통 "묶어서 중요로 보겠다" 가 주류라는 가설)
  2. `defaultAction = DIGEST` (보수적 default — 보류함 출신은 보통 덜 급한 알림)
  본 plan 의 Risks 절에 남기고, implementer 가 단일 Rule 경로의 dynamic-opposite 가 무엇이었는지 확인한 뒤 **PR 본문에서 사용자에게 결정 요청**.

---

## Task 1: Add failing tests for `BulkAssignRulesToCategoryUseCase` [IN PROGRESS via PR #382]

**Objective:** 새 use case 의 계약을 unit test 로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/BulkAssignRulesToCategoryUseCaseTest.kt`

**Steps:**
1. Fake `RulesGateway` (markRuleAsAssigned 호출 record) + Fake `CategoriesGateway` (appendRuleIdToCategory 호출 record) 작성. `AssignRuleToCategoryUseCaseTest` 의 fake 패턴 그대로 재사용.
2. 케이스 작성:
   - **happy path**: `assign(ruleIds=[r1, r2, r3], categoryId="cat-X")` 호출 시 두 gateway 가 ordered call 을 받음 — 각 ruleId 별로 `appendRuleIdToCategory(cat-X, rN)` → `markRuleAsAssigned(rN)` 순서, 그 다음 ruleId 로 진행. 즉 per-rule append-then-flip ordering 이 N 회 반복됨을 record 비교로 검증.
   - **idempotent**: 같은 ruleId 가 list 에 두 번 들어와도 use case 는 두 번 호출하지 않고 distinct 처리 후 단일 호출. (구현은 `ruleIds.distinct()` 한 줄.)
   - **empty list**: `assign(ruleIds=[], "cat-X")` → gateway 는 한 번도 호출되지 않음 (early return).
   - **partial failure mid-loop**: 두 번째 ruleId 의 `appendRuleIdToCategory` 가 throw 하면 use case 가 throw 를 propagate, 첫 번째 ruleId 의 두 op (append+flip) 는 이미 commit 된 상태로 유지 (use case 는 transaction 책임 없음 — 단일 Rule 경로와 동일 정책). assertion: 첫 ruleId 의 두 record 가 존재, 두 번째 ruleId 의 append record 만 시도되고 flip record 는 없음, throw 가 caller 까지 올라옴.
3. `./gradlew :app:testDebugUnitTest --tests "*.BulkAssignRulesToCategoryUseCaseTest"` → RED 확인.

## Task 2: Implement `BulkAssignRulesToCategoryUseCase` [IN PROGRESS via PR #382]

**Objective:** Task 1 의 테스트를 GREEN 으로.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/BulkAssignRulesToCategoryUseCase.kt`

**Steps:**
1. 단일 `AssignRuleToCategoryUseCase` 를 생성자로 받는 thin wrapper 로 구현 (use case → use case composition). 이렇게 하면 ordered append-then-flip 계약을 단일 진입점에 위임할 수 있고, 단일 경로 회귀 위험이 없다.
2. `suspend fun assign(ruleIds: List<String>, categoryId: String)` 는: `if (ruleIds.isEmpty()) return` → `ruleIds.distinct().forEach { single.assign(it, categoryId) }`. 한 build 에서 catch 없이 throw 그대로 위로.
3. companion `create(rulesRepository, categoriesRepository)` factory 를 단일 use case 와 같은 형태로 추가.
4. `./gradlew :app:testDebugUnitTest --tests "*.BulkAssignRulesToCategoryUseCaseTest"` → GREEN.

## Task 3: Add failing tests for `RulesScreenMultiSelectState`

**Objective:** Compose 외부의 pure state 로 multi-select 의사결정을 분리해 회귀 비용을 낮춘다.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/rules/RulesScreenMultiSelectStateTest.kt`

**Steps:**
1. `RulesScreenMultiSelectState` 의 의도된 모델: `enum Bucket { ACTION_NEEDED, PARKED }` + `data class State(val activeBucket: Bucket?, val selectedRuleIds: Set<String>)`. 초기값 `State(activeBucket = null, selectedRuleIds = emptySet())`.
2. 케이스:
   - `enterSelection(bucket, seedRuleId)` → activeBucket = bucket, selectedRuleIds = setOf(seedRuleId).
   - `toggle(ruleId)` → 같은 bucket 의 row 는 add/remove. 마지막 한 건 remove 시 자동 cancel (state 가 초기값으로 reset).
   - `toggle` 가 cross-bucket 호출이면 무시 (state 불변).
   - `cancel()` 은 항상 초기값으로 reset.
   - `isInSelectionMode(bucket)` 은 activeBucket 비교 단순 헬퍼.
3. `./gradlew :app:testDebugUnitTest --tests "*.RulesScreenMultiSelectStateTest"` → RED.

## Task 4: Implement `RulesScreenMultiSelectState`

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreenMultiSelectState.kt`

**Steps:**
1. Pure Kotlin data class + 메서드 (Compose 의존 없음). `copy(...)` 기반의 fluent 메서드.
2. `./gradlew :app:testDebugUnitTest --tests "*.RulesScreenMultiSelectStateTest"` → GREEN.

## Task 5: Wire multi-select gestures + selection chrome into RulesScreen

**Objective:** UnassignedRuleRowSlot 에 long-press → multi-select 진입, 같은 sub-section row 탭으로 toggle, AppBar 영역에 selection 액션 노출.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`
- 필요 시 신규 `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesMultiSelectActionBar.kt`

**Steps:**
1. `RulesScreen` 에 `var multiSelectState by remember { mutableStateOf(RulesScreenMultiSelectState()) }` 추가.
2. `UnassignedRuleRowSlot` 호출부에 새 콜백 두 개 전달:
   - `onLongPress: () -> Unit` — `multiSelectState.activeBucket == null` 일 때만 활성화. 호출 시 `enterSelection(bucket, rule.id)`.
   - `onSelectionTap: () -> Unit` — `multiSelectState.activeBucket == bucket` 일 때 row 탭이 sheet 오픈 대신 toggle 로 fall-through 한다. 두 분기는 row tap modifier 한 곳에서 분기.
3. 두 sub-section (`작업 필요` / `보류`) 의 LazyColumn `items` 안에서 selected 상태인 row 는 visual highlight (border accent + leading checkbox) — 기존 `isHighlighted` 와 별개의 `isSelected` flag 추가. `UnassignedRuleRowSlot` 시그니처에 `isSelected: Boolean = false` 추가.
4. `multiSelectState.activeBucket != null` 일 때 화면 상단 (`ScreenHeader` 아래) 에 `RulesMultiSelectActionBar` mount: `"${selectedRuleIds.size}개 선택됨"` + `OutlinedButton("분류 추가")` + `TextButton("취소")`. "분류 추가" 탭 → 새 state `pendingBulkAssignmentRuleIds: List<String>` 설정. "취소" 탭 → `multiSelectState = RulesScreenMultiSelectState()`.
5. 기존 single-rule `pendingCategoryAssignmentRuleId` 분기는 그대로 유지 — bulk 진입은 별도 state (`pendingBulkAssignmentRuleIds`) 로 분리해 sheet 호출부에서 분기. `CategoryAssignBottomSheet` 자체는 변경 금지.

## Task 6: Wire `BulkAssignRulesToCategoryUseCase` + snackbar at the sheet call site

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/rules/RulesScreen.kt`

**Steps:**
1. `pendingBulkAssignmentRuleIds != null` 일 때 RulesScreen 의 기존 sheet 호출부 (single-rule 흐름과 동일한 컴포저블) 가 같은 `CategoryAssignBottomSheet` 를 띄우되, `onAssignToExisting(categoryId)` 콜백은 single-rule path 가 아닌 `bulkAssignUseCase.assign(pendingBulkAssignmentRuleIds, categoryId)` 를 호출하도록 분기.
2. Bulk assign 성공 후: snackbar host 에 `"규칙 ${N}개를 '${selectedCategoryName}' 분류에 추가했어요"` enqueue. snackbar duration `Short`. 기존 categories Flow 에서 categoryId → name 을 라이브 lookup; null 이면 fallback 카피 `"규칙 ${N}개를 분류에 추가했어요"`.
3. Bulk assign 완료 후 state 정리: `multiSelectState = RulesScreenMultiSelectState()`, `pendingBulkAssignmentRuleIds = null`. 다음 recomposition 에 selected row highlight + ActionBar 사라짐.
4. "새 분류 만들기" terminal row 탭 → `CategoryEditorPrefill` 의 `seedRuleIds` 필드에 `pendingBulkAssignmentRuleIds` 를 동봉, editor `onSaved(category)` 가 `bulkAssignUseCase.assign(seedRuleIds, category.id)` 호출 후 snackbar `"새 분류 '${category.name}' 만들고 규칙 ${N}개를 추가했어요"`. (Editor 의 prefill 확장 한 줄 — 단일 ruleId path 의 해당 분기를 list 로 일반화.)
5. Editor cancel 시 (Path B cancel) snackbar 미등장 + multiSelectState 유지 (사용자가 다시 sheet 열어 다른 분류를 고를 수 있도록). 단, 사용자가 명시적으로 ActionBar "취소" 를 탭한 경우만 selection clear.

## Task 7: Update journey doc

**Files:**
- `docs/journeys/rules-management.md`

**Steps:**
1. Observable steps 의 step 6 ("미분류 sheet 흐름") 뒤에 step 6a 추가 — long-press 진입 → ActionBar → "분류 추가" → bulk sheet → 두 경로 (기존/신규) snackbar.
2. Known gaps 의 마지막 bullet (`**bulk 미분류 분류** 미지원`) 를 resolved 마커 + plan 링크로 교체 (gap 본문은 보존, 앞에 `(resolved YYYY-MM-DD, plan 2026-04-26-rules-bulk-assign-unassigned) ` 마커 prefix + 끝에 `→ plan: ` 링크).
3. Change log 에 새 row 1줄 (구현 PR 머지 시 commit 해시 채움).

## Task 8: ADB end-to-end verification

**Objective:** 실제 emulator 에서 두 경로 모두 동작 확인.

**Steps:**
```bash
# 사전: emulator-5554 가 실행 중, SmartNoti debug APK 설치, listener 권한 grant.

# 0. baseline reset — 기존 미분류 row 정리. Settings 진입 → Rules 화면 →
#    필요 시 "보류" / "작업 필요" 의 기존 row 삭제하여 깨끗한 상태로 시작.

# 1. seed 4 unassigned rules ("작업 필요" sub-section 으로 들어가도록
#    sheet "작업 목록에 두기" 경로 사용):
for i in 1 2 3 4; do
  # rule 추가 → sheet 가 뜨면 "작업 목록에 두기" 탭 (좌표는 device 별 측정).
  echo "manual step: Rules 화면 → 새 규칙 추가 → 키워드=BulkAsgnTest$i / 매치값=bulkasgnkw$i / 저장 → '작업 목록에 두기'"
done

# 2. 첫 번째 row long-press → multi-select 진입 + "1개 선택됨" ActionBar 노출 확인.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb shell cat /sdcard/ui.xml | grep -E '선택됨|분류 추가|취소'

# 3. 나머지 3 row 탭 toggle → "4개 선택됨" 갱신 확인.

# 4. ActionBar "분류 추가" 탭 → CategoryAssignBottomSheet 오픈 → "중요 알림" 탭.

# 5. 기대값:
#    - sheet dismiss
#    - snackbar "규칙 4개를 '중요 알림' 분류에 추가했어요"
#    - "작업 필요" sub-section 사라짐 (4개 모두 합류 → "즉시 전달" 그룹으로 이동)
#    - DataStore: smartnoti_categories 의 중요 알림 ruleIds 에 4개 ID append,
#      smartnoti_rules 의 4개 row 의 draft column = false.
adb -s emulator-5554 exec-out run-as com.smartnoti.app cat \
  files/datastore/smartnoti_categories.preferences_pb | strings | grep BulkAsgn

# 6. 경로 B (새 분류) 검증 — 새 4 rule 다시 seed → multi-select → "분류 추가"
#    → "새 분류 만들기" → editor name="BulkNewCat" 저장 → snackbar
#    "새 분류 'BulkNewCat' 만들고 규칙 4개를 추가했어요" 확인.

# 7. 경로 B cancel — 5번째 rule seed → multi-select 1건 → "분류 추가"
#    → "새 분류 만들기" → editor cancel → snackbar 미등장 + ActionBar 유지 확인.

# 8. baseline 복원 — 검증용 신규 Rule + Category 모두 삭제.
```

---

## Scope

**In:**
- 신규 `BulkAssignRulesToCategoryUseCase` + 단위 테스트 4 케이스.
- 신규 `RulesScreenMultiSelectState` (pure) + 단위 테스트 4 케이스.
- `RulesScreen` 에 multi-select 진입 (long-press) + ActionBar + 선택 highlight.
- 기존 `CategoryAssignBottomSheet` 호출부 분기로 bulk 경로 합류.
- `CategoryEditorPrefill` 의 seedRuleIds list 일반화 (단일 → 리스트, 단일 호출부는 size=1 list 로 마이그레이션).
- snackbar 카피 두 종 + 카운트 + Category name lookup.
- `docs/journeys/rules-management.md` 의 Observable step 6a 추가 + Known gap resolved 마커 + Change log row.

**Out:**
- 작업 필요 ↔ 보류 cross-section bulk 합류 (의도적 단순화).
- bulk delete / bulk enable-toggle (필요해지면 별도 plan).
- bulk assign 의 undo snackbar (단일 rule 경로도 미지원이므로 본 plan 도 포함하지 않음).
- 분류 그룹 (PRIORITY/DIGEST/SILENT/IGNORE) 사이드의 multi-select (이쪽은 `Category` 가 owning entity 이므로 다른 use case 가 필요).

---

## Risks / open questions

- **경로 B editor 의 `defaultAction` seed 값** — 단일 Rule 경로는 "현재 Category action 의 dynamic-opposite" 를 사용하지만 bulk 케이스에는 source Category 가 없다. 위 Product intent 절의 두 후보 (PRIORITY 우선 vs DIGEST 보수) 중 선택을 PR 본문에서 사용자에게 묻는다. 결정 전까지 implementer 는 한 가지를 가구현 (PRIORITY) 하고 PR 설명에 "쉽게 변경 가능" 명시.
- **Editor `CategoryEditorPrefill` 의 seedRuleIds 시그니처 변경 영향 범위** — 현재 단일 rule path (rules-feedback-loop / Detail "새 분류 만들기") 가 같은 prefill 데이터를 사용. list 로 일반화하면 단일 호출부 두 곳도 동시에 마이그레이션 필요. 변경량은 작지만 implementer 가 두 호출부를 같은 PR 에 포함해야 한다.
- **Long-press 충돌** — `UnassignedRuleRowSlot` 가 이미 다른 long-press handler 를 가지고 있다면 multi-select 진입과 충돌. implementer 가 Task 5 step 1 직전에 호출부 grep 으로 확인 + 충돌 시 본 plan 보류 + comment.
- **ActionBar mount 위치** — `ScreenHeader` 와 LazyColumn 사이에 sticky 영역으로 둘지, Floating 으로 띄울지는 ui-improvement.md 의 "ScreenHeader / SectionLabel 일관성" 측면에서 sticky 권장. 단, scroll behavior 에 따라 사용자가 ActionBar 아래에서 row 탭을 놓치지 않도록 padding 은 implementer 가 emulator 에서 미세 조정.
- **0건 selection state** — Task 4 의 `toggle` 마지막 한 건 remove 시 auto-cancel 정책이 사용자 의도와 어긋날 수 있다 (사용자가 잘못 toggle 한 뒤 다시 toggle 하려고 했는데 mode 가 자동 종료). 본 plan 은 단순화 우선 — 사용자가 다시 long-press 로 진입. 불편하면 후속 polish.

---

## Related journey

- [`docs/journeys/rules-management.md`](../journeys/rules-management.md) — 본 plan 이 해소하는 Known gap: 마지막 bullet `**bulk 미분류 분류** 미지원 — 미분류 Rule N 개를 한 분류에 한꺼번에 할당하는 흐름은 현재 없음. 한 건씩 sheet 를 거쳐야 함. 후속 plan.`
