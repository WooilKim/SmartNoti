---
id: rules-feedback-loop
title: 알림 피드백 → 룰 저장
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

Digest/Silent replacement 알림 또는 Detail 화면의 "중요로 고정 / Digest로 유지 / 조용히 유지" 액션을 한 번만 눌러도, 해당 알림의 상태를 바꾸는 동시에 동일 유형의 룰을 자동으로 저장해 다음부터는 사용자가 관여하지 않아도 되게 한다.

## Preconditions

- 대상 알림이 DB 에 존재
- replacement 알림 경로의 경우 POST_NOTIFICATIONS 권한 허용

## Trigger

- replacement 알림의 액션 버튼 탭 → `PendingIntent.getBroadcast` 로 `SmartNotiNotificationActionReceiver` 기동
- [notification-detail](notification-detail.md) 의 버튼 탭 → 같은 `NotificationFeedbackPolicy` 사용

## Observable steps

1. Broadcast 수신 시 `intent.action` 을 확인: `ACTION_PROMOTE_TO_PRIORITY` / `ACTION_KEEP_DIGEST` / `ACTION_KEEP_SILENT` 중 하나.
2. `SmartNotiNotificationActionReceiver.onReceive` 가 goAsync 로 코루틴 launch:
   1. notificationId, replacementNotificationId extras 파싱
   2. `NotificationRepository.observeAll().first()` 에서 해당 notificationId 찾음
   3. `NotificationFeedbackPolicy.applyAction(notification, action)` → status + reasonTags 업데이트된 UI 모델 생성 (reasonTag "사용자 규칙" 추가)
   4. `NotificationRepository.updateNotification(updated)` 로 DB 반영
   5. `NotificationFeedbackPolicy.toRule(notification, action)` 로 새 룰 생성:
      - sender 존재 → `RuleTypeUi.PERSON`, matchValue = sender
      - sender 없음 → `RuleTypeUi.APP`, matchValue = packageName
      - rule id = `"{type.lowercase()}:{matchValue}"` (동일 키는 upsert 로 덮어씀)
   6. `RulesRepository.upsertRule(rule)`
   7. `notificationManager.cancel(replacementNotificationId)` — replacement 알림 제거 (Detail 경로에선 생략)
3. 이후 같은 sender/앱으로 새 알림이 들어오면 (→ [notification-capture-classify](notification-capture-classify.md)) 룰 매치로 동일 decision 이 자동 적용됨.

## Exit state

- DB: 대상 알림의 status/reasonTags 업데이트됨 ("사용자 규칙" 태그 포함).
- RulesRepository: 매칭되는 룰이 upsert 됨 (동일 id 면 덮어씀, 처음이면 신규 삽입).
- 알림센터: replacement 알림 제거 (Broadcast 경로만).
- 후속 동일 유형 알림은 사용자 개입 없이 정책이 자동 적용됨.

## Out of scope

- 수동 룰 편집/삭제 (→ [rules-management](rules-management.md))
- 피드백 액션이 실제로 어떤 UI 에서 트리거되는지 (→ [notification-detail](notification-detail.md), [digest-suppression](digest-suppression.md))

## Code pointers

- `notification/SmartNotiNotificationActionReceiver`
- `notification/SmartNotiNotifier#ACTION_*` 상수, `notifier.addAction(...)` 에서 연결
- `domain/usecase/NotificationFeedbackPolicy` — applyAction + toRule
- `data/rules/RulesRepository#upsertRule`
- `data/local/NotificationRepository#updateNotification`

## Tests

- `SmartNotiNotificationActionReceiverTest` — 각 액션이 올바른 status/룰 조합을 만드는지
- `NotificationFeedbackPolicyTest` — sender / packageName 룰 생성 분기

## Verification recipe

가장 쉬운 경로는 **Detail 화면의 "중요로 고정" / "Digest로 보내기" / "조용히 처리" 버튼** — 동일한 `NotificationFeedbackPolicy.applyAction` + `RulesRepository.upsertRule` 경로를 탑니다 (→ [notification-detail](notification-detail.md)). 아래 A 로 UI 검증, B 는 broadcast 경로 직접 검증.

```bash
# A) Detail 화면 경유 (권장 — notification ID 확보 불필요)
# 1. 알림 게시
adb shell cmd notification post -S bigtext -t "발신자없음테스트" FbTest "피드백 테스트"
# 2. Home 또는 Hidden 에서 카드 탭 → Detail → "중요로 고정"
# 3. Rules 탭에서 app 타입 룰이 추가됐는지 확인 (매치값 = com.android.shell)

# B) Broadcast 직접 (CI용)
# 1. 피드백 대상 notification ID 확보 — observeAll().first() 의 id 필드. DB 직접 조회는
#    `run-as` 가 debuggable 빌드에서만 가능하므로 실무상 A 경로가 더 간편.
adb shell am broadcast -a com.smartnoti.app.action.PROMOTE_TO_PRIORITY \
  --es com.smartnoti.app.extra.NOTIFICATION_ID <id> \
  --ei com.smartnoti.app.extra.REPLACEMENT_NOTIFICATION_ID <replId>
```

## Known gaps

- 액션 적용 후 사용자에게 토스트나 확인 알림 없음 (조용히 성공).
- 동일 sender/앱에 기존 룰이 있으면 덮어씀 — 이전 action 과 달라도 경고 없음.
- NotificationFeedbackPolicy 는 KEYWORD / SCHEDULE / REPEAT_BUNDLE 룰은 만들지 않음 (오직 PERSON / APP).

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop re-verify — Detail 경유 PASS (fresh sender `TestSender_0421_T12`, `person:…` rule ALWAYS_PRIORITY 생성 확인)
