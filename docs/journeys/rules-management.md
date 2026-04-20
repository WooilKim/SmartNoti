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
4. 액션별로 그룹화된 리스트 (`RuleListGroupingBuilder`) — 각 그룹 내 `RuleRow` 가 제목/매치값/액션/우선순위 이동 버튼/편집/삭제 제공.
5. 추가 / 편집 탭 → `AlertDialog` (rule editor) 열림:
   - 룰 타입 (`RuleTypeUi`: PERSON / APP / KEYWORD / SCHEDULE / REPEAT_BUNDLE) 선택
   - 매치 값 입력 (타입별: 이름, 패키지, 키워드, 시간 범위, 반복 임계)
   - 액션 선택 (`RuleActionUi`)
   - `RuleEditorDraftValidator` 가 입력 유효성 검사 (빈 값/잘못된 시간 등)
   - 저장 → `ruleFactory.build(...)` 로 `RuleUiModel` 생성 → `rulesRepository.upsertRule(rule)`
6. 삭제 버튼 → `rulesRepository.deleteRule(id)`.
7. 순서 이동 → `rulesRepository.moveRule(id, direction)`.
8. 활성 토글 → `rulesRepository.setRuleEnabled(id, enabled)`.

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
- `ui/screens/rules/RuleListPresentationBuilder`
- `ui/screens/rules/RuleListFilterApplicator`
- `ui/screens/rules/RuleListGroupingBuilder`
- `ui/components/RuleRow`
- `data/rules/RulesRepository` — DataStore `smartnoti_rules`
- `domain/model/RuleUiModel` — `RuleTypeUi`, `RuleActionUi`

## Tests

- `RuleEditorDraftValidatorTest`
- `RuleListFilterApplicatorTest`
- `RuleListGroupingBuilderTest`
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
- 동일 매치값 + 다른 액션 룰이 충돌할 때의 우선순위는 `RuleListFilterApplicator` / 분류기 내부 규칙에 암묵적으로 의존.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
