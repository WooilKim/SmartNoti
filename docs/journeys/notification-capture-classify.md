---
id: notification-capture-classify
title: 알림 캡처 및 분류
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

외부 앱이 게시한 모든 알림을 SmartNoti가 캡처하고, PRIORITY / DIGEST / SILENT 중 하나로 분류해 DB에 저장한다. 이 journey는 인박스/숨김/요약 등 모든 후속 journey의 공통 전제다.

## Preconditions

- 사용자가 NotificationListener 권한을 허용함 (→ [onboarding-bootstrap](onboarding-bootstrap.md))
- `SmartNotiNotificationListenerService`가 시스템에 바인딩되어 `onListenerConnected` 상태

## Trigger

외부 앱이 `NotificationManager.notify(...)` 호출 → 시스템이 리스너의 `onNotificationPosted(sbn)` 콜백을 발화.

## Observable steps

1. `SmartNotiNotificationListenerService.onNotificationPosted(sbn)` 수신.
2. `processNotification(sbn)`이 serviceScope(IO dispatcher)에서 launch 됨.
3. Extras 에서 `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_CONVERSATION_TITLE` 을 꺼내 title / body / sender 복원. PackageManager 로 appName 조회.
4. `RulesRepository.currentRules()` / `SettingsRepository.observeSettings().first()` 로 현재 룰/설정 스냅샷 확보.
5. `PersistentNotificationPolicy.shouldTreatAsPersistent(isOngoing, isClearable)` 로 persistent 여부 판단.
6. `DuplicateNotificationPolicy` + `LiveDuplicateCountTracker` 로 최근 동일 content signature 발생 횟수 집계.
7. `CapturedNotificationInput` 을 조립해 `NotificationCaptureProcessor.process(input, rules, settings)` 호출.
8. 내부에서 `NotificationClassifier.classify(input, rules)` 가 분류 결정:
   - 룰 매치 (PERSON / APP / KEYWORD / SCHEDULE / REPEAT_BUNDLE) 최우선
   - 그 외: VIP 발신자 집합, 우선순위 키워드 집합, 중복 카운트 임계, Quiet hours + 쇼핑앱 등 heuristic
9. `NotificationRepository.save(notification, postTime, contentSignature)` → Room `notifications` 테이블에 upsert.
10. 이어서 source routing 단계 진입 (→ [silent-auto-hide](silent-auto-hide.md), [priority-inbox](priority-inbox.md), digest-suppression).

## Exit state

- `NotificationEntity` row 한 건이 `status ∈ {PRIORITY, DIGEST, SILENT}` 로 저장됨.
- `NotificationRepository.observeAll()` 을 구독하는 모든 구성 요소가 새 알림을 즉시 관측 가능.
- 분류 결과에 따른 후속 routing이 실행됨 (cancel / replacement / keep).

## Out of scope

- 원본 알림의 숨김/교체/유지 동작 (→ 각 분류별 journey)
- Protected 알림 예외 (→ [protected-source-notifications](protected-source-notifications.md))
- 분류 결과 UI 렌더링 (→ priority-inbox / digest-inbox / hidden-inbox)
- 룰 자체의 CRUD (→ rules-management)

## Code pointers

- `SmartNotiNotificationListenerService#onNotificationPosted` — entry
- `SmartNotiNotificationListenerService#processNotification` — pipeline
- `domain/usecase/NotificationCaptureProcessor` — input → decision
- `domain/usecase/NotificationClassifier` — 분류 규칙 본체 (VIP/키워드/shopping 상수도 이곳 생성자에 주입)
- `domain/usecase/DuplicateNotificationPolicy`, `LiveDuplicateCountTracker`
- `domain/usecase/PersistentNotificationPolicy`
- `data/local/NotificationRepository#save`
- `data/local/NotificationEntity` — Room 스키마

## Tests

- `NotificationClassifierTest` — 룰 매칭, 스케줄 윈도우, repeat bundle
- `DuplicateNotificationPolicyTest`
- `QuietHoursPolicyTest`
- `NotificationCapturePolicyTest`

## Verification recipe

```bash
# 1. 리스너 권한 허용 상태인지 확인
adb shell cmd notification allow_listener \
  com.smartnoti.app/.notification.SmartNotiNotificationListenerService

# 2. 우선순위 키워드 알림 게시
adb shell cmd notification post -S bigtext -t "Bank" BankAuth \
  "인증번호 123456을 입력하세요"

# 3. DB 캡처 확인 — 앱 실행 후 Home 혹은 Priority 탭에 반영되는지
adb shell am start -n com.smartnoti.app/.MainActivity
```

## Known gaps

- 서비스가 disconnect 된 동안 게시된 알림은 놓칠 수 있음. 최초 온보딩에 한해 [onboarding-bootstrap](onboarding-bootstrap.md) 이 `activeNotifications` 로 보완.
- 일반 재접속(예: 권한 토글) 시점의 누락은 현재 보완 경로 없음.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
