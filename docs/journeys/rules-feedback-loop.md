---
id: rules-feedback-loop
title: 알림 피드백 → 룰 저장
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

Digest/Silent replacement 알림 또는 Detail 화면의 "중요로 고정 / Digest로 보내기 / 조용히 처리 / 무시" 액션을 한 번만 눌러도, 해당 알림의 상태를 바꾸는 동시에 동일 유형의 룰을 자동으로 저장해 다음부터는 사용자가 관여하지 않아도 되게 한다.

## Preconditions

- 대상 알림이 DB 에 존재
- replacement 알림 경로의 경우 POST_NOTIFICATIONS 권한 허용

## Trigger

- replacement 알림의 액션 버튼 탭 → `PendingIntent.getBroadcast` 로 `SmartNotiNotificationActionReceiver` 기동 (단, IGNORE 는 replacement alert 에 버튼으로 노출되지 않음 — replacement 자체가 non-IGNORE 결정이므로)
- [notification-detail](notification-detail.md) 의 버튼 탭 → 같은 `NotificationFeedbackPolicy` 사용. Detail 의 "무시" 버튼은 파괴적이라 `IgnoreConfirmationDialog` 확인 + 3초 "되돌리기" 스낵바 경유.

## Observable steps

1. Broadcast 수신 시 `intent.action` 을 확인: `ACTION_PROMOTE_TO_PRIORITY` / `ACTION_KEEP_DIGEST` / `ACTION_KEEP_SILENT` / `ACTION_IGNORE` 중 하나. `ACTION_IGNORE` 는 vocabulary 일관성을 위해 배선돼 있으나 replacement alert 의 액션 버튼에는 노출되지 않음 — 현재 유일한 trigger 경로는 Detail 화면 확인 다이얼로그 (broadcast 분기는 미래 surfaces 용도 유지).
2. `SmartNotiNotificationActionReceiver.onReceive` 가 goAsync 로 코루틴 launch:
   1. notificationId, replacementNotificationId extras 파싱
   2. `NotificationRepository.observeAll().first()` 에서 해당 notificationId 찾음
   3. `NotificationFeedbackPolicy.applyAction(notification, action)` → status + reasonTags 업데이트된 UI 모델 생성 (reasonTag "사용자 규칙" 추가)
   4. `NotificationRepository.updateNotification(updated)` 로 DB 반영
   5. `NotificationFeedbackPolicy.toRule(notification, action)` 로 새 룰 생성:
      - sender 존재 → `RuleTypeUi.PERSON`, matchValue = sender
      - sender 없음 → `RuleTypeUi.APP`, matchValue = packageName
      - rule id = `"{type.lowercase()}:{matchValue}"` (동일 키는 upsert 로 덮어씀)
      - action 이 IGNORE 이면 rule 의 `RuleActionUi` 역시 IGNORE 로 저장됨 — 자동 룰 upsert 계약 유지 (promote / keep 경로와 동일).
   6. `RulesRepository.upsertRule(rule)`
   7. `notificationManager.cancel(replacementNotificationId)` — replacement 알림 제거 (Detail 경로에선 생략; IGNORE 경로는 애초에 replacement 가 없으므로 no-op)
   8. Detail "무시" 경로 한정: 확정 시 prior `NotificationUiModel` + prior 룰 스냅샷을 `applyIgnoreWithUndo` 가 유지, 3초 "되돌리기" 스낵바 action 탭 시 status / reasonTags / rule 을 원복 (새 룰이면 `deleteRule`, 기존 룰이면 prior action 으로 `upsertRule`). Undo 는 in-memory — 프로세스 재시작 시 영구화.
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

- `notification/SmartNotiNotificationActionReceiver` — broadcast → `RuleActionUi` 매핑 (IGNORE 포함)
- `notification/SmartNotiNotifier#ACTION_*` 상수 (`ACTION_PROMOTE_TO_PRIORITY` / `ACTION_KEEP_DIGEST` / `ACTION_KEEP_SILENT` / `ACTION_IGNORE`), `notifier.addAction(...)` 에서 연결
- `domain/usecase/NotificationFeedbackPolicy` — applyAction + toRule (IGNORE 케이스 포함)
- `ui/screens/detail/NotificationDetailScreen#applyIgnoreWithUndo` — Detail "무시" 확인 + undo 스냅샷 체인
- `ui/components/IgnoreConfirmationDialog`
- `data/rules/RulesRepository#upsertRule`, `#deleteRule` (undo 경로)
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
- Recipe fragility — 잔존 룰 (2026-04-21 cycle): 이 recipe 가 수행한 `upsertRule` 은 `smartnoti_rules.preferences_pb` 에 영속화되어 다음 tick 환경에 남는다. 재현 시 매번 unique sender (예: `TestSender_<date>_T<n>`) 를 쓰거나, recipe 서두에 남겨진 `person:*` 룰을 정리하는 clean-up 단계를 추가해야 후속 priority-inbox / rules-management recipe 의 baseline 을 오염시키지 않는다.
- Recipe fragility — deep-link cold-start (2026-04-21 관측): Detail 경유 경로(A)는 `am start … -e DEEP_LINK_ROUTE hidden` 으로 시작하는데, `MainActivity` 가 top-most 로 이미 떠있으면 `LaunchedEffect(deepLinkRoute)` 가 재발화하지 않아 Hidden 화면으로 이동하지 않는다. `am force-stop com.smartnoti.app` 후 cold start 필수. 동일 제약이 hidden-inbox / notification-detail recipe 에도 적용됨.
- Recipe fragility — broadcast path blocked (2026-04-21 verify SKIP): Recipe B 의 `adb shell am broadcast -a com.smartnoti.app.action.PROMOTE_TO_PRIORITY` 는 `AndroidManifest.xml` 의 `SmartNotiNotificationActionReceiver android:exported="false"` 때문에 `-n com.smartnoti.app/.notification.SmartNotiNotificationActionReceiver` 로 component 를 명시해도 enqueue 만 되고 `onReceive` 가 실행되지 않음 (동일 UID 의존). Recipe A (Detail UI) 는 Phase A Task 4 이후 bottom-nav 에서 Hidden 탭이 사라져 SILENT 로 분류된 타겟 알림을 여는 네비게이션 경로가 recipe 에 enumerated 돼있지 않음 (Home passthrough 카드 → Detail 스텝 필요). 다음 drift plan 이 두 경로 중 하나를 명문화하거나 test hook 을 제공해야 함.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: v1 loop re-verify — Detail 경유 PASS (fresh sender `TestSender_0421_T12`, `person:…` rule ALWAYS_PRIORITY 생성 확인)
- 2026-04-21: v1 loop re-verify #3 — KEEP_SILENT 분기 PASS (fresh sender `SilentTest_0421_T1`, Detail "조용히 처리" 탭 → Rules 탭 "조용히 1" 카테고리에 person 타입 룰 `person:SilentTest_0421_T1` 생성 + reasonTags 에 "사용자 규칙" 추가 관측). 3개 액션 분기 (ALWAYS_PRIORITY / ALWAYS_DIGEST / KEEP_SILENT) 모두 관측 완료.
- 2026-04-21: 4번째 feedback 액션 **"무시"** 추가 — Detail 화면에서만 trigger. `IgnoreConfirmationDialog` 확인 → `applyIgnoreWithUndo` 가 `NotificationFeedbackPolicy.applyAction(IGNORE)` + `RulesRepository.upsertRule(RuleActionUi.IGNORE)` + `onBack()` + 3초 "되돌리기" 스낵바 체인을 실행. 자동 룰 upsert 계약은 기존 3개 액션과 동일 (PERSON when sender, APP otherwise). 스냅샷 기반 undo — prior notification / prior rule 복원, 새 룰이면 delete, 기존 룰이면 prior action 으로 upsert. `SmartNotiNotifier.ACTION_IGNORE` broadcast 상수 + `SmartNotiNotificationActionReceiver` 매핑도 추가했으나 replacement alert 에는 IGNORE 버튼을 노출하지 않음 (replacement 자체가 non-IGNORE 결정이라 불가). Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6a (#187 `57df6ac`). `last-verified` 는 ADB 검증 전까지 bump 하지 않음 (per `.claude/rules/docs-sync.md`).
