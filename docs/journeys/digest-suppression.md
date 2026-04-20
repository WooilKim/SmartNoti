---
id: digest-suppression
title: 디제스트 자동 묶음 및 원본 교체
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
---

## Goal

DIGEST 로 분류된 알림 중, 사용자가 명시적으로 opt-in 한 앱에 한해 원본을 시스템 tray 에서 제거하고 대신 SmartNoti 가 게시하는 "요약+액션" replacement 알림으로 대체해서 반복 알림의 소음을 줄인다.

## Preconditions

- 알림이 DIGEST 로 분류 (→ [notification-capture-classify](notification-capture-classify.md))
- `SmartNotiSettings.suppressSourceForDigestAndSilent = true` — 전역 opt-in
- `sbn.packageName` 이 `SmartNotiSettings.suppressedSourceApps` 에 이미 있거나, 이번 처리 시 `SuppressedSourceAppsAutoExpansionPolicy` 가 자동으로 추가 (아래 "Observable steps" 참고)
- 대상 알림이 protected 가 아님 (→ [protected-source-notifications](protected-source-notifications.md))

## Trigger

`processNotification(sbn)` 의 분류 결과 = DIGEST 이고 `NotificationSuppressionPolicy.shouldSuppressSourceNotification(...)` 가 true 를 반환.

## Observable steps

1. `SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(...)` 가 decision=DIGEST 이고 전역 opt-in 이 켜졌으며 현재 리스트에 app 이 없으면 `currentApps + packageName` 반환. 리스너가 즉시 `settingsRepository.setSuppressedSourceApps(expanded)` 로 영속화.
2. `NotificationSuppressionPolicy.shouldSuppressSourceNotification(...)` 가 확장된 리스트 기준으로 true 반환.
3. `SourceNotificationRoutingPolicy.route(DIGEST, hidePersistent=*, suppress=true)` → `cancelSourceNotification=true, notifyReplacementNotification=true`.
4. 리스너가 main thread 에서 `cancelNotification(sbn.key)` 호출 → 원본 알림 제거.
5. `SmartNotiNotifier.notifySuppressedNotification(DIGEST, ...)` 호출:
   - 채널: `ReplacementNotificationChannelRegistry.resolve(DIGEST, profile)` 가 반환하는 `smartnoti_replacement_digest_*` 중 하나
   - 제목: 원본 title (없으면 body / "{appName} 알림")
   - 본문: `ReplacementNotificationTextFormatter.explanationText(DIGEST, reasonTags)`
   - subText: "{appName} • Digest"
   - 액션: `중요로 고정`, `Digest로 유지`, `열기`
6. 사용자가 액션 탭 → `SmartNotiNotificationActionReceiver` 가 broadcast 수신 → feedback 적용 (→ [rules-feedback-loop](rules-feedback-loop.md)).
7. 사용자가 본문 탭 → `contentIntent` 가 `MainActivity` 를 열고 parent route = Digest, notification id 를 전달해 Detail 로 이동.

## Exit state

- 시스템 tray: 원본 제거, replacement 1건 잔존 (AutoCancel=true 이므로 탭 시 자동 해제).
- DB: 원본은 `status=DIGEST` 로 저장됨. `replacementNotificationIssued` 필드 true.
- 사용자가 액션으로 재분류한 경우 룰이 저장되고 status/태그 업데이트.

## Out of scope

- Silent 은 replacement 없이 요약 알림으로 처리 (→ [silent-auto-hide](silent-auto-hide.md))
- opt-in 하지 않은 앱의 DIGEST 는 원본 유지 + DB 기록만 (Digest 인박스에서 훑어봄, → digest-inbox)
- 액션 수신 이후 룰 저장 세부 (→ [rules-feedback-loop](rules-feedback-loop.md))

## Code pointers

- `notification/SourceNotificationRoutingPolicy` — DIGEST 분기
- `notification/NotificationSuppressionPolicy` — opt-in 판정
- `notification/SuppressedSourceAppsAutoExpansionPolicy` — 새 앱 자동 추가 규칙
- `notification/SmartNotiNotifier#notifySuppressedNotification` — replacement 빌더
- `notification/ReplacementNotificationTextFormatter` — 본문 포맷
- `notification/ReplacementNotificationChannelRegistry` — 채널 선택
- `notification/SmartNotiNotificationActionReceiver` — 액션 수신
- `data/settings/SettingsModels` — `suppressSourceForDigestAndSilent`, `suppressedSourceApps`

## Tests

- `SourceNotificationRoutingPolicyTest#digest_suppression_cancels_source_and_shows_replacement`
- `NotificationSuppressionPolicyTest`
- `SuppressedSourceAppsAutoExpansionPolicyTest`
- `SmartNotiNotificationActionReceiverTest`
- `ReplacementNotificationChannelRegistryTest`
- `ReplacementNotificationTextFormatterTest`

## Verification recipe

```bash
# 1. Settings 탭 → "원본 알림 자동 숨김" 섹션 → "자동 숨김" 토글 ON
#    → 앱 선택 리스트에서 테스트 대상 앱 (예: com.android.shell) 체크
#    ※ cmd notification 은 com.android.shell 패키지로 게시되므로 테스트에 적합

# 2. Digest 로 분류되는 알림 게시 (중복 혹은 프로모션 키워드)
for i in 1 2 3; do
  adb shell cmd notification post -S bigtext -t "Promo" "SuppTest$i" "같은 광고 텍스트"
done

# 3. 원본이 tray 에서 제거되고 SmartNoti replacement 가 게시됐는지 확인
adb shell dumpsys notification --noredact | grep smartnoti_replacement_digest

# 4. replacement 의 "Digest로 유지" 액션 탭 → Rules 탭에 app 룰 추가 확인
```

## Known gaps

- Auto-expansion 은 사용자가 Settings 에서 명시적으로 비운 앱도 DIGEST 가 다시 오면 재추가함. "sticky 제외" 리스트는 미구현 — 현재는 재분류로 회피.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-20: `SuppressedSourceAppsAutoExpansionPolicy` 추가 — 전역 opt-in 이 켜졌고 DIGEST 로 분류된 새 앱이 들어오면 자동으로 `suppressedSourceApps` 확장. onboarding 이후 게시되는 "(광고)" 류 알림이 원본 유지되던 문제 해소.
- 2026-04-20: `NotificationReplacementIds.idFor` 에 notificationId 포함 — 같은 앱에서 서로 다른 내용의 DIGEST 가 동시에 게시될 때 replacement 가 서로 덮어쓰던 충돌 해소.
- 2026-04-20: Settings 의 앱 선택 리스트에 "모두 선택" / "모두 해제" OutlinedButton 추가 — 대량 opt-in / opt-out 편의. 현재 필터로 보이는 앱만 영향 (숨겨진 앱의 선택 상태는 보존).
