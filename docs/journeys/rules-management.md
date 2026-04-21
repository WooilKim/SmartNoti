---
id: rules-management
title: 규칙 CRUD
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

사용자가 발신자 / 앱 / 키워드 / 스케줄 / 반복묶음 규칙을 직접 만들고 편집/삭제/순서 조정/필터링 해서 분류 파이프라인의 입력으로 활용한다.

## Preconditions

- 온보딩 완료

## Trigger

사용자가 하단 네비의 "규칙" 탭 탭 → `Routes.Rules` 네비게이션.

## Observable steps

1. `RulesScreen` 마운트, 다음 상태 구독:
   - `RulesRepository.observeRules()` — 현재 룰 목록
   - `NotificationRepository.observeCapturedAppsFiltered(...)` — 앱 선택 제안용
   - `SettingsRepository.observeSettings()` — persistent 알림 필터 설정
2. 상단 `ScreenHeader` + "직접 규칙 추가" 카드의 "새 규칙 추가" 버튼.
3. "활성 규칙 N개" 카드 + `FilterChip` (ALWAYS_PRIORITY / DIGEST / SILENT) — `RuleListFilterApplicator` 가 선택된 액션으로 필터링.
4. 액션별로 그룹화된 리스트 (`RuleListGroupingBuilder`). 그룹 내부에서 `RuleListHierarchyBuilder` 가 flat list 를 tree 로 재조립 — base rule 아래 해당 rule 을 `overrideOf` 로 가리키는 override rule 이 자식으로 nest. 삭제/필터링된 base 를 참조하는 override 는 top-level 에 "기본 규칙을 찾을 수 없음" broken indicator 와 함께 렌더.
5. `RuleRow` 는 depth 에 따라 indent (16dp per level), override 는 primary tint pill "이 규칙의 예외 · {base 이름}" 라벨을, broken override 는 error tint 경고 문구를 제목 옆에 표시. 제목 / 매치값 / 액션 / 순서 이동 (drag handle + arrow fallback) / 편집 / 삭제 어포던스는 기존과 동일.
6. 추가 / 편집 탭 → `AlertDialog` (rule editor) 열림:
   - 룰 타입 (`RuleTypeUi`: PERSON / APP / KEYWORD / SCHEDULE / REPEAT_BUNDLE) 선택
   - 매치 값 입력 (타입별: 이름, 패키지, 키워드, 시간 범위, 반복 임계)
   - 액션 선택 (`RuleActionUi`)
   - "예외 규칙" 섹션 — "기존 규칙의 예외로 만들기" `Switch` + (ON 일 때) `RuleEditorOverrideOptionsBuilder` 가 만든 base 후보 dropdown. 후보가 없으면 switch 자체 비활성화.
   - `RuleEditorDraftValidator` 가 입력 유효성 검사 (빈 값/잘못된 시간 등), `RuleOverrideSupersetValidator` 가 draft 매치 조건이 선택된 base 의 superset 인지 검증 (KEYWORD 는 token 포함, 그 외는 strict equality) 하여 helper text 로 경고.
   - 저장 → `RuleDraftFactory.create(..., overrideOf = ...)` 로 `RuleUiModel` 생성 → `rulesRepository.upsertRule(rule)`. `RuleOverrideValidator` 가 self-reference / circular chain 을 감지하면 persist 없이 `Log.e` 후 reject.
7. 삭제 버튼 → `rulesRepository.deleteRule(id)`.
8. 순서 이동 → drag handle long-press + vertical drag (48dp step) 또는 arrow 버튼 → `rulesRepository.moveRule(id, direction)`. `RuleOrdering` 이 tier key (`overrideOf`) 가 동일한 가장 가까운 이웃까지만 swap 하도록 non-tier rows 를 skip — base 는 base 끼리, override 는 같은 base 를 공유하는 sibling 끼리만 우선순위가 바뀌고, 다른 tier 와는 절대 섞이지 않음.
9. 활성 토글 → `rulesRepository.setRuleEnabled(id, enabled)`.

## Exit state

- DataStore 의 `smartnoti_rules` 에 룰 상태가 영속화됨.
- 이후 새로 게시되는 알림은 `RulesRepository.currentRules()` 를 통해 업데이트된 룰을 참조 (→ [notification-capture-classify](notification-capture-classify.md)).

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
- `data/rules/RulesRepository` — DataStore `smartnoti_rules`, override-aware upsert
- `data/rules/RuleStorageCodec` — 8-column 포맷 (legacy 7-column tolerant)
- `domain/model/RuleUiModel` — `RuleTypeUi`, `RuleActionUi`, nullable `overrideOf`

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
- `RuleDraftFactoryTest` (있다면)

## Verification recipe

```bash
# 1. 규칙 탭에서 "새 규칙 추가" → 키워드 "인증번호" + ALWAYS_PRIORITY 저장
# 2. 이후 동일 키워드 알림이 Priority 로 분류되는지 확인
adb shell cmd notification post -S bigtext -t "은행" OtpTest "인증번호 123456"
# 3. Home 의 StatPill 즉시 카운트 또는 Priority 탭에 반영 확인
```

## Known gaps

- 룰 편집이 AlertDialog 기반이라 입력 화면이 좁음 — 복잡한 SCHEDULE/REPEAT_BUNDLE 편집이 답답할 수 있음.
- 룰 내보내기/가져오기 미구현.
- 동일 매치값 + 다른 액션 룰이 충돌할 때의 우선순위는 `RuleConflictResolver` 의 "동일 tier 는 `allRules` 인덱스 순 (earlier wins)" 규칙에 따름. UI 에서는 tier 내부 드래그 재배치로만 조정 가능.
- Override 체인은 1-level 만 지원 (base → override). `C → B → A` 같은 다단 체인은 `RuleListHierarchyBuilder` 가 top-level broken 으로 표시하고 분류기 측에서도 resolver 가 한 단계만 탐색. (plan `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Open question #3).
- Recipe fragility — baseline 누적 (2026-04-21 cycle): rules-feedback-loop 경로로 upsert 된 `person:*` 룰이 `smartnoti_rules.preferences_pb` 에 영속화되어 이 recipe 의 "활성 규칙 N개" 헤더 baseline 이 cycle 에 따라 증가한다. 검증 시 추가/삭제 delta (N→N+1, N+1→N) 만 관측하고, 절대값(N) 은 테스트 환경 상태에 따라 달라질 수 있다.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Phase C (hierarchical rules v2) 반영 — `RuleUiModel.overrideOf` 필드 추가, `RuleStorageCodec` 8-column 포맷, `RuleOverrideValidator` 가 self-reference / circular chain reject (#148). Rule editor AlertDialog 에 "예외 규칙" 섹션 + base dropdown + `RuleOverrideSupersetValidator` helper text (#151). Rules 탭이 `RuleListHierarchyBuilder` 로 base/override tree 렌더, `RuleRow` 에 `Base` / `Override` / `BrokenOverride` presentation 분기 (#150). Drag-to-reorder 가 `RuleOrdering` 으로 tier-aware swap 만 허용 (#152). 1-level override 체인만 지원 — Known gaps 참고. Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase C Task 1/3/4/5. `last-verified` 는 실제 recipe 재실행 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
