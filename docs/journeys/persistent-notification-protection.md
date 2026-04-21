---
id: persistent-notification-protection
title: 지속 알림 키워드 기반 보호
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
---

## Goal

지속(ongoing) / 비제거 가능(isClearable=false) 알림은 사용자가 "지속 알림 숨김" 옵션을 켜도 특정 키워드(통화/내비/녹화/카메라/마이크)에 해당하면 예외적으로 숨기지 않는다. 다른 방식([protected-source-notifications](protected-source-notifications.md))이 커버하지 못하는 텍스트 힌트 기반 보호망.

## Preconditions

- 알림이 posted 됨
- `shouldTreatAsPersistent(isOngoing, isClearable)` 결과 true (둘 중 하나만 만족해도 persistent 로 취급)
- `SmartNotiSettings.protectCriticalPersistentNotifications = true` (default true)

## Trigger

`processNotification(sbn)` 내에서 isPersistent 판정이 true 일 때 `PersistentNotificationPolicy.shouldBypassPersistentHiding(...)` 평가.

## Observable steps

1. `PersistentNotificationPolicy.shouldTreatAsPersistent(sbn.isOngoing, sbn.isClearable)` → isPersistent 결정.
2. isPersistent 이면 `shouldBypassPersistentHiding(packageName, title, body, protectCriticalPersistentNotifications)` 호출.
3. 내부 로직:
   - `protectCriticalPersistentNotifications == false` → 즉시 false (보호 해제)
   - 아니면 `"$packageName $title $body".lowercase()` 에 `BYPASS_KEYWORDS` 중 하나라도 포함되는지 검사
   - 키워드: `통화`, `전화`, `call`, `dialer`, `길안내`, `내비`, `navigation`, `maps`, `녹화`, `recording`, `screen record`, `마이크 사용 중`, `camera in use`, `camera access`, `microphone in use`
4. bypass 가 true 면:
   - `shouldHidePersistentSourceNotification = (isPersistent && !bypass) && hidePersistentSourceNotifications` 가 false 가 되어 원본 유지
   - DB 에는 `isPersistent = false` 로 저장 (bypass 결정이 UI 의 persistent 필터에도 반영)
5. bypass 가 false 인 일반 persistent 알림은 `hidePersistentSourceNotifications` 설정에 따라 DIGEST 로 분류 + 원본 숨김 가능 (→ [digest-suppression](digest-suppression.md)).

## Exit state

- 통화/내비/녹화/마이크/카메라 같은 중요 지속 알림은 사용자 설정과 무관하게 tray 에 유지됨.
- 그 외 지속 알림은 `hidePersistentSourceNotifications` 설정에 따라 정상 숨김 흐름 적용.

## Out of scope

- MediaSession/category/foreground-service flag 기반 보호 (→ [protected-source-notifications](protected-source-notifications.md)) — 그쪽이 더 빠르게, 확정적으로 동작. 이 journey 는 텍스트 기반 보완책.
- 사용자가 키워드 세트를 수정하는 UI (현재 하드코딩)
- `hidePersistentNotifications` (UI 상 persistent 알림을 인박스에서 숨길지) 는 별개 설정

## Code pointers

- `domain/usecase/PersistentNotificationPolicy` — `shouldTreatAsPersistent`, `shouldBypassPersistentHiding`, `BYPASS_KEYWORDS`
- `data/settings/SettingsModels#SmartNotiSettings` — `protectCriticalPersistentNotifications` (default true), `hidePersistentSourceNotifications` (default false), `hidePersistentNotifications` (default true)
- `notification/SmartNotiNotificationListenerService#processNotification` — isPersistent/bypass 계산
- `domain/model/SourceNotificationSuppressionState` — `PERSISTENT_PROTECTED` 상태 (UI 표시용)

## Tests

- `PersistentNotificationPolicyTest` — 통화/내비/녹화 bypass, 충전 등 비-critical 은 숨김 허용, 보호 토글 off 시 bypass 무시

## Verification recipe

`cmd notification post` 는 `FLAG_ONGOING_EVENT` 를 직접 세팅하지 못하기 때문에 persistent 판정(`isOngoing || !isClearable`) 이 트리거되지 않습니다. 그래서 이 journey 는 실제 지속 알림을 띄우는 앱 (전화/내비/녹화) 또는 테스트 전용 앱으로만 end-to-end 검증 가능.

**Policy 단위 검증** 은 유닛 테스트 `PersistentNotificationPolicyTest` 가 모든 bypass 키워드를 커버합니다.

```bash
# 유닛 테스트 실행
./gradlew :app:testDebugUnitTest --tests \
  "com.smartnoti.app.domain.usecase.PersistentNotificationPolicyTest"

# end-to-end: 전화 / Google Maps 내비 / 화면 녹화 등 실제 시스템 기능으로 ongoing 알림을
# 발생시킨 뒤 SmartNoti 설정에서 "지속 알림 원본 자동 숨김" 을 켠 상태에서도 tray 에
# 유지되는지 확인.
```

## Known gaps

- 키워드가 한국어/영어 혼합으로 하드코딩 — 다국어(일본어/중국어 등) 알림 대응 미흡.
- 키워드 매치가 substring contains 기반이라 오탐 가능성 (예: "전화번호" 라는 단어만 있어도 bypass 됨).
- 향후 `ProtectedSourceNotificationDetector` 로 일원화 고려 대상.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Policy-level verification re-run (`PersistentNotificationPolicyTest`, 7/7 PASS). `last-verified` 갱신.
