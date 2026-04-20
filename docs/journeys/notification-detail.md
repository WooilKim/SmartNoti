---
id: notification-detail
title: 알림 상세 및 피드백 액션
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
---

## Goal

한 건의 알림에 대해 SmartNoti 가 어떻게 판단했고 어떻게 전달했는지 투명하게 보여주고, 즉석에서 재분류해 학습시킬 수 있는 단일 상세 화면을 제공한다.

## Preconditions

- 해당 `notificationId` 가 DB 에 존재 (→ [notification-capture-classify](notification-capture-classify.md))
- 온보딩 완료

## Trigger

- Home / Priority / Digest / Hidden / Insight 어디서든 카드 탭 → `navController.navigate(Routes.Detail.create(id))`
- replacement 알림 본문 탭 → 딥링크로 parent route + detail 연결 (→ [digest-suppression](digest-suppression.md))

## Observable steps

1. `NotificationDetailScreen` composable 마운트, `repository.observeNotification(id)` 구독.
2. `DetailTopBar` 렌더 — 좌측 뒤로가기 `IconButton` + 타이틀 "알림 상세".
3. 대상 알림이 null 이면 `EmptyState("알림을 찾을 수 없어요", ...)` 를 보여주고 return.
4. 존재하면 섹션 순서대로 렌더링:
   - 알림 요약 카드 — appName, sender/title, body, `StatusBadge(status)`
   - "왜 이렇게 처리됐나요?" — `ReasonChipRow(reasonTags)` (FlowRow 이므로 줄바꿈)
   - (선택) 온보딩 추천 반영 카드 — `NotificationDetailOnboardingRecommendationSummaryBuilder`
   - (선택) "어떻게 전달되나요?" — `NotificationDetailDeliveryProfileSummaryBuilder` 결과. 전달 모드/소리/진동/Heads-up/잠금화면 라벨
   - (선택) "원본 알림 처리 상태" — `NotificationDetailSourceSuppressionSummaryBuilder` 결과 (원본 상태 / 대체 알림 여부)
   - "이 알림 학습시키기" 카드 — 3개 액션 버튼
5. 액션 탭:
   - `중요로 고정` → `RuleActionUi.ALWAYS_PRIORITY`
   - `Digest로 보내기` → `RuleActionUi.DIGEST`
   - `조용히 처리` → `RuleActionUi.SILENT`

   내부 처리:
   1. `NotificationFeedbackPolicy.applyAction(notification, action)` → status/reasonTags 업데이트된 UI 모델 반환
   2. `NotificationRepository.updateNotification(updated)` → DB upsert
   3. `NotificationFeedbackPolicy.toRule(notification, action)` → 새 `RuleUiModel` (sender 있으면 PERSON, 없으면 APP)
   4. `RulesRepository.upsertRule(rule)` — 다음 동일 알림부터 자동 적용

## Exit state

- DB 의 알림 row 는 새로운 status/태그로 업데이트됨 (해당 리스트 화면 자동 갱신).
- RulesRepository 에 매칭 룰이 있다면 upsert, 없으면 insert.
- 사용자는 뒤로가기로 원래 화면(Priority/Digest/Hidden/Home)으로 복귀.

## Out of scope

- 룰 편집/삭제 (→ [rules-management](rules-management.md))
- 보호 대상 알림의 원본 유지 보장 (→ [protected-source-notifications](protected-source-notifications.md))
- 알림 자체의 분류 결정 (→ [notification-capture-classify](notification-capture-classify.md))

## Code pointers

- `ui/screens/detail/NotificationDetailScreen`
- `domain/usecase/NotificationFeedbackPolicy` — applyAction + toRule
- `domain/usecase/NotificationDetailDeliveryProfileSummaryBuilder`
- `domain/usecase/NotificationDetailOnboardingRecommendationSummaryBuilder`
- `domain/usecase/NotificationDetailSourceSuppressionSummaryBuilder`
- `data/local/NotificationRepository#observeNotification`, `#updateNotification`
- `data/rules/RulesRepository#upsertRule`

## Tests

- `NotificationFeedbackPolicyTest` — sender → PERSON, else → APP 규칙 생성
- `NotificationDetailDeliveryProfileSummaryBuilderTest` (있다면)

## Verification recipe

```bash
# 1. 알림 게시 → 분류 → Detail 진입
adb shell cmd notification post -S bigtext -t "광고" Promo1 "오늘의 이벤트"
adb shell am start -n com.smartnoti.app/.MainActivity
# Priority/Digest/Hidden 어느 화면에서든 카드 탭

# 2. "중요로 고정" 탭 → 해당 알림이 Priority 로 이동했는지 확인
# 3. 다음 동일 signature 알림을 보내 자동 Priority 로 분류되는지 확인
```

## Known gaps

- 재분류 시 토스트/확인 UI 없음 (상태만 바뀌어 UX 가 조용함).
- Detail 내부에서 "룰 보기" 바로가기 부재 — 룰이 저장됐는지 즉시 확인하려면 Rules 탭으로 수동 이동 필요.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
