---
id: protected-source-notifications
title: 미디어·통화·포그라운드 서비스 알림 보호
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
---

## Goal

MediaSession, 통화, 내비, 알람, 포그라운드 서비스처럼 **원본 알림을 살려둬야 정상 동작하는** 알림은, SmartNoti 의 어떤 숨김/교체 라우팅도 적용받지 않는다. 원본을 취소하면 해당 서비스의 재생이 끊기거나 서비스 자체가 죽기 때문이다 (예: YouTube Music 재생 중단).

## Preconditions

- 알림이 SmartNoti 리스너에 posted 됨 (→ [notification-capture-classify](notification-capture-classify.md))

## Trigger

`SmartNotiNotificationListenerService.processNotification(sbn)` 내부, routing 결정 **직전**.

## Observable steps

1. `ProtectedSourceNotificationDetector.signalsFrom(sbn)` 으로 다음 네 필드를 추출:
   - `category` (Notification.category, string)
   - `templateName` (`extras[Notification.EXTRA_TEMPLATE]`)
   - `hasMediaSessionExtra` (`extras.containsKey("android.mediaSession")`)
   - `isForegroundService` (`flags & FLAG_FOREGROUND_SERVICE != 0`)
2. `isProtected(signals)` 평가 — 다음 중 **하나라도** true 면 보호 대상:
   - `category` ∈ `{"call", "transport", "navigation", "alarm", "progress"}`
   - `hasMediaSessionExtra == true`
   - `templateName` 이 `"MediaStyle" | "BigMediaStyle" | "DecoratedMediaCustomViewStyle"` 로 끝남
   - `isForegroundService == true`
3. 보호 대상이면 routing 을 강제로 덮어씀:
   - `cancelSourceNotification = false`
   - `notifyReplacementNotification = false`
   - `shouldSuppressSourceNotification = false` 로 함께 덮어 다른 설정이 끼어들 여지 제거
4. 리스너는 `cancelNotification(sbn.key)` 도, `notifier.notifySuppressedNotification(...)` 도 호출하지 않음.
5. 그러나 분류/저장 경로는 그대로 — `NotificationRepository.save(...)` 로 row 는 기록됨.

## Exit state

- 시스템 tray: 원본 알림 유지 → MediaSession/포그라운드 서비스 계속 살아있음 (예: 음악 재생 지속).
- SmartNoti DB: 해당 알림이 분류된 상태로 저장됨. Home/Priority 등 내부 인박스에는 여전히 노출.

## Out of scope

- 분류 자체 (→ [notification-capture-classify](notification-capture-classify.md))
- Protected 알림이 PRIORITY 로 분류되는 경우 (정상 흐름 — 원본 유지가 둘 다의 기대)
- Protected 알림이 SILENT 로 분류되는 경우 count 포함 여부 (→ [silent-auto-hide](silent-auto-hide.md) — 요약 count 에는 포함. 사용자가 Hidden 인박스에서 동일 알림을 참조 가능)
- 메시징 스타일 지속 알림 (의도적으로 보호 대상 아님)

## Code pointers

- `notification/ProtectedSourceNotificationDetector` — signal 추출/판정 상수
- `notification/SmartNotiNotificationListenerService#processNotification` — `isProtectedSourceNotification` 분기

## Tests

- `ProtectedSourceNotificationDetectorTest` — 카테고리 / 템플릿 / mediaSession extra / foreground service flag 케이스 전수

## Verification recipe

```bash
# 1. 유튜브 뮤직 등 MediaStyle 앱으로 재생 시작
#    → 알림이 게시되고 FLAG_FOREGROUND_SERVICE + android.mediaSession 을 보유

# 2. SmartNoti 활성 상태에서 원본이 취소되지 않는지 확인
adb shell dumpsys notification --noredact \
  | grep -A3 "com.google.android.apps.youtube.music"

# 3. 재생이 끊기지 않아야 함 (실제 디바이스에서 재생 테스트)

# 4. `cmd notification post` 로 합성 테스트 (FLAG_FOREGROUND_SERVICE 는 cmd 가
#    직접 붙일 수 없어 실제 앱으로 검증 권장)
```

## Known gaps

- 휴리스틱 기반 detector 이므로 custom template 을 쓰는 일부 앱은 놓칠 수 있음. 신고를 받는 대로 조건 보강 예정.
- 메시징 스타일 지속 알림(예: 채팅방 상단 고정)은 보호 대상 아님 — 사용자가 이들을 SILENT 로 분류하면 의도한 대로 숨김 동작.

## Change log

- 2026-04-20: 최초 구현 + 문서화 (commit `762fa28`, PR #2)
