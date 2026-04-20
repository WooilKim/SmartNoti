---
id: silent-auto-hide
title: 조용히 분류된 알림 자동 숨김
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
---

## Goal

SILENT 로 분류된 알림은 사용자가 즉시 볼 필요가 없다는 판단이므로, 시스템 알림센터의 원본을 자동으로 숨기고 대신 SmartNoti 가 요약 알림 한 건("숨겨진 알림 N건")으로 개수와 바로가기만 제공한다.

## Preconditions

- NotificationListener + POST_NOTIFICATIONS 권한 허용
- 알림이 SILENT 로 분류됨 (→ [notification-capture-classify](notification-capture-classify.md))
- 대상 알림이 protected 가 아님 (→ [protected-source-notifications](protected-source-notifications.md))

## Trigger

외부 앱이 알림을 게시하고, 분류 결과가 SILENT.

## Observable steps

1. `SourceNotificationRoutingPolicy.route(SILENT, *, *)` → `cancelSourceNotification = true, notifyReplacementNotification = false` 고정. (legacy `suppressSourceForDigestAndSilent` 설정과 무관)
2. 리스너가 main thread 에서 `cancelNotification(sbn.key)` 호출 → 시스템 tray 에서 원본 제거.
3. `NotificationRepository.save(...)` 로 DB 에 `status = SILENT` row 저장.
4. `SmartNotiNotificationListenerService.silentSummaryJob` 가 `repository.observeAll()` 과 `settingsRepository.observeSettings()` 를 `combine` 으로 구독, `filterPersistent(hidePersistentNotifications)` 적용 후 `status == SILENT` 개수 집계 — Home StatPill 과 같은 규칙.
5. count 가 직전 값과 다르면 `SilentHiddenSummaryNotifier.post(count)` 호출 (동일한 값이 연속되면 재게시하지 않음).
6. `smartnoti_silent_summary` 채널(IMPORTANCE_MIN) 로 알림 게시:
   - title `"숨겨진 알림 {N}건"`
   - text `"탭해서 숨겨진 알림 보기"`
   - action button `"숨겨진 알림 보기"`
   - `autoCancel = true`, `silent = true`, visibility `SECRET`
7. 사용자가 요약 본문 또는 액션 탭:
   1. PendingIntent 가 `MainActivity` 를 FLAG_ACTIVITY_NEW_TASK + CLEAR_TOP 로 기동하며 extra `com.smartnoti.app.extra.DEEP_LINK_ROUTE = "hidden"` 전달
   2. `MainActivity.extractDeepLinkRoute()` 에서 파싱, `pendingDeepLinkRoute` state 갱신
   3. `AppNavHost` 의 `LaunchedEffect(pendingDeepLinkRoute)` 가 `navController.navigate("hidden")` 호출
   4. `HiddenNotificationsScreen` 가 SILENT 리스트 렌더링
   5. `autoCancel` 로 요약 알림 자동 dismiss
8. 사용자가 Hidden 카드 탭 → `Routes.Detail` 로 이동해 재분류 가능.

## Exit state

- 시스템 tray: silent 원본 0건, (사용자가 아직 dismiss 하지 않았다면) 요약 1건만.
- Hidden 화면: DB 의 모든 SILENT 알림이 `NotificationCard` 로 렌더링, 개수 = 요약의 count.
- 재분류로 count 변경 시 요약이 자동 갱신 (혹은 count=0 이면 `cancel()` 로 사라짐).

## Out of scope

- Protected 알림 (→ [protected-source-notifications](protected-source-notifications.md) — 그쪽이 먼저 평가되어 cancel 건너뜀)
- Digest 원본 교체 (→ digest-suppression)
- Priority 원본 유지 (→ [priority-inbox](priority-inbox.md))
- Hidden 화면 내 일괄 재분류/삭제 (현재 없음)

## Code pointers

- `notification/SourceNotificationRoutingPolicy` — SILENT 분기
- `notification/SmartNotiNotificationListenerService#onListenerConnected` — `silentSummaryJob` + `silentSourceSweepJob` 기동
- `notification/SmartNotiNotificationListenerService#sweepStaleSilentSources` — tray active 와 DB SILENT row 를 매칭해 잔존 원본 cancel
- `notification/SilentSourceMigrationSweeper` — 순수 정책 (어떤 key 를 cancel 할지 결정)
- `notification/SilentHiddenSummaryNotifier` — 채널 / 빌더 / PendingIntent
- `ui/screens/hidden/HiddenNotificationsScreen` — 목록 UI
- `navigation/Routes#Hidden`, `navigation/AppNavHost` — deep-link 처리
- `MainActivity#extractDeepLinkRoute`

## Tests

- `SourceNotificationRoutingPolicyTest#silent_always_cancels_source_without_individual_replacement`
- `SilentSourceMigrationSweeperTest` — protected 존중, 매칭 로직, multi-key 케이스
- (SilentHiddenSummaryNotifier 자체 테스트는 Robolectric 의존으로 아직 없음)

## Verification recipe

```bash
# 1. SILENT 로 분류될 법한 알림 게시 (광고성 텍스트 / 프로모션 프리셋 활성 상태에서)
adb shell cmd notification post -S bigtext -t "Promo" Promo1 "오늘만 30% 할인"

# 2. 원본이 tray 에서 사라지고 요약만 남는지 확인
adb shell dumpsys notification --noredact | grep -A2 smartnoti_silent_summary

# 3. 요약 탭 시뮬레이션 — Hidden 화면 직접 진입
adb shell am start -n com.smartnoti.app/.MainActivity \
  -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden

# 4. Detail 에서 재분류 후 요약 count 가 감소하는지
```

## Known gaps

- 사용자가 요약을 swipe dismiss 한 뒤 count 가 변하지 않으면 재게시되지 않음 (의도된 동작 — swipe 를 "확인함" 으로 해석). 단, 첫 사용자 입장에서는 혼란 가능.

## Change log

- 2026-04-20: 최초 구현 + 문서화 (commit `acf7c39`, PR #2)
- 2026-04-20: 요약 count 에 persistent 필터 반영 — Home StatPill / Hidden 헤더와 동일 수치 보장
- 2026-04-20: `buildNotificationId` 안정화 — 같은 notification slot 업데이트가 별도 row 로 누적되던 문제 수정. 음악/미디어 알림 개수가 부풀려지지 않음
- 2026-04-20: `SilentSourceMigrationSweeper` 추가 — listener 재연결 시점마다 DB SILENT row 와 매칭되는 시스템 tray 잔존 원본을 cancel. protected 알림은 건너뜀, idempotent. `docs/plans/2026-04-20-silent-source-migration.md` plan 구현.
