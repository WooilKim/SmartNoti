---
id: categories-management
title: 분류 (Category) CRUD + drag-reorder + 액션 선택
status: shipped
owner: @wooilkim
last-verified: 2026-04-25
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
   - 그 아래 `CategoryConditionChips` (`maxInline = 2`) 가 추가돼 `조건: 키워드=세일 → 즉시 전달` 형태로 매칭 조건과 액션을 한 줄에 미리 보여준다. 조건 토큰이 3개 이상이면 첫 2개 + `외 N개` 로 축약된다. 카피는 `CategoryConditionChipFormatter` (pure) + `CategoryActionLabels` 가 단일 진입점. APP 타입 토큰은 사용자 친화 라벨 (예: `앱=카카오톡`) 로 보이고, 라벨 해석에 실패하면 `앱=<packageName>` 으로 fallback (production 은 `LocalAppLabelLookup` 가 `PackageManagerAppLabelLookup` 를 주입).
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
- `ui/screens/categories/components/CategoryConditionChipFormatter` — pure formatter for the inline `조건: ... → action` chip text. i18n 진입점. APP 토큰은 옵션 `appLabelLookup` 인자를 통해 user-facing label 로 해석.
- `ui/screens/categories/components/CategoryConditionChips` — Compose chip row (card / detail / editor 공용). `LocalAppLabelLookup.current` 를 default 로 사용해 APP 토큰이 자동으로 라벨 해석을 받는다.
- `ui/screens/categories/components/CategoryActionLabels` — chip-variant action 라벨 단일 상수
- `ui/screens/categories/components/AppLabelLookup` — `fun interface AppLabelLookup` (SAM, packageName → label?) + production `PackageManagerAppLabelLookup`. lookup 실패 (NameNotFoundException) 시 `null` 반환 → formatter 가 raw matchValue 로 fallback.
- `app/src/main/AndroidManifest.xml` — `<queries><intent action=MAIN></queries>` 선언 (Android 11+ package visibility) 으로 `PackageManagerAppLabelLookup` 의 `getApplicationInfo` lookup 이 launcher 활동을 가진 임의 사용자 앱 (예: `com.google.android.youtube` → `YouTube`) 에도 성공. listener 쪽은 `BIND_NOTIFICATION_LISTENER_SERVICE` 가 implicit visibility 를 부여하므로 manifest 추가 없이도 이미 동작 중이었음.
- `ui/screens/categories/components/LocalAppLabelLookup` — CompositionLocal. `MainActivity.setContent` 에서 production `PackageManagerAppLabelLookup(packageManager)` provide. preview/test 환경 default 는 `AppLabelLookup.Identity` (항상 raw 노출).
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
- `CategoryConditionChipFormatterTest` — chip 카피 형식 / 토큰 라벨 / 액션 라벨 / `외 N개` overflow / 빈 rule list 방어 케이스. APP 토큰 `appLabelLookup` 분기 (라벨 적용 / null fallback / blank fallback / blank matchValue lookup skip / PERSON·KEYWORD·SCHEDULE·REPEAT_BUNDLE 무영향 / 혼합 rule / default Identity backward-compat) 도 포함.

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
- (resolved 2026-04-25, plan `docs/plans/2026-04-25-category-chip-app-label-lookup.md`) `CategoryConditionChips` 의 APP 토큰은 현재 `Rule.matchValue` (raw `com.kakao.talk` 같은 packageName) 를 그대로 노출 — `appLabelLookup` 주입으로 사용자 친화 라벨로 바꾸는 후속 plan 필요 (plan `2026-04-24-categories-condition-chips.md` open question). → 후속 plan: `docs/plans/2026-04-25-category-chip-app-label-lookup.md` (chip surface 한 곳만 적용; 카드 metadata line / Detail "연결된 앱 · ${packageName}" 은 동일 lookup 으로 후속 PR 가능).

## Change log

- 2026-04-25: APP-토큰 라벨 가시성 보강 — plan `docs/plans/2026-04-25-android-queries-package-visibility.md`. AndroidManifest 에 `<queries><intent action=MAIN></queries>` 선언 → `PackageManagerAppLabelLookup` 가 Android 11+ 에서도 launcher 활동을 가진 임의 사용자 앱 (예: YouTube) 의 라벨을 정상 해석. chip 이 더 이상 raw `com.google.android.youtube` 로 fallback 되지 않음. ADB 검증 emulator-5554: `중요 알림` Category 에 `com.google.android.youtube` APP rule (이름 `YT`) 합류 후 Detail 의 펼친 chip 이 `앱=YouTube` 로 노출 (text 직접 확인). 카드 chip 은 `maxInline=2` 이라 `외 2개` 로 가려졌지만 동일 lookup 경로. listener 쪽 `SmartNotiNotificationListenerService` 의 라벨 해석 코드는 변경 없음 (notification-access grant 로 이미 성공하던 경로). Manifest 누락이 근본 원인이었으며, regression 방지로 `PackageManagerVisibilityContractTest` (pure JVM, manifest XML 파싱) 1건 추가 — Option A (instrumented) 는 CI 에 connectedDebugAndroidTest 러너가 없어 Option B 채택. (PR 링크는 머지 시 채움.)
- 2026-04-25: v1 loop tick re-verify on emulator-5554 (PASS) post-#320. `am start` → BottomNav `분류` tap → 3 categories rendered. `중요 알림` row 의 inline `CategoryConditionChips` content-desc 가 `조건: 키워드=인증번호,결제,배송,출발 또는 앱=Shell → 즉시 전달` 로 정확히 노출 (text 토큰도 `앱=Shell`). Detail 진입 → 동일 chip + 펼침 카드 모두 `앱=Shell` (메타데이터 line `APP · com.android.shell` raw packageName 은 documented out-of-scope). `편집` → `CategoryEditorScreen` AlertDialog 미리보기 chip 도 `앱=Shell` 로 즉시 렌더. 즉, 카드 / Detail / Editor 세 surface 모두 #320 의 `LocalAppLabelLookup` 자동 wiring 가 작동. Observable steps 1-5 verified, 별도 DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-25 bump.
- 2026-04-25: APP-토큰 라벨 해석 — plan `docs/plans/2026-04-25-category-chip-app-label-lookup.md`. `CategoryConditionChips` 가 더 이상 raw `앱=com.kakao.talk` 가 아닌 `앱=카카오톡` 같은 user-facing label 로 APP 토큰을 노출. `AppLabelLookup` SAM (`null` 반환 = 라벨 미상) + production `PackageManagerAppLabelLookup` (PackageManager 기반, `NameNotFoundException` → null) 신설. `LocalAppLabelLookup` CompositionLocal 을 `MainActivity.setContent` 에서 한 번 provide → 카드 / Detail / Editor 세 surface 가 모두 호출 사이트 변경 없이 자동 wiring (옵션 A — testability + 다른 surface 재사용 가용성 우선). ADB 검증 emulator-5554: `중요 알림` Category 에 `com.android.shell` APP rule 합류 후 카드 chip / Detail / Editor 미리보기 모두 `앱=Shell` 노출 (text + content-desc 동일). 라벨 해석 실패 fallback 은 unit test 의 `null/blank` 케이스로 검증. (PR 링크는 머지 시 채움.)
- 2026-04-22: 신규 문서화 — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Tasks 8 + 9 (#238) 구현 결과물. `CategoriesScreen` + `CategoryDetailScreen` + `CategoryEditorScreen` + `CategoryEditorDraftValidator` shipped. `CategoriesRepository` (DataStore `smartnoti_categories`) + `CategoryConflictResolver` + `RuleToCategoryMigration` 은 Phase P1/P2 에서 선행 shipped (#236 / #239). BottomNav "분류" 탭은 Task 11 (#240) 에서 추가. `last-verified` 는 journey-tester 가 ADB recipe 를 실행해 확정할 때까지 비워둠 (per `.claude/rules/docs-sync.md`).
- 2026-04-22: FAB 가시성 regression 수정 + empty-state inline CTA 추가 — plan `docs/plans/2026-04-22-categories-empty-state-inline-cta.md`. `CategoriesScreen` 의 `Box(fillMaxSize())` 루트가 부모 Scaffold `contentPadding` 을 소비하지 않아 FAB 가 AppBottomBar 뒤로 가려지던 문제를, 로컬 `Scaffold(floatingActionButton = ...)` 로 리팩토링해 해결. 같은 변경에서 `EmptyState` 에 옵셔널 `action` 슬롯 추가 + `CategoriesScreen` 의 empty 상태 본문 아래에 `"새 분류 만들기"` inline CTA 주입. FAB / CTA / editor 어휘를 모두 `CategoriesEmptyStateAction.LABEL = "새 분류 만들기"` 로 통일. (PR 링크는 머지 시 채움.)
- 2026-04-24: 인라인 condition chip 추가 — plan `docs/plans/2026-04-24-categories-condition-chips.md`. 카드 / Detail / Editor 3 화면에 `CategoryConditionChips` 신설 → "조건: 키워드=세일 또는 보낸이=홍길동 → 즉시 전달" 형태로 매처와 액션을 한 줄에 노출. `CategoryConditionChipFormatter` (pure) + `CategoryActionLabels` 로 카피 단일화. 카드는 `maxInline = 2` (3+ 시 `외 N개`), Detail/Editor 는 펼침. ADB 검증 emulator-5554 — chip 텍스트와 content-desc 가 모두 정확히 렌더됐고 maxInline=2 가 phone-width 카드에 깔끔하게 들어맞음. APP 토큰의 raw packageName 노출은 후속 plan 으로 미룸. (PR 링크는 머지 시 채움.)
- 2026-04-24: **미분류 Rule 진입점 신설** — plan `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md`. 고급 규칙 편집의 Rule editor 가 액션 dropdown 을 제거하면서, 신규 Rule 은 owning Category 가 없는 "미분류" draft 로 시작하게 됨. Rule 저장 직후 `CategoryAssignBottomSheet` (ModalBottomSheet) 가 강제 노출되어 기존 분류를 선택하면 `CategoriesRepository.appendRuleIdToCategory(categoryId, ruleId)` 로 분류에 합류 — 즉, 분류 탭은 "Rule 합류 destination" 역할이 새로 생김. 분류 row 탭 → Detail → 편집 시 ruleIds 가 sheet 통과로 들어온 신규 멤버를 그대로 보여줌. (CategoriesScreen 자체에 "미분류 N개" 안내 카드는 본 PR scope 밖 — 후속 plan 으로 보존.)
- 2026-04-24: v1 loop tick re-verify on emulator-5554 (PASS). BottomNav "분류" tap → 3 categories rendered ("중요 알림" 즉시 전달 / "프로모션 알림" Digest / "반복 알림" Digest), each row exposes `규칙 1개` + `CategoryActionBadge` + inline `CategoryConditionChips` (text + content-desc both `조건: … → 즉시 전달|모아서 알림`). FAB clickable bounds `[642,1938][1038,2085]` above AppBottomBar (no inset clipping regression). FAB tap → `CategoryEditorScreen` AlertDialog ("새 분류" title, name field, 전달 방식 dropdown default `즉시 전달 (PRIORITY)`, 연결할 앱 picker `특정 앱 없음`, Rule multi-select for all 3 rules, 미리보기 chip `조건 없음 → 즉시 전달`, 닫기/추가 buttons). Row tap → `CategoryDetailScreen` ("분류 상세" / 카테고리 이름 / `CategoryActionBadge` `즉시 전달` / 기본 정보 카드 with full `CategoryConditionChips` + `규칙 1개 · 순서 0` / 편집·삭제 / 소속 규칙 list with `RuleRow`). Observable steps 1-5 verified end-to-end.
- 2026-04-22: v1 loop tick re-verify on emulator-5554 (PASS). BottomNav "분류" tap → 3 categories rendered ("중요 알림" 즉시 전달 / "프로모션 알림" Digest / "반복 알림" Digest), each with `규칙 1개` + `CategoryActionBadge` chip + inline `CategoryConditionChips` (`조건: 키워드=… → 즉시 전달|모아서 알림`, content-desc mirrors text). FAB clickable region observed at bounds `[642,1938][1038,2085]` above the bottom nav (no inset clipping regression — confirms 2026-04-22 fix). FAB tap opens `CategoryEditorScreen` AlertDialog with title "새 분류" + name field + 전달 방식 dropdown (default `즉시 전달 (PRIORITY)`) + 연결할 앱 picker (`특정 앱 없음`) + Rule multi-select chips for all 3 rules + 미리보기 with `CategoryConditionChips` (`조건 없음 → 즉시 전달`) + 닫기/추가 buttons. Row tap → `CategoryDetailScreen` ("분류 상세" / 카테고리 이름 / `CategoryActionBadge` / 기본 정보 카드 + full `CategoryConditionChips` / 편집·삭제 / 소속 규칙 list with `RuleRow`). Observable steps 1–5 verified end-to-end; FAB label text not present in uiautomator dump (NAF=true Compose node) but visual region + click target confirmed.
