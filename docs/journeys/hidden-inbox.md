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
2. `SettingsRepository.observeSettings()` 구독해 `hidePersistentNotifications` 획득 (default `true`).
3. `NotificationRepository.observeAllFiltered(hidePersistentNotifications)` 을 `collectAsStateWithLifecycle` 로 구독.
4. 결과 리스트에 `toHiddenGroups(...)` 적용 — SILENT 알림만 골라 `packageName` 으로 `DigestGroupUiModel` 로 묶음. 각 그룹은 앱명 / count / 최근순 items 로 구성.
5. 상단 영역:
   - 뒤로가기 `IconButton`
   - `ScreenHeader` — eyebrow "숨긴 알림", title "숨겨진 알림 {총 N}건", subtitle 안내
6. `totalCount == 0` 이면 `EmptyState`.
7. 그렇지 않으면 `SmartSurfaceCard` 에 "{앱 수}개 앱에서 {총}건을 숨겼어요" 요약 + 각 그룹에 대해 `DigestGroupCard` 렌더 (앱명, count 배지, 최근 items preview).
8. preview 카드 탭 → `navController.navigate(Routes.Detail.create(id))` (→ [notification-detail](notification-detail.md)).
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

- `ui/screens/hidden/HiddenNotificationsScreen` — 헤더 + `HiddenGroupCardWithBulkActions` 래퍼
- `ui/components/DigestGroupCard` — 그룹 카드 재사용
- `data/local/NotificationRepository#toHiddenGroups` — SILENT 알림을 앱별 `DigestGroupUiModel` 로 변환
- `data/local/NotificationRepository#deleteAllSilent`, `deleteSilentByPackage`, `restoreSilentToPriorityByPackage` — 대량 처리
- `data/local/NotificationDao#deleteAllSilent`, `deleteSilentByPackage` — SQL 기반 삭제
- `navigation/Routes#Hidden`
- `navigation/AppNavHost` — Hidden composable 등록 + deep-link LaunchedEffect
- `domain/model/NotificationId#buildNotificationId` — `sourceEntryKey` 있을 때 timestamp 제외해 같은 알림 slot 이 upsert 로 collapse

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
- SILENT 알림이 수백 건 쌓이면 LazyColumn 성능 미검증.
- "모두 중요로 복구" 후 원본 알림은 이미 tray 에서 제거된 상태라 복구해도 알림센터에 다시 뜨지 않음 — DB 상태만 Priority 로 바뀌어 Priority 탭에 나타남. 의도된 동작이지만 사용자는 "복구 = 시스템 알림 재게시" 를 기대할 수 있음.

## Change log

- 2026-04-20: 최초 구현 + 문서화 (commit `acf7c39`)
- 2026-04-20: persistent 필터 드리프트 수정 — `observeAllFiltered(hidePersistentNotifications)` 사용 (Home StatPill 과 요약 알림 count 일치)
- 2026-04-20: 앱별 그룹 카드 (`DigestGroupCard` 재사용) + `buildNotificationId` 안정화로 같은 slot 반복 업데이트가 한 row 로 collapse — 음악/미디어 알림이 반복 누적되던 UX 문제 해소
- 2026-04-20: 대량 처리 액션 추가 — 헤더의 "전체 모두 지우기" + 그룹별 "모두 중요로 복구" / "모두 지우기". 전체 지우기는 확인 다이얼로그 경유.
