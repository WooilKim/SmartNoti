---
id: rules-management
title: 고급 규칙 편집 (Settings 하위)
status: shipped
owner: @wooilkim
last-verified: 2026-04-26
---

## Goal

전문 사용자가 발신자 / 앱 / 키워드 / 스케줄 / 반복묶음 **조건 매처(Rule)** 를 직접 만들고 편집/삭제/순서 조정/필터링 해서 분류 파이프라인의 입력으로 활용한다. 일반적으로는 "분류(Category)" 탭에서 편집하는 것이 권장 경로 — Rules 화면은 Category 에 묶이기 전의 매처 자체를 직접 손대기 위한 power-user 진입점으로 Settings > "고급 규칙 편집" 에 격하됐다.

> 2026-04-22 plan `categories-split-rules-actions` Phase P3 Task 11 이후 `Routes.Rules` 는 BottomNav 탭이 아니다. Settings 의 "고급 규칙 편집 열기" 버튼으로만 진입한다. Route 는 Detail 의 "적용된 규칙" 칩 deep-link 의 타겟으로 계속 사용된다. Rule 모델은 순수 조건 매처이며 action (PRIORITY/DIGEST/SILENT/IGNORE) 은 Category 가 소유 (→ [categories-management](categories-management.md)).

## Preconditions

- 온보딩 완료

## Trigger

- Settings 탭 → "고급" 섹션의 "고급 규칙 편집 열기" OutlinedButton 탭 → `navController.navigate(Routes.Rules.create())`
- Detail 의 "적용된 규칙" 칩 탭 → `Routes.Rules.create(highlightRuleId = ...)` (해당 Rule row 로 스크롤 + 플래시)
- (BottomNav "규칙" 탭 진입은 plan Task 11 이후 제거됨)

## Observable steps

1. `RulesScreen` 마운트, 다음 상태 구독:
   - `RulesRepository.observeRules()` — 현재 룰 목록
   - `CategoriesRepository.observeCategories()` — Rule row 에 표시할 액션은 `RuleCategoryActionIndex` 로 owning Category 의 action 을 조회해 derive
   - `NotificationRepository.observeCapturedAppsFiltered(...)` — 앱 선택 제안용
   - `SettingsRepository.observeSettings()` — persistent 알림 필터 설정
2. 상단 `ScreenHeader` + "직접 규칙 추가" 카드의 "새 규칙 추가" 버튼.
3. "활성 규칙 N개" 카드 + `FilterChip` (ALWAYS_PRIORITY / DIGEST / SILENT / IGNORE) — `RuleListFilterApplicator` 가 선택된 액션으로 필터링. Action 은 Rule 자체가 아니라 owning Category 의 action 을 reflect. IGNORE chip 은 해당 action 의 Category 에 속한 Rule 이 최소 하나 이상일 때만 노출.
4. 액션별로 그룹화된 리스트 (`RuleListGroupingBuilder`, 순서: 즉시 전달 → Digest → 조용히 → 상황별 → 무시). 그룹 내부에서 `RuleListHierarchyBuilder` 가 flat list 를 tree 로 재조립 — base rule 아래 해당 rule 을 `overrideOf` 로 가리키는 override rule 이 자식으로 nest. 삭제/필터링된 base 를 참조하는 override 는 top-level 에 "기본 규칙을 찾을 수 없음" broken indicator 와 함께 렌더.
5. `RuleRow` 는 depth 에 따라 indent (16dp per level), override 는 primary tint pill "이 규칙의 예외 · {base 이름}" 라벨을, broken override 는 error tint 경고 문구를 제목 옆에 표시. 제목 / 매치값 / **(derived) 액션 chip** / 순서 이동 (drag handle + arrow fallback) / 편집 / 삭제 어포던스.
6. 추가 / 편집 탭 → `AlertDialog` (rule editor) 열림:
   - 룰 타입 (`RuleTypeUi`: PERSON / APP / KEYWORD / SCHEDULE / REPEAT_BUNDLE) 선택
   - 매치 값 입력 (타입별: 이름, 패키지, 키워드, 시간 범위, 반복 임계)
   - **액션 선택은 더 이상 editor 안에서 묻지 않음 (plan `2026-04-24-rule-editor-remove-action-dropdown`).** Rule 은 순수 조건 매처이고 액션 (PRIORITY / DIGEST / SILENT / IGNORE) 은 owning Category 가 소유. "처리 방식" SectionLabel + dropdown + `draftAction` state + 저장 시 1:1 companion Category auto-upsert 로직 모두 제거됨.
   - "예외 규칙" 섹션 — "기존 규칙의 예외로 만들기" `Switch` + (ON 일 때) `RuleEditorOverrideOptionsBuilder` 가 만든 base 후보 dropdown. 후보가 없으면 switch 자체 비활성화.
   - `RuleEditorDraftValidator` 가 입력 유효성 검사 (빈 값/잘못된 시간 등), `RuleOverrideSupersetValidator` 가 draft 매치 조건이 선택된 base 의 superset 인지 검증 (KEYWORD 는 token 포함, 그 외는 strict equality) 하여 helper text 로 경고.
   - 저장 → `RuleDraftFactory.create(..., overrideOf = ..., draft = ...)` 로 `RuleUiModel` 생성 → `rulesRepository.upsertRule(rule)` 만 수행. **신규 Rule** 은 `draft = true` 로 저장되어 RulesScreen 의 "작업 필요" 서브-버킷에 즉시 노출. **편집 모드** 는 기존 Rule 의 `draft` 값을 그대로 유지 (한 번 분류에 합류된 적 있으면 영구히 `draft = false`). 신규 Rule 저장 직후 `CategoryAssignBottomSheet` (ModalBottomSheet) 가 강제 노출되어 사용자에게 "이 규칙을 어떤 분류에 추가하시겠어요?" 를 묻고, 분류를 선택하면 `AssignRuleToCategoryUseCase.assign(ruleId, categoryId)` 가 (1) `CategoriesRepository.appendRuleIdToCategory` → (2) `RulesRepository.markRuleAsAssigned` (→ `draft = false`) 를 순서대로 수행. **편집 모드** 는 sheet 를 띄우지 않음. Sheet 의 두 dismiss CTA: **"작업 목록에 두기"** (default) 는 `draft = true` 그대로 유지 + sheet 만 닫음, **"분류 없이 보류"** 는 `RulesRepository.markRuleAsParked(ruleId)` 호출로 `draft = false` 로 flip. Outside-tap / system back 은 `draft` 값을 변경하지 않음 (sheet 닫음 자체는 의도 신호로 해석하지 않음). `RuleOverrideValidator` 가 self-reference / circular chain 을 감지하면 persist 없이 `Log.e` 후 reject.
   - 미분류 Rule 은 RulesScreen 의 액션 그룹 위에 두 sub-section 으로 분리되어 노출됨 (plan `2026-04-26-rule-explicit-draft-flag`):
     - **"작업 필요"** (`draft = true`) — `RuleRowPresentation.Unassigned(isParked = false)` + border-only "미분류" chip + "분류에 추가되기 전까지 비활성" 배너. 카드 탭 시 다시 sheet 가 열려 분류 합류를 재시도할 수 있음.
     - **"보류"** (`draft = false` && unassigned) — 같은 presentation 의 `isParked = true` 변형으로 "보류" chip + "사용자가 보류함 · 필요 시 작업으로 끌어올리세요" 배너. 카드 탭은 동일하게 sheet 재오픈, 카드 우하단의 `TextButton` "작업으로 끌어올리기" 가 `upsertRule(rule.copy(draft = true))` 를 호출해 row 를 다시 "작업 필요" 로 승격.
     - 두 sub-bucket 모두 비어있으면 어떤 SectionLabel 도 노출되지 않음 (regression guard).
7. 삭제 버튼 → `rulesRepository.deleteRule(id)`. 2026-04-22 plan `categories-runtime-wiring-fix` Task 5 (#245) 이후 `deleteRule` 이 Rule persist 후 `CategoriesRepository.onRuleDeleted(id)` 를 호출해 해당 rule id 를 모든 Category 의 `ruleIds` 에서 dedup 제거한다. `ruleIds` 가 비게 된 Category 는 **삭제하지 않고 보존** — 사용자가 분류 탭에서 "rule 없는 Category" 를 명시적으로 편집/삭제 결정.
8. 순서 이동 → drag handle long-press + vertical drag (48dp step) 또는 arrow 버튼 → `rulesRepository.moveRule(id, direction)`. `RuleOrdering` 이 tier key (`overrideOf`) 가 동일한 가장 가까운 이웃까지만 swap 하도록 non-tier rows 를 skip — base 는 base 끼리, override 는 같은 base 를 공유하는 sibling 끼리만 우선순위가 바뀌고, 다른 tier 와는 절대 섞이지 않음. (Category 간 순서 변경은 [categories-management](categories-management.md) 의 drag-reorder 로 수행.)
9. 활성 토글 → `rulesRepository.setRuleEnabled(id, enabled)`.

## Exit state

- DataStore 의 `smartnoti_rules` 에 룰 상태가 영속화됨 (8-column 포맷, 마지막 컬럼 `draft: Boolean`).
- 사용자가 sheet 에서 분류를 선택했다면 `AssignRuleToCategoryUseCase` 가 (1) `smartnoti_categories` 의 해당 Category 의 `ruleIds` 에 Rule.id 를 dedup-append + (2) Rule 의 `draft` 를 `false` 로 flip 까지 모두 수행. "작업 목록에 두기" 를 누르면 Rule 은 `draft = true` 미분류로 유지. "분류 없이 보류" 를 누르면 `draft = false` 로 flip 되지만 어떤 Category 의 `ruleIds` 에도 속하지 않음 — RulesScreen 의 "보류" 서브-버킷에 노출.
- 이후 새로 게시되는 알림은 `RulesRepository.currentRules()` + `CategoriesRepository.currentCategories()` 를 통해 업데이트된 조건 + 액션을 참조 (→ [notification-capture-classify](notification-capture-classify.md)). 미분류 Rule (`draft` 값과 무관) 은 owning Category 가 없으므로 classifier 가 user-routed 결정을 만들지 않음 (→ heuristic chain → SILENT). `draft` 는 순수 UI/UX hint 로, 분류기 hot path 는 이 필드를 읽지 않음.

## Out of scope

- 알림 카드 액션으로 인한 자동 룰 생성 (→ [rules-feedback-loop](rules-feedback-loop.md))
- 온보딩 quick-start 로 생성되는 초기 프리셋 룰 (→ [onboarding-bootstrap](onboarding-bootstrap.md))
- 룰이 실제로 분류에 어떻게 적용되는가 (→ [notification-capture-classify](notification-capture-classify.md))

## Code pointers

- `ui/screens/rules/RulesScreen` — 메인 화면 + 편집 다이얼로그
- `domain/usecase/RuleDraftFactory`
- `ui/screens/rules/RuleEditorDraftValidator`
- `ui/screens/rules/RuleEditorAppSuggestionBuilder`
- `ui/screens/rules/RuleEditorRepeatBundleThresholdController`
- `ui/screens/rules/RuleEditorOverrideOptionsBuilder` — override base 후보 dropdown 빌더
- `ui/screens/rules/RuleOverrideSupersetValidator` — override draft superset 검증
- `ui/screens/rules/RuleListPresentationBuilder`
- `ui/screens/rules/RuleListFilterApplicator`
- `ui/screens/rules/RuleListGroupingBuilder`
- `ui/screens/rules/RuleListHierarchyBuilder` — flat list → base/override tree (1-level)
- `domain/usecase/RuleOrdering` — tier-aware `moveRule` 이웃 탐색
- `domain/usecase/RuleOverrideValidator` — self-reference / cycle 감지
- `domain/usecase/RuleConflictResolver` — 동일 tier 타이-브레이크 + base/override 선택 (분류기 측)
- `ui/components/RuleRow` — `RuleRowPresentation.Base` / `Override` / `BrokenOverride`
- `data/rules/RulesRepository` — DataStore `smartnoti_rules`, override-aware upsert, `deleteRule` 이 persist 후 `CategoriesRepository.onRuleDeleted(id)` 로 cascade 호출
- `data/rules/RuleStorageCodec` — 8-column 포맷 (id|title|subtitle|type|enabled|matchValue|overrideOf|draft); legacy 7-column (no draft) → `draft = false`, pre-P1 8-column (action at index 4) → strip + `draft = false`
- `data/categories/CategoriesRepository` — editor 저장 시 owning Category 를 upsert
- `data/categories/CategoriesRepository#onRuleDeleted` — Rule 삭제 시 모든 Category 의 `ruleIds` 에서 dedup 제거 (empty `ruleIds` Category 는 보존)
- `data/categories/RuleDeletedCascade` — pure helper, strip logic 단위 테스트 대상
- `domain/usecase/RuleCategoryActionIndex` — rule id → owning Category.action 조회 (리스트 derive). null 반환 = "미분류"
- `domain/usecase/UnassignedRulesDetector` — pure helper. `(rules, categories) -> List<unassigned rules>` 로 미분류 bucket 계산
- `domain/usecase/UnassignedRulesPartitioner` — pure helper. detector 출력을 `Partition(actionNeeded, parked)` 로 split (RulesScreen 의 두 sub-section 이 사용). plan `2026-04-26-rule-explicit-draft-flag`
- `domain/usecase/AssignRuleToCategoryUseCase` — sheet 의 Category 선택 경로가 호출하는 단일 entry point. `appendRuleIdToCategory` → `markRuleAsAssigned` 두 op 의 ordered call + atomicity 책임. 같은 plan
- `data/rules/RulesRepository#markRuleAsAssigned` / `#markRuleAsParked` — `draft` flag flip setters (둘 다 `false` 로 flip; 분리된 entry point 는 호출 의도 분리용)
- `ui/components/RuleRow#RuleRowPresentation.Unassigned` — `data class Unassigned(isParked: Boolean)`. `isParked = false` → 작업 필요 톤, `isParked = true` → 보류 톤
- `ui/screens/rules/RulesScreen#UnassignedRuleRowSlot` — 두 sub-section 공용 row composable. parked 행에는 "작업으로 끌어올리기" TextButton 추가
- `ui/screens/rules/RulesScreen#CategoryAssignSheetContent` — 저장 직후 ModalBottomSheet 의 분류 선택 본문 + 두 dismiss CTA ("작업 목록에 두기" / "분류 없이 보류")
- `domain/model/Rule` — 순수 조건 매처 (action 필드 없음, Phase P1 Task 4)
- `domain/model/RuleUiModel` — `RuleTypeUi`, `RuleActionUi` (UI 편의 enum, storage 용 아님 — list filter / grouping 에서 owning Category.action 을 derive 해 표시할 때만 사용), nullable `overrideOf`
- `ui/screens/settings/SettingsScreen#AdvancedRulesEntryCard` — Settings 진입점 "고급 규칙 편집 열기"

## Tests

- `RuleEditorDraftValidatorTest`
- `RuleEditorOverrideOptionsBuilderTest`
- `RuleOverrideSupersetValidatorTest`
- `RuleListFilterApplicatorTest`
- `RuleListGroupingBuilderTest`
- `RuleListHierarchyBuilderTest`
- `RuleOrderingTest` — tier-aware 이웃 swap
- `RuleOverrideValidatorTest`
- `RuleConflictResolverTest`
- `RuleDraftFactoryTest` — `action` 파라미터 없는 시그니처 회귀 고정
- `RuleCategoryActionIndexTest` — 미분류 (어떤 Category 도 claim 하지 않은) Rule id 가 null 반환
- `RuleWithoutCategoryNoOpTest` — owning Category 없는 매치는 user-routed PRIORITY/DIGEST/IGNORE 로 promote 되지 않음 (heuristic chain → SILENT)
- `UnassignedRulesDetectorTest` — RulesScreen 의 미분류 섹션 멤버십 helper
- `UnassignedRulesPartitionerTest` — 미분류 출력의 draft 기준 split (`actionNeeded` / `parked`), 입력 순서 보존, claimed rule 배제
- `AssignRuleToCategoryUseCaseTest` — sheet 의 Category 선택 경로가 `appendRuleIdToCategory` → `markRuleAsAssigned` 순서로 ordered call, append 가 throw 하면 mark 호출 안 함
- `RulesRepositoryDeleteCascadeTest` — `deleteRule` 이 Category.ruleIds 에서 id 를 제거하고 빈 `ruleIds` Category 는 보존함

## Verification recipe

```bash
# 1. Settings 탭 → "고급" 섹션 → "고급 규칙 편집 열기" 탭
#    (BottomNav 에 "규칙" 탭은 더 이상 없음)
# 2. "새 규칙 추가" → 키워드 "인증번호" 입력 후 "추가"
#    → 액션 dropdown 은 더 이상 없음. 저장 직후 ModalBottomSheet 가
#    "이 규칙을 어떤 분류에 추가하시겠어요?" 를 묻고 두 dismiss CTA
#    ("작업 목록에 두기" / "분류 없이 보류") 를 함께 노출.
# 3. (Path A — 정상 합류) 기존 분류 (예: "중요 알림") "추가" 탭 →
#    AssignRuleToCategoryUseCase 가 appendRuleIdToCategory + draft=false
#    flip 을 ordered 로 수행. 미분류 sub-section 에서 사라짐.
# 4. 동일 키워드 알림 게시 → Home 의 StatPill 즉시 카운트 증가 확인
adb shell cmd notification post -S bigtext -t "은행" OtpTest "인증번호 123456"
adb shell am start -n com.smartnoti.app/.MainActivity

# 5. (Path B — "작업 목록에 두기") 저장 후 sheet 의 "작업 목록에 두기"
#    탭 → Rule 은 draft=true 미분류로 남음. RulesScreen 상단에 "작업
#    필요" 서브-섹션이 노출되며 border-only "미분류" chip + "분류에
#    추가되기 전까지 비활성" 배너로 row 가 표시됨. 동일 키워드 알림은
#    여전히 heuristic chain 으로 빠져 priority 로 routing 되지 않음.

# 6. (Path C — "분류 없이 보류") 미분류 row 탭 → sheet 재오픈 → "분류
#    없이 보류" 탭 → markRuleAsParked 호출로 draft=false flip. row 가
#    "작업 필요" 에서 "보류" 서브-섹션으로 이동 — "보류" chip + "사용자가
#    보류함" 배너 + 카드 우하단 "작업으로 끌어올리기" TextButton 노출.
#    분류기 동작은 Path B 와 동일 (heuristic chain → SILENT).

# 7. (Path D — "작업으로 끌어올리기") 보류 row 의 TextButton 탭 →
#    upsertRule 로 draft=true flip. row 가 다시 "작업 필요" 서브-섹션으로
#    이동. 두 sub-bucket 모두 비면 SectionLabel 은 노출되지 않음.
```

## Known gaps

- 룰 편집이 AlertDialog 기반이라 입력 화면이 좁음 — 복잡한 SCHEDULE/REPEAT_BUNDLE 편집이 답답할 수 있음.
- 룰 내보내기/가져오기 미구현.
- 동일 매치값 + 다른 액션 룰이 충돌할 때의 우선순위는 `RuleConflictResolver` 의 "동일 tier 는 `allRules` 인덱스 순 (earlier wins)" 규칙에 따름. UI 에서는 tier 내부 드래그 재배치로만 조정 가능.
- Override 체인은 1-level 만 지원 (base → override). `C → B → A` 같은 다단 체인은 `RuleListHierarchyBuilder` 가 top-level broken 으로 표시하고 분류기 측에서도 resolver 가 한 단계만 탐색. (plan `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Open question #3).
- Recipe fragility — baseline 누적 (2026-04-21 cycle): rules-feedback-loop 경로로 upsert 된 `person:*` 룰이 `smartnoti_rules.preferences_pb` 에 영속화되어 이 recipe 의 "활성 규칙 N개" 헤더 baseline 이 cycle 에 따라 증가한다. 검증 시 추가/삭제 delta (N→N+1, N+1→N) 만 관측하고, 절대값(N) 은 테스트 환경 상태에 따라 달라질 수 있다.
- ADB-only verification 으로는 override pill / nested tree indent / tier-aware drag-reorder 관찰 불가 (2026-04-22 sweep): (a) 현 recipe 가 override 룰을 만들지 않아 DB 에 nested 대상이 없고, (b) `adb shell input text` 가 한글 미지원이라 한글 base (`광고`, `엄마`, `중요 알림` 등) 를 strict-equality 로 override 하는 ASCII 값이 없으며, (c) same-value ASCII 시도 (e.g. `TestSender_0421_T12` → `TestSender_0421_T12`) 는 `RuleOverrideValidator` 의 SELF_REFERENCE 로 정상 reject. Phase C UI 재검증은 instrumentation test (`RulesScreenTest` 혹은 Compose UI test) 또는 사전-seed 된 override 룰이 포함된 fixture 로 이동하는 것이 안정적.
- (resolved 2026-04-22, plan `categories-runtime-wiring-fix` Task 5 / #245) **Rule 삭제 시 Category.ruleIds cascade** — `deleteRule` 이 `CategoriesRepository.onRuleDeleted(id)` 를 호출해 해당 id 를 모든 Category 의 `ruleIds` 에서 dedup 제거. `ruleIds` 가 비게 된 Category 는 보존 (empty-Category GC 는 별도 과제). 더 이상 dangling reference 가 쌓이지 않음.
- (resolved 2026-04-24, plan `2026-04-24-rule-editor-remove-action-dropdown`) **Rule 조건 편집 시 action 변경** — editor 에서 action dropdown 자체가 사라졌고, 저장 경로가 `upsertRule` 단일 persist 만 호출하므로 두-개-persist 의 원자성 누락 문제 자체가 사라짐. action 변경은 이제 분류 탭 Category editor 에서 단일 `upsertCategory` 로만 가능.
- (resolved 2026-04-26, plan `2026-04-26-rule-explicit-draft-flag`) **신규 미분류 Rule sheet 거부 흐름** — Rule 에 영속 `draft: Boolean` 필드가 추가되어 "이 Rule 은 영원히 미분류로 두겠다" (sheet "분류 없이 보류" CTA, draft=false) 와 "sheet 를 깜빡 닫았다" (sheet "작업 목록에 두기" CTA, draft=true 유지) 가 storage 레벨에서 구분된다. RulesScreen 의 미분류 섹션은 "작업 필요" + "보류" 두 sub-section 으로 분리되어 사용자가 의도적 보류와 미처리 draft 를 한눈에 구별할 수 있고, 보류 row 의 "작업으로 끌어올리기" TextButton 으로 다시 작업 필요 로 승격 가능. classifier 동작은 변경 없음 (둘 다 owning Category 없음 → SILENT fall-through; `draft` 는 순수 UI/UX hint).
- **bulk 미분류 분류** 미지원 — 미분류 Rule N 개를 한 분류에 한꺼번에 할당하는 흐름은 현재 없음. 한 건씩 sheet 를 거쳐야 함. 후속 plan.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Rule editor 에 IGNORE (무시) 액션 추가 — dropdown 라벨 "무시 (즉시 삭제)", `RuleListPresentationBuilder` 가 IGNORE 룰이 존재할 때만 overview/filter 세그먼트 노출, `RuleListGroupingBuilder` 가 "무시" 섹션을 tier 스택 최하단에 emit, `RuleRow` 의 action chip 이 IGNORE 에 한해 border-only 중립 회색 톤으로 렌더. 코덱/설명/그룹/프레젠테이션 단위 테스트 보강. IGNORE 룰이 매치된 알림은 `NotificationClassifier` 가 `NotificationDecision.IGNORE` 를 반환 (#179 `5f516d6`), `SmartNotiNotificationListenerService` 가 tray cancel 만 수행하고 replacement alert 는 건너뜀 (#181 `fb532c1`), 기본 뷰는 IGNORE 를 제외하고 Settings opt-in 토글 시에만 [ignored-archive](ignored-archive.md) 에 노출 (#185 `9a5b4b9`). Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Tasks 1-6 (#178 `6aad9d5`, #179 `5f516d6`, #181 `fb532c1`, #183 `9a65992`, #185 `9a5b4b9`, #187 `57df6ac`). `last-verified` 는 ADB 검증 실행 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-21: Phase C (hierarchical rules v2) 반영 — `RuleUiModel.overrideOf` 필드 추가, `RuleStorageCodec` 8-column 포맷, `RuleOverrideValidator` 가 self-reference / circular chain reject (#148). Rule editor AlertDialog 에 "예외 규칙" 섹션 + base dropdown + `RuleOverrideSupersetValidator` helper text (#151). Rules 탭이 `RuleListHierarchyBuilder` 로 base/override tree 렌더, `RuleRow` 에 `Base` / `Override` / `BrokenOverride` presentation 분기 (#150). Drag-to-reorder 가 `RuleOrdering` 으로 tier-aware swap 만 허용 (#152). 1-level override 체인만 지원 — Known gaps 참고. Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase C Task 1/3/4/5. `last-verified` 는 실제 recipe 재실행 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
- 2026-04-22: Fresh APK (build 2026-04-22) re-verify on emulator-5554. PASS — recipe 의 키워드 "인증번호" 포스트 → Home StatPill 즉시 카운트 16→17 로 반영 확인. Phase C 관측: 편집 다이얼로그 "예외 규칙" switch 토글 시 base dropdown 이 9개 candidate (`중요 알림`, `프로모션 알림`, `반복 알림`, `엄마`, `TestSender_0421_T11`, `TestSender_0421_T12`, `SilentTest_0421_T1`, `광고`, `IgnoreTestRule`) 를 채워 렌더. Action dropdown 에 IGNORE "무시 (즉시 삭제)" chip 노출, 리스트 상단 filter 에 "무시 1" chip 및 최하단 tier 섹션 "무시 / 규칙 1개" 렌더. 그룹 순서: 즉시 전달 3 → Digest 4 → 조용히 1 → 무시 1 (doc 과 일치). Override pill / nested indent / drag-reorder 는 이번 세션에서 end-to-end exercise 불가 — 현 DB 에 override 룰이 없고, strict-equality 타입 (PERSON/APP/SCHEDULE/REPEAT_BUNDLE) 은 same-value override 시 `RuleOverrideValidator` 가 SELF_REFERENCE 로 reject 되므로 (base.id == draft.id), KEYWORD superset 또는 한글 입력이 가능한 instrumentation 환경에서 재검증 필요. Known gaps 에 observability 제한 기록.
- 2026-04-22: **Rule/Category 분리 + Settings 격하** 반영 — Rule 모델에서 action 필드 제거 (Phase P1 Task 4), action 은 Category 가 소유. Rules 탭은 BottomNav 에서 제거되어 Settings "고급 규칙 편집" 서브메뉴로 진입. RulesScreen 의 액션 chip / filter / grouping 은 `RuleCategoryActionIndex` 로 owning Category 의 action 을 derive 해 기존 UI 계약 유지. 저장 시 Rule + 대응 Category 를 동시 upsert. Title/Goal/Trigger/Observable steps/Code pointers 전면 갱신. Plan: `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Tasks 1-4 (#236), P3 Task 11 (#240). `last-verified` 는 Settings 진입점 재-ADB 검증 전까지 현 일자 유지 (이전 sweep 은 레거시 BottomNav "규칙" 탭 기반 recipe 로 수행됨).
- 2026-04-22: **Rule 삭제 cascade drift 해소** — `RulesRepository.deleteRule` 이 Rule persist 완료 후 `CategoriesRepository.onRuleDeleted(id)` 를 호출해 모든 Category 의 `ruleIds` 에서 해당 rule id 를 dedup 제거. `RuleDeletedCascade` pure helper 로 strip 로직 분리, `RulesRepositoryDeleteCascadeTest` 로 계약 고정. `ruleIds` 가 비게 된 Category 는 "rule-less" 상태로 보존 (empty-Category GC 는 별도 과제). 더 이상 dangling Rule id 가 Category 에 남지 않음. Plan: `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 5 (#245), Task 7 (this PR). `last-verified` 변경 없음.
- 2026-04-22: **`smartnoti_rules` DataStore 단일 소유자 정리** — `LegacyRuleActionReader` 가 고유 `preferencesDataStore("smartnoti_rules")` delegate 를 선언해 AndroidX 의 "one delegate per file" 제약을 위반, 앱 launch 즉시 `IllegalStateException: There are multiple DataStores active for the same file …/smartnoti_rules.preferences_pb` 로 크래시. Fix: `RulesRepository` 가 유일 소유자로서 `internal val dataStore: DataStore<Preferences>` 로 handle 을 노출하고 `LegacyRuleActionReader` 는 생성자 주입으로 그 handle 을 받는다. `MigrateRulesToCategoriesRunner.create` 의 wiring 한 줄 (`LegacyRuleActionReader(rulesRepository.dataStore)`) 이 유일한 호출부. Robolectric regression (`RulesDataStoreSingleOwnerTest`) 로 회귀 고정. ADB cold-launch smoke PASS — logcat 에 `IllegalStateException` / `smartnoti_rules.preferences_pb` 흔적 없음, onboarding 권한 화면 정상 렌더. Plan: `docs/plans/2026-04-22-rules-datastore-dedup-fix.md` (this PR). `last-verified` 변경 없음.
- 2026-04-24: v1 loop tick re-verify on emulator-5554 (APK `versionName=0.1.0`, listener enabled). Recipe step 3 — `cmd notification post -S bigtext -t '은행' OtpTest '인증번호 123456'` — relaunched `com.smartnoti.app/.MainActivity`; Home StatPill advanced `오늘 알림 62→63` and `즉시 20→21` (Digest 11 / 조용히 31 unchanged), confirming the existing keyword-인증번호 priority rule still routes `com.android.shell` posts through the listener → classifier → repository chain end-to-end. Initial second post showed cached pre-recompose UI; counts only updated after `am start` re-foregrounded MainActivity (HomeViewModel re-collected `observePriority`/`observeAllFiltered`). No change to Observable steps 1-9 / Exit state — Settings 진입점 (`AdvancedRulesEntryCard`) and editor dialog not re-traversed this sweep (covered by 2026-04-22 sweep + Phase C unit tests). DRIFT 없음. `last-verified` 2026-04-22 → 2026-04-24.
- 2026-04-25: ADB end-to-end re-verify of D3 (`rule-editor-remove-action-dropdown`, #317) on emulator-5554 (APK rebuilt 2026-04-25 09:40, `versionName=0.1.0`). PASS — Settings 진입점 → Rules 화면 → "새 규칙 추가" 다이얼로그가 `기본 정보 / 규칙 타입 / 예외 규칙` 3개 섹션만 노출 (구 "처리 방식" SectionLabel + RuleActionUi dropdown 부재 확인). 키워드 타입 + 이름 `VerifyTest_0425` + 매치값 `verifyotp` 로 저장 → 즉시 `CategoryAssignBottomSheet` 가 "이 규칙을 어떤 분류에 추가하시겠어요?" + 3개 기존 분류 (중요 알림 즉시 전달, 프로모션 알림 Digest, 반복 알림 Digest) + "나중에 분류에 추가" skip CTA 로 노출. (a) Skip 경로: "활성 규칙 3→4", 액션 그룹 위에 신규 "미분류" 섹션이 `RuleRowPresentation.Unassigned` (border-only "미분류" chip + "분류에 추가되기 전까지 비활성" 배너) 로 렌더. (b) 미분류 카드 탭 → 동일 sheet 재오픈 확인. (c) sheet 에서 "중요 알림" 옆 "추가" 탭 → "미분류" 섹션 사라지고 "즉시 전달 규칙 1→2" 로 이동, "중요 알림" Category.ruleIds 합류 확인. 테스트 후 신규 Rule 삭제로 baseline 복원. DRIFT 없음 — Observable steps 6 의 sheet 흐름 + 미분류 섹션 + 카드 재탭 모두 doc 과 일치. `last-verified` 2026-04-24 → 2026-04-25.
- 2026-04-26: **명시적 `draft: Boolean` 플래그 도입** — `RuleUiModel` / `Rule` 도메인 모델에 `draft: Boolean = false` 필드 추가 (default false 라 legacy 7-column row 는 자동으로 "보류" 서브-버킷으로 마이그레이션). `RuleStorageCodec` 8-column 포맷 (id|title|subtitle|type|enabled|matchValue|overrideOf|draft) 으로 확장 + legacy 7-column / pre-P1 8-column-with-action tolerance. 신규 `AssignRuleToCategoryUseCase` 가 sheet 의 Category 선택 경로를 단일화 — `CategoriesRepository.appendRuleIdToCategory` → `RulesRepository.markRuleAsAssigned` (draft=false flip) 두 op 의 ordered call + atomicity. `markRuleAsParked` setter 는 "분류 없이 보류" CTA 전용 entry point (storage 효과는 markRuleAsAssigned 와 동일하지만 호출 의도 분리). RulesScreen 의 단일 "미분류" 섹션이 `UnassignedRulesPartitioner` 를 통해 두 sub-section ("작업 필요" `draft=true` / "보류" `draft=false`) 으로 분리되어 렌더; 두 sub-bucket 이 모두 비면 어떤 SectionLabel 도 노출 안 함 (regression guard). `CategoryAssignBottomSheet` 의 dismiss CTA 가 "작업 목록에 두기" (default, draft 변경 없음) + "분류 없이 보류" (markRuleAsParked) 두 개로 분기. "보류" row 에는 우하단 `TextButton` "작업으로 끌어올리기" 가 노출되어 `upsertRule(rule.copy(draft = true))` 로 다시 작업 필요 로 승격. 신규 단위 테스트: `UnassignedRulesPartitionerTest`, `AssignRuleToCategoryUseCaseTest`, `RuleStorageCodecTest` (4개 신규 케이스), `RuleWithoutCategoryNoOpTest` (2개 신규 케이스 — draft=true/false 모두 SILENT fall-through 고정). Plan: `docs/plans/2026-04-26-rule-explicit-draft-flag.md` Tasks 1-7 (this PR). `last-verified` 변경 없음 — ADB end-to-end 검증은 후속 sweep 에서 수행 예정.
- 2026-04-26: ADB end-to-end re-verify of `2026-04-26-rule-explicit-draft-flag` (#357) on emulator-5554 (APK rebuilt 2026-04-26 15:27, `versionName=0.1.0`, reinstalled mid-sweep — initial dump on stale 14:33 build still showed legacy single "나중에 분류에 추가" CTA, fresh install resolved). PASS — Settings 진입점 → Rules 화면. (a) baseline 3개 룰 (활성 규칙 3개), 미분류 sub-section 부재. (b) 새 키워드 룰 `ActionNeededTest_0426` (matchValue `actionneededkw`) 저장 → `CategoryAssignBottomSheet` 가 두 dismiss CTA "분류 없이 보류" + "작업 목록에 두기" 노출 (단일 "나중에 분류에 추가" CTA 가 둘로 split 된 것 확인). (c) "작업 목록에 두기" 탭 → "활성 규칙 5개", 액션 그룹 위에 **"작업 필요"** SectionLabel + 설명 "분류에 추가되기 전까지 어떤 알림도 분류하지 않아요…" + ActionNeededTest_0426 row (border-only "미분류" chip + "분류에 추가되기 전까지 비활성" 배너) 렌더. (d) 사전에 OLD APK 의 단일 "나중에 분류에 추가" 경로로 만든 `DraftFlagTest_0426` 룰이 자동으로 **"보류"** sub-section 에 노출 (사용자가 보류함 · 필요 시 작업으로 끌어올리세요 배너 + "보류" chip + 카드 우하단 "작업으로 끌어올리기" TextButton) — 두 sub-bucket 동시 렌더 확인 (작업 필요 → 보류 순서). (e) "작업으로 끌어올리기" 탭 → DraftFlagTest_0426 가 보류 → 작업 필요 로 이동, "보류" SectionLabel 사라짐 (regression guard PASS — 빈 sub-bucket 은 라벨 노출 안 함). 테스트 후 두 신규 Rule 삭제로 baseline 복원 (활성 규칙 3개). DRIFT 없음 — Observable steps 6 의 두-CTA sheet + 두 sub-section partition + promote 어포던스 모두 doc 과 일치. `last-verified` 2026-04-25 → 2026-04-26.
- 2026-04-24: **Rule editor 액션 dropdown 제거 + 미분류 draft 도입** — 고급 규칙 편집의 AlertDialog 에서 "처리 방식" SectionLabel + `EnumSelectorRow(RuleActionUi …)` + `draftAction` state + 저장 시 1:1 companion Category auto-upsert 블록을 hard remove. Rule 은 순수 조건 매처로만 살아남고, 저장 직후 `CategoryAssignBottomSheet` (ModalBottomSheet, 신규 Rule 한정) 가 강제 노출되어 기존 분류 하나를 선택하면 `CategoriesRepository.appendRuleIdToCategory` 로 합류. sheet 를 닫으면 Rule 은 "미분류" draft 로 남고 어떤 알림도 분류하지 않음 (storage 변경 없이 derive — `RuleCategoryActionIndex.actionFor` → null → classifier 의 `owning` empty → SILENT fall-through). RulesScreen 의 액션 그룹 위에 별도 "미분류" 섹션이 노출되며 `RuleRowPresentation.Unassigned` 가 border-only "미분류" chip + "분류에 추가되기 전까지 비활성" 배너로 렌더 — row 탭 시 다시 sheet 가 열림. `RuleDraftFactory.create()` 시그니처에서 `action` 파라미터를 제거 (onboarding preset 호출부 + 단위 테스트 동기화). 신규 helper `UnassignedRulesDetector` (pure) + `RuleRowPresentation.Unassigned`. 새 단위 테스트 4건 (`RuleDraftFactoryTest`, `RuleCategoryActionIndexTest`, `RuleWithoutCategoryNoOpTest`, `UnassignedRulesDetectorTest`) 으로 contract 고정. Plan: `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md` Tasks 1-7 (this PR). `last-verified` 변경 없음 — 미분류 sheet 의 ADB end-to-end 검증은 후속 sweep 에서 수행.
