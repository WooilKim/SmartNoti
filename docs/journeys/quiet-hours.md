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

시스템 시간 조작은 다른 기능에도 영향을 주므로, **앱의 Settings 에서 quietHoursStartHour / quietHoursEndHour 를 현재 시각을 포함하도록 바꾸는 것** 이 가장 간편한 방법입니다.

```bash
# 1. Settings 탭에서 "조용한 시간" 섹션 열기 — 시작/종료 시간을 현재 시각을 포함하도록 조정
#    (예: 현재 14:30 이라면 start=14, end=16 으로)

# 2. `com.coupang.mobile` 또는 기존 classifier 의 shoppingPackages 에 포함된 앱 패키지로 알림 게시
#    (cmd notification 는 --pkg 옵션이 없어 `com.android.shell` 로만 게시됨. 쇼핑앱 테스트는
#     실제 앱 혹은 테스트 전용 앱 필요.)

# 3. quiet hours 종료 후에도 분류가 남아 있는지 Home/정리함 탭에서 확인 — 알림의
#    reasonTags 에 "quiet hours" 관련 태그가 있는지 Detail 에서 확인
```

## Known gaps

- `shoppingPackages` 가 classifier 생성자에 하드코딩 (`setOf("com.coupang.mobile")`) — 사용자가 카테고리를 확장할 수 없음.
- Quiet hours 가 적용되었다는 것을 Detail 의 reasonTags 로만 확인 가능. UI 에서 "quiet hours 때문에 숨김" 같은 명시적 설명 부재.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
