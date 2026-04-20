---
id: quiet-hours
title: 조용한 시간
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
---

## Goal

사용자가 지정한 시간대(기본 23시 ~ 익일 7시)에 들어오는 쇼핑앱 알림을 자동으로 DIGEST 로 떨어뜨려 밤 시간 소음을 줄인다.

## Preconditions

- `SmartNotiSettings.quietHoursEnabled = true` (default true)
- 현재 시각이 `[quietHoursStartHour, quietHoursEndHour)` 범위 내 (overnight 지원)
- 대상 알림의 packageName 이 classifier 의 `shoppingPackages` 집합에 포함됨 (예: `com.coupang.mobile`)

## Trigger

`processNotification` 내부에서 `CapturedNotificationInput` 조립 시 현재 시각/quiet hours 설정을 반영.

## Observable steps

1. `SettingsRepository.currentNotificationContext(duplicateCount)` 가 현재 시각을 `QuietHoursPolicy.isQuietAt(hourOfDay)` 로 체크해 `quietHours: Boolean` 필드 채움.
2. `CapturedNotificationInput.withContext(...)` 에 해당 값이 주입됨.
3. `NotificationClassifier.classify(input, rules)`:
   - 룰 매치 우선
   - 그 다음 `input.quietHours && packageName in shoppingPackages` → `DIGEST` 반환 (VIP/키워드 등 더 높은 우선순위가 없을 때)
4. 이후 [notification-capture-classify](notification-capture-classify.md) 파이프라인으로 이어지며, opt-in 된 앱이면 원본 숨김 + replacement (→ [digest-suppression](digest-suppression.md)).

## Exit state

- 해당 시간대 쇼핑앱 알림이 DIGEST 분류로 DB 에 저장됨.
- Home/정리함 인박스에서 확인 가능.

## Out of scope

- Quiet hours 가 priority/키워드 heuristic 보다 우선하지는 않음 — 중요한 결제/인증 알림은 계속 PRIORITY.
- 시간 창 밖의 쇼핑앱 알림은 일반 분류 흐름.
- 사용자가 shoppingPackages 를 수정하는 UI 는 없음 (현재 classifier 생성자에 하드코딩).

## Code pointers

- `data/settings/SettingsModels#SmartNotiSettings` — `quietHoursEnabled`, `quietHoursStartHour` (default 23), `quietHoursEndHour` (default 7)
- `data/settings/SettingsRepository#currentNotificationContext` — 시각 → `quietHours` 플래그
- `domain/usecase/QuietHoursPolicy#isQuietAt` — same-day / overnight 처리
- `domain/usecase/NotificationClassifier` — quiet hours + shopping package 분기
- `ui/screens/settings/SettingsScreen` — 토글/시간 선택 UI (Settings 화면 문서화 미완)

## Tests

- `QuietHoursPolicyTest` — 9~18 (same-day), 23~7 (overnight) 범위
- `NotificationClassifierTest` — 쇼핑 앱 + quiet hours → DIGEST

## Verification recipe

```bash
# 1. 에뮬레이터 시간을 23:30 으로 설정 (quiet hours 창 안)
adb shell date 112423302026.00
# 또는 시스템 시간 유지한 채 quiet hours 범위를 현재 시각을 포함하게 설정

# 2. 쇼핑앱(com.coupang.mobile) 으로 알림 게시
adb shell cmd notification post -S bigtext --pkg com.coupang.mobile \
  -t "Coupang" QuietTest "한정 특가"
# (현재 cmd notification 은 --pkg 옵션이 없을 수 있어 실제 앱으로 테스트 권장)

# 3. Home/정리함 탭에 DIGEST 로 분류됐는지 확인
```

## Known gaps

- `shoppingPackages` 가 classifier 생성자에 하드코딩 (`setOf("com.coupang.mobile")`) — 사용자가 카테고리를 확장할 수 없음.
- Quiet hours 가 적용되었다는 것을 Detail 의 reasonTags 로만 확인 가능. UI 에서 "quiet hours 때문에 숨김" 같은 명시적 설명 부재.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
