---
id: priority-inbox
title: 중요 알림 인박스
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
---

## Goal

PRIORITY 로 분류된 알림을 "중요" 탭에 모아 사용자가 한눈에 훑을 수 있게 한다. 시스템 알림센터의 원본은 **절대 건드리지 않아** 사용자가 기대하는 즉시 전달(소리 / 헤즈업 / 잠금화면)이 정상 동작하게 한다.

## Preconditions

- 알림이 PRIORITY 로 분류되어 DB 에 저장됨 (→ [notification-capture-classify](notification-capture-classify.md))
- 온보딩 완료 상태 (→ [onboarding-bootstrap](onboarding-bootstrap.md))

## Trigger

- 사용자가 하단 네비의 "중요" 탭 탭, 또는
- Home 의 "중요 알림 ... 열기" 탭, 또는
- replacement 알림의 deep-link 가 `Routes.Priority.route` 를 parent 로 지정한 경우

## Observable steps

1. `navigateToTopLevel(Routes.Priority.route)` → NavController 가 Priority composable 렌더링.
2. `PriorityScreen` 이 `SettingsRepository.observeSettings()` 와 `NotificationRepository.observePriorityFiltered(hidePersistentNotifications)` 를 collect.
3. LazyColumn 이 status = PRIORITY 인 `NotificationUiModel` 리스트를 카드로 표시 (empty-state 는 헤더 + `EmptyState` 컴포넌트).
4. 사용자가 카드 탭 → `Routes.Detail.create(notificationId)` 로 navigate (→ notification-detail).

## 시스템 tray 처리

- `SourceNotificationRoutingPolicy.route(PRIORITY, *, *)` → `SourceNotificationRouting(cancelSourceNotification = false, notifyReplacementNotification = false)` 고정.
- 원본 알림은 항상 시스템 tray 에 유지됨. SmartNoti 가 cancel / replace 를 시도하지 않음.
- `suppressSourceForDigestAndSilent` 등 사용자 설정과 무관하게 PRIORITY 는 건드리지 않는 것이 불변조건.

## Delivery profile

- `DeliveryProfilePolicy` 가 각 알림에 대해 AlertLevel / VibrationMode / HeadsUp / LockScreen visibility 조합 프로파일을 계산.
- 채널은 `ReplacementNotificationChannelRegistry` 가 프로파일 조합마다 사전 생성해 둠 (PRIORITY 의 경우 LOUD / SOFT / QUIET × STRONG / LIGHT / OFF × headsup on/off 등).

## Exit state

- Priority 탭에 현재 PRIORITY 분류된 알림이 모두 표시됨.
- 시스템 tray 의 원본 그대로 유지.
- 사용자는 Detail 로 이동해 재분류/룰 고정 등 후속 액션 수행 가능 (→ rules-feedback-loop).

## Out of scope

- 분류 로직 자체 (→ [notification-capture-classify](notification-capture-classify.md))
- "중요로 고정" feedback action 의 룰 저장 경로 (→ rules-feedback-loop)
- Priority 알림의 사운드/헤즈업 실제 재생 (플랫폼/DeliveryProfile 책임)

## Code pointers

- `ui/screens/priority/PriorityScreen`
- `data/local/NotificationRepository#observePriorityFiltered`
- `domain/usecase/DeliveryProfilePolicy`
- `notification/SourceNotificationRoutingPolicy` — PRIORITY 분기
- `notification/ReplacementNotificationChannelRegistry` — 채널 정의
- `navigation/Routes.Priority`, `navigation/BottomNavItem`

## Tests

- 분류/룰 레이어 테스트로 간접 커버. `PriorityScreen` 단독 테스트는 현재 없음.
- `SourceNotificationRoutingPolicyTest#persistent_hidden_priority_keeps_source_notification_and_skips_replacement`

## Verification recipe

```bash
# 1. VIP 발신자 또는 우선순위 키워드가 포함된 알림 게시
adb shell cmd notification post -S bigtext -t "엄마" Mom "오늘 저녁에 올 수 있어?"

# 2. 시스템 tray 원본 유지 확인
adb shell dumpsys notification --noredact | grep -B1 -A4 "엄마"

# 3. Priority 탭 진입해 해당 카드가 보이는지 시각 확인
adb shell am start -n com.smartnoti.app/.MainActivity
# (1080x2400 에뮬레이터 기준 "중요" 탭 좌표: 327, 2232)
```

## Known gaps

- Priority 인박스 자체에 일괄 처리(읽음/모두 지우기) 액션 없음 — 카드별 Detail 이동 필요.
- PriorityScreen 직접 UI 테스트 부재.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
