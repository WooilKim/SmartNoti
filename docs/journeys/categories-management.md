---
id: categories-management
title: 분류 (Category) CRUD + drag-reorder + 액션 선택
status: shipped
owner: @wooilkim
last-verified: 2026-04-22
---

## Goal

사용자가 알림 처리 단위를 "**분류(Category)**" 로 다룬다. 각 분류는 이름 + (선택) 앱 핀 + 소속 matcher Rule 집합 + 4개 액션(PRIORITY / DIGEST / SILENT / IGNORE) 하나로 구성된다. 사용자는 분류 탭에서 신규 생성 / 편집 / 삭제 / 드래그 재정렬로 우선순위 동점 상황을 명시 제어한다. Rule 은 순수 조건 매처로만 존재하며 액션은 분류가 소유한다.

## Preconditions

- 온보딩 완료
- Phase P1 의 `RuleToCategoryMigration` 이 1회 실행돼 기존 Rule 이 `cat-from-rule-<ruleId>` Category 로 옮겨진 상태 (첫 실행 시 자동, idempotent)

## Trigger

사용자가 하단 네비 "**분류**" 탭 탭 → `Routes.Categories` 네비게이션.

## Observable steps

1. `CategoriesScreen` 마운트, 다음 상태 구독:
   - `CategoriesRepository.observeCategories()` — 현재 분류 목록 (order 순)
   - `RulesRepository.observeRules()` — 편집기에서 Rule 멀티셀렉트용
   - `NotificationRepository.observeCapturedAppsFiltered(...)` — 앱 피커 제안용
   - `SettingsRepository.observeSettings()` — persistent 필터 설정
2. 상단 `ScreenHeader` ("분류") + 신규 생성 FAB (`ExtendedFloatingActionButton`, "**새 분류 만들기**"). FAB 는 로컬 `Scaffold` 의 `floatingActionButton` 슬롯을 통해 배치돼 AppBottomBar 위로 떠오른다 (`Box(fillMaxSize())` 에 올리면 부모 `contentPadding` 을 소비하지 못해 네비바 뒤로 가려지던 regression 이 2026-04-22 에 고쳐짐). 분류가 0개인 empty state 에서는 `EmptyState` 본문 아래에 같은 라벨의 **inline CTA** (`FilledTonalButton`) 가 추가로 렌더돼 신규 사용자가 시선 흐름(Header → EmptyState 카피) 그대로 탭 한 번에 editor 로 진입할 수 있다. FAB 와 inline CTA 는 모두 `CategoriesEmptyStateAction.LABEL` 단일 상수를 참조해 카피가 드리프트하지 않는다.
3. LazyColumn 으로 각 Category row 렌더:
   - 이름
   - app 핀이면 앱 뱃지 (`appPackageName != null` → 앱 라벨)
   - `CategoryActionBadge` — PRIORITY / DIGEST / SILENT / IGNORE 색상 구분 chip
   - 소속 Rule 수 (`category.ruleIds.size`)
   - drag handle (Task 6 의 `moveCategory(..., UP|DOWN)` 트리거)
   - 그 아래 `CategoryConditionChips` (`maxInline = 2`) 가 추가돼 `조건: 키워드=세일 → 즉시 전달` 형태로 매칭 조건과 액션을 한 줄에 미리 보여준다. 조건 토큰이 3개 이상이면 첫 2개 + `외 N개` 로 축약된다. 카피는 `CategoryConditionChipFormatter` (pure) + `CategoryActionLabels` 가 단일 진입점.
4. Row 탭 → `CategoryDetailScreen` 진입 (동일 composable 스택, 뒤로가기 지원):
   - Category 요약 (이름 / 앱 / 액션)
   - 요약 카드 안에 `CategoryConditionChips` (`maxInline = Int.MAX_VALUE`) — 모든 조건 토큰을 펼쳐서 사용자가 매처 사슬 전체를 감사할 수 있다.
   - 소속 Rule 리스트 (`RuleRow` 최소 렌더 — 조건만 표시)
   - "편집" → `CategoryEditorScreen`, "삭제" → 즉시 삭제 후 리스트 복귀.
5. FAB 또는 Detail "편집" → `CategoryEditorScreen` (AlertDialog 기반, 기존 RuleEditor 와 동일 패턴):
   - 이름 입력 (`OutlinedTextField`)
   - 앱 피커 (`DropdownMenu` — `capturedApps` + "특정 앱 없음")
   - Rule 멀티셀렉트 (`FilterChip` × rules) + 그 아래 "미리보기" 라벨과 `CategoryConditionChips` 가 사용자가 chip 을 토글하거나 액션을 바꿀 때마다 즉시 다시 렌더돼 저장 전 결과 카피를 확인할 수 있다.
   - 액션 `DropdownMenu` (PRIORITY / DIGEST / SILENT / IGNORE)
   - `CategoryEditorDraftValidator` 로 저장 버튼 gate (이름 필수, Rule 최소 1개)
6. 저장 → `CategoriesRepository.upsertCategory(category)` (DataStore `smartnoti_categories` 영속화). 신규는 `order = max(existing) + 1` (뒤에 append). 편집은 기존 `order` 유지.
7. 삭제 → `CategoriesRepository.deleteCategory(categoryId)`.
8. drag handle long-press + swap → `CategoriesRepository.moveCategory(id, UP|DOWN)` → 이웃과 직접 교환. 결과는 그 다음 분류 매칭 시 `CategoryConflictResolver` 의 동점 tie-break 에 사용 (상단 = 낮은 `order` = 승).

## Exit state

- DataStore `smartnoti_categories` 에 신규 / 변경 / 삭제가 영속됨.
- 해당 분류가 소유한 Rule 매치 결과가 새 `action` 으로 분류됨 (→ [notification-capture-classify](notification-capture-classify.md)).
- 드래그 재정렬은 이후 **동일 specificity** 충돌에서만 관측 가능 — app-pin 이나 rule-type 사다리 차이가 있으면 드래그 순서 무관하게 specificity 높은 분류가 이긴다.

## Out of scope

- Rule 자체 CRUD (→ [rules-management](rules-management.md) — 이제 "고급 규칙 편집" 으로 Settings 하위)
- 새 앱 감지 + 분류 유도 (→ [home-uncategorized-prompt](home-uncategorized-prompt.md))
- 분류가 실제로 분류에 어떻게 적용되는가 (→ [notification-capture-classify](notification-capture-classify.md))
- Category 간 공유 / 템플릿 — 현 plan out-of-scope

## Code pointers

- `ui/screens/categories/CategoriesScreen` — 목록 + FAB + detail/editor 호스팅. 로컬 `Scaffold(floatingActionButton = ...)` 슬롯으로 FAB 배치 (AppBottomBar inset 자동 회피).
- `ui/screens/categories/CategoriesEmptyStateAction` — FAB/EmptyState CTA 공용 라벨 상수 (`"새 분류 만들기"`)
- `ui/components/EmptyState` — `action: (@Composable () -> Unit)?` 옵셔널 슬롯 (`subtitle` 아래 inline CTA 렌더)
- `ui/screens/categories/CategoryDetailScreen` — Category 요약 + 소속 Rule 리스트 + 편집/삭제
- `ui/screens/categories/CategoryEditorScreen` — AlertDialog 에디터 (신규/편집 공용)
- `ui/screens/categories/CategoryEditorDraftValidator` — 이름 + Rule 최소 1개 검증
- `ui/screens/categories/CategoryActionBadge` — PRIORITY/DIGEST/SILENT/IGNORE 색상 chip
- `ui/screens/categories/components/CategoryConditionChipFormatter` — pure formatter for the inline `조건: ... → action` chip text. i18n 진입점.
- `ui/screens/categories/components/CategoryConditionChips` — Compose chip row (card / detail / editor 공용)
- `ui/screens/categories/components/CategoryActionLabels` — chip-variant action 라벨 단일 상수
- `data/categories/CategoriesRepository` — DataStore `smartnoti_categories`, `observeCategories` / `upsertCategory` / `deleteCategory` / `moveCategory`
- `data/categories/CategoryStorageCodec` — JSON 직렬화
- `data/categories/RuleToCategoryMigration` + `MigrateRulesToCategoriesRunner` — 첫 실행 1:1 자동 마이그레이션
- `domain/model/Category` + `CategoryAction` — 4-value enum
- `domain/usecase/CategoryConflictResolver` — 매칭 분류 집합에서 승자 선택 (app-pin 보너스 + rule-type 사다리 + `order` 동점)
- `navigation/Routes#Categories`, `navigation/BottomNavItem` ("분류" 라벨)

## Tests

- `CategoryTest` — Category 4-field 계약 + `CategoryAction` enum 정확히 4개
- `CategoriesRepositoryTest` — CRUD + JSON round-trip + 동일 Rule id 의 다중 Category 소속 허용
- `CategoryTieBreakTest` — 드래그 `order` 동점 tie-break, app-pin 보너스, IGNORE vs SILENT 동점
- `CategoryEditorDraftValidatorTest` — 저장 버튼 enable/disable 로직
- `CategoryConflictResolverTest` — specificity + tie-break 전체 케이스
- `RuleToCategoryMigrationTest` — 1:1 변환 + 2회 실행 idempotent
- `CategoryConditionChipFormatterTest` — chip 카피 형식 / 토큰 라벨 / 액션 라벨 / `외 N개` overflow / 빈 rule list 방어 케이스

## Verification recipe

```bash
# 1. 분류 탭 진입 → 기존 Category 목록 확인 (migration 결과)
adb shell am start -n com.smartnoti.app/.MainActivity
# BottomNav "분류" 탭 탭 (1080x2400 기준 좌표는 환경에 따라 다름)

# 2. FAB "분류 만들기" 탭 → editor 다이얼로그 오픈
# 3. 이름 "테스트분류" + Rule 1개 선택 + 액션 DIGEST → "저장" 탭
# 4. 목록에 새 row 추가되는지 확인, `CategoryActionBadge` = Digest 렌더

# 5. drag handle long-press + drag → 목록 순서 변경 후 유지되는지 확인

# 6. 같은 Category row 탭 → Detail 진입 → "삭제" → 목록에서 사라지는지 확인
```

## Known gaps

- Drag-reorder 는 현재 arrow/handle 기반 — smooth drag gesture 는 현행 UI 가 직접 이웃 swap 만 지원 (`moveCategory` 계약상 한 스텝씩).
- Category 이름 고유성 미보장 (id 가 primary key 이므로 중복 이름 허용).
- Detail 화면이 "최근 이 Category 로 분류된 알림 preview" 를 아직 표시하지 않음 — 현재는 Rule 리스트 + 액션 chip 까지만. 후속 plan 대상.
- Category 자동 추천 (ML / 휴리스틱) 미구현 — 사용자가 수동 생성만 가능.
- Recipe 는 아직 ADB 로 end-to-end 검증되지 않음 (`last-verified` 비어 있음). 첫 journey-tester sweep 에서 스크린샷 + uiautomator dump 로 고정.
- `CategoryConditionChips` 의 APP 토큰은 현재 `Rule.matchValue` (raw `com.kakao.talk` 같은 packageName) 를 그대로 노출 — `appLabelLookup` 주입으로 사용자 친화 라벨로 바꾸는 후속 plan 필요 (plan `2026-04-24-categories-condition-chips.md` open question).

## Change log

- 2026-04-22: 신규 문서화 — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Tasks 8 + 9 (#238) 구현 결과물. `CategoriesScreen` + `CategoryDetailScreen` + `CategoryEditorScreen` + `CategoryEditorDraftValidator` shipped. `CategoriesRepository` (DataStore `smartnoti_categories`) + `CategoryConflictResolver` + `RuleToCategoryMigration` 은 Phase P1/P2 에서 선행 shipped (#236 / #239). BottomNav "분류" 탭은 Task 11 (#240) 에서 추가. `last-verified` 는 journey-tester 가 ADB recipe 를 실행해 확정할 때까지 비워둠 (per `.claude/rules/docs-sync.md`).
- 2026-04-22: FAB 가시성 regression 수정 + empty-state inline CTA 추가 — plan `docs/plans/2026-04-22-categories-empty-state-inline-cta.md`. `CategoriesScreen` 의 `Box(fillMaxSize())` 루트가 부모 Scaffold `contentPadding` 을 소비하지 않아 FAB 가 AppBottomBar 뒤로 가려지던 문제를, 로컬 `Scaffold(floatingActionButton = ...)` 로 리팩토링해 해결. 같은 변경에서 `EmptyState` 에 옵셔널 `action` 슬롯 추가 + `CategoriesScreen` 의 empty 상태 본문 아래에 `"새 분류 만들기"` inline CTA 주입. FAB / CTA / editor 어휘를 모두 `CategoriesEmptyStateAction.LABEL = "새 분류 만들기"` 로 통일. (PR 링크는 머지 시 채움.)
- 2026-04-24: 인라인 condition chip 추가 — plan `docs/plans/2026-04-24-categories-condition-chips.md`. 카드 / Detail / Editor 3 화면에 `CategoryConditionChips` 신설 → "조건: 키워드=세일 또는 보낸이=홍길동 → 즉시 전달" 형태로 매처와 액션을 한 줄에 노출. `CategoryConditionChipFormatter` (pure) + `CategoryActionLabels` 로 카피 단일화. 카드는 `maxInline = 2` (3+ 시 `외 N개`), Detail/Editor 는 펼침. ADB 검증 emulator-5554 — chip 텍스트와 content-desc 가 모두 정확히 렌더됐고 maxInline=2 가 phone-width 카드에 깔끔하게 들어맞음. APP 토큰의 raw packageName 노출은 후속 plan 으로 미룸. (PR 링크는 머지 시 채움.)
