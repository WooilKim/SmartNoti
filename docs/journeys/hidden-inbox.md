---
id: hidden-inbox
title: 숨긴 알림 인박스 (Hidden 화면)
status: shipped
owner: @wooilkim
last-verified: 2026-04-20
---

## Goal

SILENT 로 분류되어 시스템 tray 에서 숨겨진 알림들을 한 화면에 모아 보여주고, 개별 알림의 상세 진입과 재분류를 가능하게 한다. [silent-auto-hide](silent-auto-hide.md) 의 UI 측 종점.

## Preconditions

- 사용자가 SILENT 인박스로 진입 (요약 알림 탭 딥링크 또는 향후 메뉴 진입점)

## Trigger

- `Routes.Hidden` 으로 navigate. 현재 유일한 경로는 `SilentHiddenSummaryNotifier` 의 `contentIntent` / action button. (향후 Settings 의 숨긴 알림 바로가기 등 확장 여지)

## Observable steps

1. `HiddenNotificationsScreen` composable 마운트.
2. `SettingsRepository.observeSettings()` 를 구독해 `hidePersistentNotifications` 값 획득 (default `true`).
3. `NotificationRepository.observeAllFiltered(hidePersistentNotifications)` 을 `collectAsStateWithLifecycle` 로 구독 — persistent 알림을 설정에 맞게 걸러낸 스트림.
4. 결과 리스트에서 `status == NotificationStatusUi.SILENT` 만 남겨 표시.
5. 상단 영역:
   - 뒤로가기 `IconButton` (`Icons.AutoMirrored.Outlined.ArrowBack`)
   - `ScreenHeader` — eyebrow "숨긴 알림", title "숨겨진 알림 {N}건", subtitle "조용히로 분류된 알림이에요. 시스템 알림센터에서는 숨기고 여기에만 모아뒀어요."
6. 비어 있으면 `EmptyState("아직 숨긴 알림이 없어요", "조용히 처리된 알림이 생기면 여기에 모여요")`.
7. 있으면 `SmartSurfaceCard` + `NotificationCard` 리스트 (`key = it.id`).
8. 카드 탭 → `navController.navigate(Routes.Detail.create(id))` (→ [notification-detail](notification-detail.md)).
9. 뒤로가기 → `navController.popBackStack()`.

## Exit state

- 화면에 현재 DB 의 SILENT 알림이 전부 표시됨.
- 사용자가 Detail 에서 재분류하면 observeAll stream 이 emit → 리스트 자동 업데이트.
- 재분류 결과 SILENT 가 0건이 되면 [silent-auto-hide](silent-auto-hide.md) 의 observer 가 요약 알림을 cancel.

## Out of scope

- 요약 알림 관리 (→ [silent-auto-hide](silent-auto-hide.md))
- 일괄 재분류/삭제 (현재 미구현)
- 필터/검색 (현재 단순 리스트만)

## Code pointers

- `ui/screens/hidden/HiddenNotificationsScreen`
- `navigation/Routes#Hidden`
- `navigation/AppNavHost` — Hidden composable 등록 + deep-link LaunchedEffect
- `data/local/NotificationRepository#observeAll`

## Tests

- 현재 없음 (UI 레이어, silent-auto-hide 의 통합 검증에 의존)

## Verification recipe

```bash
# 1. Hidden 화면 직접 진입
adb shell am start -n com.smartnoti.app/.MainActivity \
  -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden

# 2. DB 의 SILENT 개수 = 화면 카드 개수 = 요약 알림 count 가 일치하는지 확인
adb shell dumpsys notification --noredact | grep 숨겨진
```

## Known gaps

- 화면 단독 테스트 부재.
- "모두 지우기" / "Priority 로 일괄 복구" 같은 대량 처리 액션 미제공.
- SILENT 알림이 수백 건 쌓이면 LazyColumn 성능 미검증.

## Change log

- 2026-04-20: 최초 구현 + 문서화 (commit `acf7c39`)
- 2026-04-20: persistent 필터 드리프트 수정 — `observeAllFiltered(hidePersistentNotifications)` 사용 (Home StatPill 과 요약 알림 count 일치)
