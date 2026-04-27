---
id: protected-source-notifications
title: 미디어·통화·포그라운드 서비스 알림 보호
status: shipped
owner: @wooilkim
last-verified: 2026-04-27
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
# A) MediaStyle 합성 테스트 (가장 가벼움)
#    `-S media` 는 category=transport 를 세팅하므로 카테고리 기반 보호가 켜지는지
#    확인 가능.
adb shell cmd notification post -S media -t "Player" MediaTest "미디어 스타일 테스트"
# 확인: dumpsys 에 MediaTest 가 여전히 존재 (SmartNoti 가 cancel 하지 않음)
adb shell dumpsys notification --noredact | grep -B1 "MediaTest"

# B) 실제 미디어 앱 (권장)
#    YouTube Music 등으로 재생 시작 → FLAG_FOREGROUND_SERVICE + android.mediaSession 보유
adb shell dumpsys notification --noredact \
  | grep -A3 "com.google.android.apps.youtube.music"
#    재생이 끊기지 않으면 PASS

# C) Foreground service flag 직접 세팅은 `cmd notification post` 가 지원하지 않으므로
#    FLAG_FOREGROUND_SERVICE 경로는 실제 앱 또는 테스트 전용 앱으로만 검증 가능.
```

## Known gaps

- 휴리스틱 기반 detector 이므로 custom template 을 쓰는 일부 앱은 놓칠 수 있음. 신고를 받는 대로 조건 보강 예정.

## Design decisions

- **MessagingStyle 지속 알림은 의도적으로 보호 대상이 아님.** 메신저의 채팅방 상단 고정 알림은 FLAG_FOREGROUND_SERVICE 도 MediaSession 도 없어서 cancel 해도 서비스가 죽지 않는다. 사용자가 메신저 알림을 SILENT 로 분류해 숨기고 싶을 때 그 의도를 그대로 존중해야 하므로 MessagingStyle 은 `ProtectedSourceNotificationDetector` 의 보호 목록에 **넣지 않는다**. 특정 메신저에서 cancel 후 재게시 루프가 발생한다면 이는 해당 앱의 재게시 패턴 문제이며, 사용자는 재분류 또는 앱 설정의 알림 끄기로 해결.

## Change log

- 2026-04-20: 최초 구현 + 문서화 (commit `762fa28`, PR #2)
- 2026-04-20: MessagingStyle 비보호를 Known gap 에서 Design decision 으로 승격 — 의도적 결정임을 명시.
- 2026-04-26: journey-tester rotation re-verification (oldest non-deprecated by `last-verified` 2026-04-24 → 2026-04-26).
- 2026-04-27: journey-tester rotation re-verification via Recipe A MediaStyle synthetic (`-S media` → `category=transport`); MediaTest persists in dumpsys post-listener (no cancel). Recipes B/C SKIP (real-app + foreground-flag injection).
- 2026-04-27: **Issue #510 closure (cross-cut)** — protected source notifications are not eligible for replacement notifications by design (steps 3-4 force `notifyReplacementNotification = false`), so the new `large = source app icon + small = action glyph` visual contract introduced by the #510 fix does not apply here. Listed for traceability only — no behavioral change in this journey. PR #521 (Tasks 1-4) + closure this PR. Plan: `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`.
