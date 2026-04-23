---
status: planned
---

# Categories Condition Chips Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 분류(Category) 카드와 편집기에서 "이 분류가 어떤 조건으로 매칭되어 어떤 액션을 수행하는지" 한 줄에 시각적으로 인지할 수 있게 한다. 현재 Category row 는 이름/앱뱃지/액션뱃지/Rule 개수만 보여주고 실제 매칭 조건은 Detail 진입 후에야 드러나기 때문에, 신규 사용자가 "조건 → 분류 → 액션" 의 파이프라인을 직관적으로 이해하기 어렵다. 본 plan 은 각 분류의 소속 Rule 들을 `조건: sender=X 또는 keyword=Y → 즉시 전달` 형태의 칩으로 인라인 렌더링해, 5탭 BottomNav 같은 구조 변경 없이 정보 밀도만 높인다.

**Architecture:**
- 순수 함수 `CategoryConditionChipFormatter` 신설 — `(rules: List<Rule>, action: CategoryAction) → CategoryConditionChipText` (data class with `conditionPrefix`, `conditionTokens`, `actionLabel`). Compose 의존성 없음, 단위 테스트 100% 가능.
- `CategoryConditionChips` composable 신설 (`ui/screens/categories/components/`) — formatter 결과를 받아 칩 row 를 렌더. Card 모드(최대 N개 + "외 N개" overflow)와 Editor 모드(전체 펼침) 두 variant.
- `CategoriesScreen` row, `CategoryDetailScreen` 요약, `CategoryEditorScreen` 미리보기 영역에 동일 composable 을 wire — 한 곳에서 카피가 바뀌면 세 화면에 즉시 반영.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit unit tests.

---

## Product intent / assumptions

- **사용자 confirmed**: "조건: sender=X 또는 keyword=Y → 즉시 전달" 형식의 Editor-Only-Visual 솔루션 채택. 5탭 BottomNav / 탭 rename / Rules 승격 모두 reject.
- **사용자 confirmed**: Settings 의 "고급 규칙 편집" 라벨은 본 PR scope 밖 — 변경 금지.
- 칩 카피는 한국어 자연스러움 우선. 토큰 형식 후보:
  - PERSON: `보낸이=홍길동`
  - APP: `앱=카카오톡` (앱 라벨 resolution 은 Rule.matchValue 가 packageName 인 경우 별도 lookup 필요 — 1차 구현은 raw matchValue, 후속 enhance)
  - KEYWORD: `키워드=세일`
  - SCHEDULE: `시간=22:00–07:00`
  - REPEAT_BUNDLE: `반복묶음`
- 칩 사이 연결어 한국어: 첫 조건 앞에 `조건:`, 조건들 사이에 ` 또는 `, 마지막 액션 앞에 ` → `. 예: `조건: 키워드=세일 또는 보낸이=쇼핑몰 → 모아서 알림`.
- Action 라벨은 사용자 현행 어휘를 유지: PRIORITY=`즉시 전달`, DIGEST=`모아서 알림`, SILENT=`조용히`, IGNORE=`무시` (현행 `CategoryActionBadge` 의 라벨과 일치 — drift 방지를 위해 가능하면 같은 상수 참조).
- Card 모드 overflow 임계: 조건 토큰 1~2개까지는 펼치고, 3개 이상이면 첫 1개 + "외 N개" (e.g. `조건: 키워드=세일 외 2개 → 조용히`). 정확한 N=1 vs N=2 cutoff 는 구현 시 시각 검증으로 결정 (pure function 의 파라미터로 expose 해 양쪽 다 테스트 가능하게).
- Editor 모드는 항상 모두 펼침 — 사용자가 편집 중이므로 정보 손실 없음이 우선.

---

## Task 1: Add failing tests for `CategoryConditionChipFormatter`

**Objective:** 칩 카피의 형식·언어·overflow 규칙을 테스트로 고정한 뒤 구현.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/categories/components/CategoryConditionChipFormatterTest.kt`

**Steps:**
1. 다음 시나리오를 케이스로 작성:
   - Rule 0개 + DIGEST → `조건 없음 → 모아서 알림` (방어적 케이스 — 실제로는 validator 가 막지만 안전 기본값).
   - Rule 1개 (KEYWORD `세일`) + PRIORITY → `조건: 키워드=세일 → 즉시 전달`.
   - Rule 2개 (KEYWORD `세일`, PERSON `홍길동`) + DIGEST → `조건: 키워드=세일 또는 보낸이=홍길동 → 모아서 알림`.
   - Rule 3개 + maxInline=1 → `조건: 키워드=세일 외 2개 → 조용히`.
   - Rule 3개 + maxInline=Int.MAX_VALUE (Editor 모드) → 모두 펼침 + 액션.
   - SCHEDULE / REPEAT_BUNDLE / APP type 각각 라벨 검증.
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.components.CategoryConditionChipFormatterTest"` 로 모두 RED 확인.

## Task 2: Implement `CategoryConditionChipFormatter` + composable

**Objective:** Task 1 의 테스트가 GREEN 이 되게 + Compose UI 한 단위.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/components/CategoryConditionChipFormatter.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/categories/components/CategoryConditionChips.kt`

**Steps:**
1. `CategoryConditionChipFormatter.format(rules, action, maxInline)` — 순수 함수. 반환 `CategoryConditionChipText(prefix="조건:", tokens=List<String>, overflowLabel: String?, arrow=" → ", actionLabel: String)`.
2. `CategoryConditionChipText.toPlainString()` 헬퍼도 추가 — 테스트 어서션 단순화 + 접근성(content description) 용.
3. `CategoryConditionChips(text: CategoryConditionChipText, modifier: Modifier = Modifier)` composable — 작은 `AssistChip` / `Surface` chip row. 색상은 분류 액션 색에 맞춘 subtle tint (현행 `CategoryActionBadge` 색 재사용). 다크 우선 (`.claude/rules/ui-improvement.md`).
4. Action 라벨 상수는 `CategoryActionBadge` 와 공유되도록 별도 `CategoryActionLabels` object 로 추출하거나 기존 위치에서 재export (drift 방지).
5. `./gradlew :app:testDebugUnitTest` 전체 그린 확인.

## Task 3: Wire chips into Categories screens

**Objective:** 세 화면에서 동일 composable 을 사용해 정보 밀도 향상.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoriesScreen.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryDetailScreen.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryEditorScreen.kt`

**Steps:**
1. `CategoriesScreen` Category row 의 metadata line 아래에 `CategoryConditionChips(formatter.format(rules, category.action, maxInline = 2))` 추가. Rules 는 `RulesRepository.observeRules()` 에서 이미 구독 중이므로 `category.ruleIds` 로 lookup.
2. `CategoryDetailScreen` 요약 영역 (이름 / 앱 / 액션 chip 아래) 에 `CategoryConditionChips(..., maxInline = Int.MAX_VALUE)` 추가 — Detail 은 펼침.
3. `CategoryEditorScreen` 의 Rule 멀티셀렉트 chip 아래에 미리보기로 `CategoryConditionChips(..., maxInline = Int.MAX_VALUE)` 렌더 — 사용자가 칩을 토글하면 즉시 반영 (Compose state 자연 흐름).
4. 시각 검증: `./gradlew :app:assembleDebug` + adb install + 분류 탭 진입해 카드/Detail/Editor 각각 칩 노출 확인. 칩이 너무 빽빽하면 maxInline 조정.

## Task 4: Update `categories-management` journey

**Objective:** Observable steps + Code pointers + Change log 동기화 (`docs-sync.md` 준수).

**Files:**
- `docs/journeys/categories-management.md`

**Steps:**
1. Observable step 3 에 chip row 항목 추가 — "각 row 는 Category 메타데이터 아래에 `CategoryConditionChips` 가 추가돼 `조건: 키워드=세일 → 즉시 전달` 형태로 매칭 조건과 액션을 한 줄에 미리 보여준다. 조건이 많으면 `외 N개` 로 축약."
2. Observable step 4 (Detail) 에 "Detail 은 칩을 모두 펼친다" 추가.
3. Observable step 5 (Editor) 에 "Rule 멀티셀렉트 변경에 따라 칩 미리보기가 즉시 갱신" 추가.
4. Code pointers 에 `CategoryConditionChips`, `CategoryConditionChipFormatter`, `CategoryActionLabels` 신규 항목 추가.
5. Tests 에 `CategoryConditionChipFormatterTest` 추가.
6. Change log 에 본 PR 항목 append (날짜 2026-04-24, 요약, plan link, PR link merge 시 채움).
7. `last-verified` 는 **갱신하지 않음** — recipe 를 직접 돌리지 않았다면 그대로 둔다.

## Task 5: Self-review + PR

- 단위 테스트 GREEN, `./gradlew :app:assembleDebug` 통과.
- PR 본문에 카드 / Detail / Editor 각각 chip 렌더 스크린샷 첨부 (가능하면 1~2개 / 3개 이상 두 케이스).
- PR 제목: `feat(categories): inline condition chips on cards and editor`.

---

## Scope

**In:**
- 새 pure-function formatter + 단위 테스트.
- 새 reusable composable.
- 세 화면 (`CategoriesScreen`, `CategoryDetailScreen`, `CategoryEditorScreen`) 에 wire.
- Journey 갱신 (Observable steps / Code pointers / Tests / Change log).

**Out:**
- BottomNav 5탭 변경 / "분류" 탭 rename — 명시적으로 reject.
- Settings 의 "고급 규칙 편집" 라벨 변경 — 명시적으로 reject.
- 앱 packageName → 사용자 친화 라벨 resolution (현행 raw matchValue 사용. 후속 plan).
- Detail 화면의 "최근 이 Category 로 분류된 알림 preview" — 별도 Known gap, 본 plan 은 손대지 않음.
- BottomBar / 다른 화면 visual polish.

---

## Risks / open questions

- **Card overflow 임계 (maxInline) 확정**: `1` vs `2` 중 어느 값이 실제 카드 폭에서 한 줄에 들어가는지 시각 검증 필요. Pure function 으로 maxInline 을 파라미터화해 양쪽 다 테스트 가능하게 만들었으므로, 구현 후 ADB/에뮬레이터로 결정. 사용자가 사전에 cutoff 를 지정하지 않았으므로 implementer 판단 가능 범위 — 그러나 결과는 PR 본문에 명시.
- **APP type matchValue 의 사용자 친화성**: 현행 `Rule.matchValue` 가 `com.kakao.talk` 같은 packageName 인 경우 칩에 그대로 노출되면 가독성이 떨어진다. 후속 plan 으로 `appLabelLookup` injection 을 도입할 수 있으나, 본 PR 에서는 raw 노출 + Known gap 추가로 처리 예정. 사용자가 즉시 깔끔하게 보이길 원한다면 별도 task 로 승격해야 함 — open question 으로 유지.
- **칩 색 vs `CategoryActionBadge` 색의 위계**: 두 요소가 같은 색이면 시각 노이즈가 될 수 있음. 칩은 더 subtle tint 로 차별화 필요. 디자인 미세조정은 Task 3 시각 검증 단계에서.
- **카피 변경 1차 비용**: `조건` / `또는` / `→` / 액션 라벨이 향후 다국어로 확장되면 이 formatter 가 i18n 진입점이 된다. 본 PR 에서는 한국어 하드코딩 + 상수화만 수행, 다국어는 별도 plan.

---

## Related journey

- [categories-management](../journeys/categories-management.md) — Known gaps 의 "신규 사용자가 조건→분류 파이프라인을 인지하기 어렵다" (현행 문서에는 명시되지 않음 — 본 plan 이 사용자 직접 요청으로 큐 우회) 를 해소. 본 PR ship 후 해당 journey 의 Change log 에 추가하고 Code pointers 에 신규 composable/formatter 추가.
