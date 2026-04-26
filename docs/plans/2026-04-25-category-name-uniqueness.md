---
status: planned
---

# Category 이름 고유성 검증 + Editor 저장 차단 Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 분류 (Category) 의 사용자 노출 이름이 의미 있게 고유해지도록, Editor 저장 단계에서 trim + case-insensitive 비교로 동일 이름 중복을 차단한다. 사용자는 Editor 의 이름 입력 필드 바로 아래에서 "이미 사용 중인 이름" 같은 inline 에러 카피를 보고, 저장 버튼이 비활성화된다 (현재 활성 상태인 두 게이트 — 이름 비어있지 않음, Rule 최소 1개 — 와 동일한 단일 `enabled` 분기). 자기 자신을 편집하는 경우는 자기 id 와 같은 Category 는 충돌로 간주하지 않는다. Detail "새 분류 만들기" Path B 의 prefill (sender 이름 자동 채움) 도 동일 게이트를 통과하므로 사용자가 동일 sender 로 두 번째 Category 를 만들려 할 때 editor 안에서 즉시 차단된다.

**Architecture:**
- 신규 pure 함수 `CategoryNameUniqueness` (object 또는 class) 가 `(name, currentCategoryId, existingCategories)` 를 받아 `NameStatus` (`OK` / `EMPTY` / `DUPLICATE`) 를 반환. 비교는 `name.trim()` + `equals(other, ignoreCase = true)`. `currentCategoryId` 가 비어있지 않으면 본인 row 는 충돌 후보에서 제외 (Edit 흐름에서 본인 이름을 그대로 두고 다른 필드만 바꾸는 케이스를 깨지 않기 위해).
- `CategoryEditorDraftValidator.canSave(...)` 시그니처 확장: 기존 `(name, selectedRuleIds)` 에 `(existingNames: List<Pair<String, String>>, currentCategoryId: String?)` 같은 파라미터를 추가하거나, 별도 `nameAvailable(...)` 메서드를 노출해 `canSave` 가 그것을 forward 호출하게 한다 (구현자가 더 깔끔한 쪽 결정 — 기존 호출 사이트 1곳 + Test 1곳만 영향).
- `CategoryEditorScreen` 의 이름 `OutlinedTextField` 에 `isError = (status == DUPLICATE)` + `supportingText = { Text("이미 사용 중인 이름이에요") }` (DUPLICATE) 또는 빈 helper 를 surface. 저장 버튼 `enabled` 는 기존처럼 validator 의 단일 boolean 결과만 본다.
- `MainActivity` / `AppNavHost` 의 Editor 호출 사이트는 이미 `categories` 리스트를 props 로 전달 중 (line 71). 별도 wiring 불필요.

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`OutlinedTextField` `isError` + `supportingText`), JUnit unit tests.

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `categories-management` 의 Known gap 첫 bullet 라인 "Category 이름 고유성 미보장 (id 가 primary key 이므로 중복 이름 허용)" 은 사용자가 직접 본문에 등록한 항목으로, "id 가 primary key" 를 깨지 않으면서 이름 중복만 표면 차단하는 것이 본 plan 의 범위.
- **결정 필요**: 비교 방식 — 본 plan 은 `trim()` + case-insensitive 비교로 채택. 이유: "프로모션" / " 프로모션" / "프로모션 " 같은 공백 변종은 사용자에게 같은 이름으로 보임. 한글에는 case 차이가 없지만 영어 카테고리 (예: "Work" / "work") 도 같은 이름으로 본다. 다른 정규화 (NFC/NFD 유니코드 정규화, 대시 등) 는 over-engineering 이므로 out.
- **결정 필요**: 충돌 발견 시 사용자 액션 — 본 plan 은 inline 에러 + 저장 차단만 제공. "기존 분류 열기" 같은 redirect 액션은 후속 plan. Editor 가 이미 dialog 라 새 화면을 띄우기 부담스럽고, 이름 충돌이 거의 user typo 수준이라 inline 정보만 줘도 충분하다고 가정.
- **결정 필요**: Path B prefill (Detail "새 분류 만들기") 가 같은 이름의 기존 Category 를 노출했을 때 — Editor 가 즉시 DUPLICATE 상태로 마운트되고 저장이 차단된다. 사용자는 이름을 바꾸거나 Editor 를 닫고 Path A ("기존 분류에 포함") 로 넘어가야 한다. 이 흐름이 친절하지 않다고 판단되면 Editor 마운트 직후 한 번만 prefill 이름이 충돌하면 자동으로 `"<name> 2"` suffix 를 붙이는 휴리스틱이 가능하지만 — 휴리스틱은 사용자 의도가 아닌 계산 결과를 강제하므로 본 plan 에서는 채택하지 않는다. Open question.
- **결정 필요**: 마이그레이션 — 이미 영속화된 중복 이름 Category 는 본 plan 에서 강제 rename 하지 않는다 (사용자 데이터를 자동 수정하지 않음). Editor 를 다음에 열어 저장하려 할 때 비로소 차단된다. `RuleToCategoryMigration` 결과로 `이름="알림"` Category 가 둘 이상 생긴 사용자 환경이 있다면 후속 cleanup plan 이 필요할 수 있음 — 본 plan 의 검증 단계에서 한 사용자 케이스라도 발견되면 Risks 에 보고.
- **결정 필요**: Path B 가 자기 자신의 `id` 를 아직 모르는 신규 흐름 — `currentCategoryId = null` 로 호출. 자기 자신 제외 로직은 Edit 흐름에서만 활성화되므로 신규 케이스는 모든 기존 Category 와 비교한다 (의도된 엄격함).
- 본 plan 은 Compose 의존성을 validator 에 주입하지 않는다 — `CategoryNameUniqueness` 는 순수 Kotlin 으로 unit-test 한다. Editor 는 결과를 string copy 로 surface 하는 책임만 진다.
- 카피/접근성: `OutlinedTextField` 의 `supportingText` 는 자동으로 TalkBack 에 announced 됨 — 추가 contentDescription 필요 없음.

---

## Task 1: Add failing tests for `CategoryNameUniqueness`

**Objective:** 비교 규칙 (trim, case-insensitive, 자기 id 제외) 을 unit test 로 고정한 뒤 구현. 테스트 표면이 가장 명확한 곳부터 짠다.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoryNameUniquenessTest.kt`

**Steps:**
1. 다음 시나리오를 assertion 으로 작성:
   - 빈 이름 → `EMPTY`.
   - 공백만 있는 이름 (`"   "`) → `EMPTY`.
   - 기존 Category 가 비어 있을 때 임의 이름 → `OK`.
   - 기존에 `"프로모션"` 이 있고 새 입력이 `"프로모션"` → `DUPLICATE`.
   - 기존에 `"프로모션"` 이 있고 새 입력이 `" 프로모션 "` → `DUPLICATE` (trim).
   - 기존에 `"Work"` 가 있고 새 입력이 `"work"` → `DUPLICATE` (case-insensitive).
   - 기존에 `"중요"` 가 있고 새 입력이 `"중요알림"` → `OK` (substring 은 충돌 아님).
   - Edit 흐름: `currentCategoryId = "cat-promo"`, 기존 `cat-promo:"프로모션"` + `cat-other:"광고"`, 새 입력 `"프로모션"` → `OK` (자기 자신 제외).
   - Edit 흐름: `currentCategoryId = "cat-promo"`, 기존 `cat-promo:"프로모션"` + `cat-other:"광고"`, 새 입력 `"광고"` → `DUPLICATE` (다른 row 와 충돌).
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.CategoryNameUniquenessTest"` → 모두 RED 확인 (구현이 아직 없음).

## Task 2: Implement `CategoryNameUniqueness`

**Objective:** Task 1 의 테스트가 모두 GREEN 이 되게.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryNameUniqueness.kt`

**Steps:**
1. 다음 형태로 작성:
   ```
   enum class CategoryNameStatus { OK, EMPTY, DUPLICATE }

   object CategoryNameUniqueness {
       fun evaluate(
           candidate: String,
           currentCategoryId: String?,
           existing: List<Pair<String, String>>, // (id, name)
       ): CategoryNameStatus { ... }
   }
   ```
2. 비교 로직:
   - `val trimmed = candidate.trim()`. `if (trimmed.isEmpty()) return EMPTY`.
   - `val collision = existing.any { (id, name) -> id != currentCategoryId && name.trim().equals(trimmed, ignoreCase = true) }`.
   - `if (collision) return DUPLICATE else return OK`.
3. `existing` 인자 형태가 `Pair<String, String>` 가 아닌 `List<Category>` 인 편이 호출 사이트에서 더 자연스럽다고 implementer 가 판단하면 그렇게 바꿔도 무방 — pure validation 의 contract 만 유지. 결정은 Task 3 wiring 이 가장 깔끔한 형태로 implementer 가 선택. Test 도 그에 맞춰 변경.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.CategoryNameUniquenessTest"` → 모두 GREEN.

## Task 3: Wire uniqueness into `CategoryEditorDraftValidator` + Editor

**Objective:** Editor 저장 버튼이 DUPLICATE 시 비활성화되고, 이름 필드 아래 inline 에러 카피가 표시된다.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorDraftValidator.kt`
- `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoryEditorDraftValidatorTest.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorScreen.kt`

**Steps:**
1. `CategoryEditorDraftValidator` 두 가지 옵션 — implementer 가 더 깔끔한 쪽 채택:
   - **옵션 A (단일 메서드 확장)**: `canSave(name, selectedRuleIds, existing, currentCategoryId)` 시그니처. 호출 사이트가 한 줄로 짧아짐.
   - **옵션 B (메서드 분리)**: `canSave(name, selectedRuleIds)` 는 유지하고 별도 `nameAvailable(name, existing, currentCategoryId): Boolean` 추가. Editor 에서 두 함수를 `&&` 로 결합해 `enabled` 산출. 기존 호출 사이트 / 테스트가 안 깨짐.
   - PR 본문에 어느 옵션을 골랐고 왜인지 한 줄 명시.
2. `CategoryEditorDraftValidatorTest` 에 옵션에 맞춰 시나리오 추가:
   - 새 흐름 + 중복 이름 → `canSave = false`.
   - 새 흐름 + 고유 이름 + Rule 1개 → `canSave = true`.
   - Edit 흐름 + 본인 이름 그대로 → `canSave = true`.
   - Edit 흐름 + 다른 Category 와 같은 이름 → `canSave = false`.
3. `CategoryEditorScreen.kt`:
   - 호출 사이트에서 `editingCategory?.id` 를 `currentCategoryId` 로, `categories` 리스트를 `existing` 으로 forward.
   - `OutlinedTextField` 에 다음 두 가지 추가:
     ```
     isError = nameStatus == CategoryNameStatus.DUPLICATE,
     supportingText = {
         if (nameStatus == CategoryNameStatus.DUPLICATE) {
             Text("이미 사용 중인 이름이에요", color = MaterialTheme.colorScheme.error)
         }
     },
     ```
   - `nameStatus` 는 `remember(draftName, categories, editingCategory)` 로 매 입력마다 재계산 — pure 함수라 비용 무시 가능.
   - 저장 버튼 `enabled` 는 새 `canSave` 결과 하나만 본다 (DUPLICATE 도 자동 차단).
4. 카피 한국어 단일 어조 유지 — 기존 카피 톤 (`"이미 사용 중인 이름이에요"`) 와 일관. 다국어는 본 plan scope 외.
5. `./gradlew :app:testDebugUnitTest` 전체 GREEN. `./gradlew :app:assembleDebug` 통과.

## Task 4: Manual ADB verification on emulator-5554

**Objective:** 두 흐름 (분류 탭 → FAB → 신규 / Detail → "새 분류 만들기" Path B) 에서 사용자가 중복 이름 입력 시 inline 에러를 보고 저장 버튼이 비활성화되는지 시각 확인.

**Steps:**
1. APK 빌드 후 설치: `./gradlew :app:installDebug`.
2. **분류 탭 신규 흐름**:
   - 분류 탭 → FAB "새 분류 만들기" 탭 → 이름 칸에 기존 Category 와 동일한 이름 입력.
   - inline 에러 "이미 사용 중인 이름이에요" 가 이름 칸 바로 아래에 빨간색으로 보이는지 uiautomator dump + screenshot 캡처.
   - 저장 버튼이 disabled (회색) 인지 확인.
   - 이름을 한 글자 추가하면 inline 에러 사라지고 저장 버튼 enable 되는지 확인.
3. **Edit 흐름**:
   - 기존 Category row 탭 → "편집" → 이름은 그대로 두고 다른 필드 수정 → 저장 버튼이 enable 인지 확인 (자기 자신 제외 로직 검증).
4. **Detail Path B 흐름**:
   - 알림 게시: `adb shell cmd notification post -S bigtext -t "DupNameTest" Snd1 "본문"`.
   - SmartNoti → 정리함/Home 어디서든 카드 탭 → Detail → "분류 변경" → "새 분류 만들기" → editor 가 prefill `name="DupNameTest"` 로 마운트.
   - 두 번째 알림 동일 sender 로 게시 후 같은 흐름 반복 → 두 번째 진입 시 editor 가 즉시 DUPLICATE 상태로 마운트되고 저장 차단되는지 확인 (기존 첫 번째 Category 가 이미 `"DupNameTest"` 라는 이름으로 persist 됐으므로).
   - 사용자가 prefill 된 이름을 수정하면 inline 에러 사라지고 저장 가능해지는지 확인.
5. 결과를 `docs/journeys/categories-management.md` Change log + Verification log (README) 에 캡처와 함께 기록.

## Task 5: Update `categories-management` journey

**Objective:** Observable steps + Code pointers + Known gaps + Tests + Change log 동기화.

**Files:**
- `docs/journeys/categories-management.md`

**Steps:**
1. Observable step 5 의 "이름 입력 (`OutlinedTextField`)" 라인에 "동일 이름 (trim + case-insensitive) 의 기존 Category 가 있으면 inline 에러 `이미 사용 중인 이름이에요` + 저장 버튼 비활성화 — 자기 자신 편집은 충돌로 간주하지 않음." 한 문장 추가.
2. Known gaps 의 첫 bullet ("Category 이름 고유성 미보장 (id 가 primary key 이므로 중복 이름 허용).") 을 **(resolved 2026-04-25, plan `2026-04-25-category-name-uniqueness`)** prefix 로 마킹 — `.claude/rules/docs-sync.md` 의 "Known gap 본문은 그대로, plan 링크만 annotate" 규칙을 따른다. 본문 텍스트 변경 금지.
3. Code pointers 에 `CategoryNameUniqueness` 추가.
4. Tests 에 `CategoryNameUniquenessTest` 항목 추가, `CategoryEditorDraftValidatorTest` 항목에 "uniqueness 분기" 한 줄 추가.
5. Change log 에 본 PR 항목 append (날짜는 implementer 가 작업한 실제 UTC 날짜, 요약, plan link, PR link merge 시 채움).
6. `last-verified` 는 ADB recipe 를 처음부터 끝까지 다시 돌리지 않았다면 **갱신하지 않음**.

## Task 6: Self-review + PR

- `./gradlew :app:testDebugUnitTest` GREEN, `./gradlew :app:assembleDebug` 통과.
- PR 본문에 다음 명시:
  1. 채택한 wiring 옵션 (A 단일 메서드 확장 / B 메서드 분리) 과 이유.
  2. ADB 검증 결과 (분류 탭 신규 / Edit 흐름 본인 이름 / Detail Path B 두 번째 진입 차단) — uiautomator dump 발췌 또는 screenshot 1장.
  3. 마이그레이션 미실행 (이미 영속화된 중복 이름 Category 는 자동 정리되지 않음) — 현재 사용자 데이터에 그런 케이스가 발견됐는지 한 줄 보고.
- PR 제목 후보: `feat(categories): block duplicate Category names in editor save gate`.
- frontmatter 를 `status: shipped` + `superseded-by: ../journeys/categories-management.md` 로 flip — plan-implementer 가 implementer-frontmatter-flip 규칙대로 자동 처리.

---

## Scope

**In:**
- 신규 pure `CategoryNameUniqueness` (object/class) + `NameStatus` enum.
- `CategoryEditorDraftValidator` 시그니처 확장 (옵션 A 또는 B).
- `CategoryEditorScreen` 이름 필드 `isError` + `supportingText` + 저장 버튼 `enabled` 게이트 통합.
- 신규 unit test + 기존 validator test 보강.
- Journey 문서 동기화 (Observable / Code pointers / Known gaps annotate / Tests / Change log).

**Out:**
- 이미 영속화된 중복 이름 Category 의 자동 cleanup / rename — 사용자 데이터 보존 원칙. 발견 시 별도 plan.
- "이름이 중복돼요. 기존 분류 열기?" 같은 redirect 액션 — Editor 안에 inline 정보만. 후속 plan 가능.
- Path B prefill 자동 suffix (`"<name> 2"` 휴리스틱) — open question 으로 남김.
- 다국어 / locale 별 비교 정규화 (NFC/NFD, 한글 자모 분리 등) — 현 비교는 trim + case-insensitive 만.
- Rule 이름 / 앱 라벨 등 다른 도메인 객체의 이름 고유성 — out.

---

## Risks / open questions

- **Path B prefill 자동 suffix?** Detail "새 분류 만들기" 가 같은 이름의 기존 Category 가 있을 때 자동으로 `"<name> 2"` 같은 suffix 를 붙여줄지 여부. 사용자 친절도 vs 자동 추측의 위험 — 본 plan 은 추측을 거절하고 inline 차단만. 만약 사용자가 자주 같은 sender 로 두 번째 Category 를 만들고자 한다면 (예: "엄마" 의 Person Category 와 "엄마 (회사)" 의 KEYWORD Category) 차단 카피만으로는 다음 액션 (이름 어떻게 바꿀지) 이 모호할 수 있음. 사용자 사전 결정 필요.
- **Edit 흐름에서 기존 dup 이 이미 영속화된 케이스**: 사용자가 두 개의 `"중요"` Category 를 가진 채 둘 중 하나를 편집하면 — 자기 id 제외 로직 덕에 `OK` 로 통과한다. 즉 본 plan 은 기존 dup 을 강제 정리하지 않는다 (의도). 만약 정리도 동시에 원하면 별도 plan 으로 마이그레이션 카드 / 사용자 안내 추가.
- **`existing` 리스트의 신선도**: Editor 가 `categories` 를 props 로 받지만 사용자가 editor 를 연 상태에서 다른 경로 (예: 다른 화면) 로 Category 를 만들 가능성은 사실상 0 (dialog 가 모달임). 따라서 race 는 무시 가능. 만약 백그라운드 sync 같은 게 추가되면 재고 필요.
- **편집 중 `id` 가 없는 Path B prefill** 의 자기 자신 제외: Path B 는 `currentCategoryId = null` 이고 prefill 의 sender 이름이 기존 Category 와 충돌하는 경우 — 본 plan 은 신규로 간주해 모두 비교한다 (DUPLICATE 가 자연 발생). 의도적.
- **카피 톤 검증**: "이미 사용 중인 이름이에요" 는 카피 가이드와 일관한지 — 다른 inline 에러 카피 (예: Editor 의 다른 필드, RuleEditor) 와 같은 어조인지 implementer 가 grep 해서 한 줄 확인. 어긋나면 PR 본문에 그대로 기록.
- **테스트 fragility**: `CategoryEditorDraftValidatorTest` 가 기존에 두 가지 시나리오만 검증 중. 옵션 A 로 시그니처를 확장하면 모든 호출 사이트가 인자 4개로 바뀜 — 컴파일 에러로 즉시 잡혀 silent breakage 위험은 낮다. 옵션 B 로 메서드를 분리하면 기존 테스트 그대로 두고 새 메서드 테스트만 추가 — 안전성 측면에서 옵션 B 권장.

---

## Related journey

- [categories-management](../journeys/categories-management.md) — Known gaps 첫 bullet ("Category 이름 고유성 미보장 (id 가 primary key 이므로 중복 이름 허용).") 을 해소. 본 PR ship 후 해당 bullet 을 (resolved …) 로 마킹하고 Change log 에 PR 링크 추가.
