---
id: hidden-inbox
title: 숨긴 알림 인박스 (Hidden 화면) — 보관 / 처리 탭 분리
status: deprecated
superseded-by: docs/journeys/inbox-unified.md
owner: @wooilkim
last-verified: 2026-04-27
---

> **Deprecated 2026-04-22** — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 (#240) 이후 Hidden 화면은 독립 deep-link 타겟으로는 남아 있으나 (silent summary contentIntent 용), 1차 사용자 진입 경로는 `정리함` 통합 탭의 "보관 중" / "처리됨" 서브탭이다. 현재 동작 계약은 [inbox-unified](inbox-unified.md) 가 소유한다. 이 문서는 tray deep-link 경로 + `HiddenNotificationsScreen` 내부 세그먼트 계약에 대한 기록으로 남긴다 — 신규 기능 작성 시에는 inbox-unified 를 기준으로 갱신하거나 통합하라.

## Goal

SILENT 로 분류된 알림을 **보관 중** (`SilentMode.ARCHIVED`) / **처리됨** (`SilentMode.PROCESSED` + legacy null) 두 탭으로 나눠 보여주고, 개별 알림의 상세 진입·재분류·bulk 처리를 가능하게 한다. [silent-auto-hide](silent-auto-hide.md) 의 UI 측 종점.

## Preconditions

- 사용자가 SILENT 인박스로 진입 (요약 알림 탭 딥링크 또는 향후 메뉴 진입점)

## Trigger

- `Routes.Hidden` 으로 navigate. 진입 경로는 세 가지:
  - `SilentHiddenSummaryNotifier` 의 **루트** 보관 요약 (`smartnoti_silent_summary` 채널) `contentIntent` / `"숨겨진 알림 보기"` action → `DEEP_LINK_ROUTE = "hidden"` 만 전달, 필터 없음 (전체 보관 목록 + 기본 "보관 중" 탭).
  - 같은 notifier 의 **그룹 summary** (`smartnoti_silent_group` 채널, sender 또는 app 단위) `contentIntent` → `DEEP_LINK_ROUTE = "hidden"` + `DEEP_LINK_SENDER` 또는 `DEEP_LINK_PACKAGE_NAME` extra 전달. `MainActivity#extractDeepLinkRoute` 가 두 extra 를 읽어 `Routes.Hidden.create(sender = ..., packageName = ...)` URL 로 재구성 → `AppNavHost` 가 해당 쿼리로 composable 진입.
  - Android SystemUI 가 같은 `setGroup` 태그의 children 을 tray 안에서 펼쳐주는 경로는 `Routes.Hidden` 을 거치지 않고 tray 상에서 완결된다 (→ [silent-auto-hide](silent-auto-hide.md) step 8). Hidden 화면으로 들어오는 경로는 summary 탭일 때만.
- (향후 Settings 의 숨긴 알림 바로가기 등 확장 여지)

## Observable steps

1. `HiddenNotificationsScreen` composable 마운트.
2. `SettingsRepository.observeSettings()` 구독해 `hidePersistentNotifications` 획득 (default `true`).
3. `NotificationRepository.observeAllFiltered(hidePersistentNotifications)` 을 `collectAsStateWithLifecycle` 로 구독.
4. 구독 결과 `filteredNotifications` 에 `toHiddenGroups(hidePersistentNotifications = false, silentModeFilter = ...)` 를 두 번 적용해 탭별 그룹을 만든다:
   - `silentModeFilter = SilentMode.ARCHIVED` → 보관 중 탭 그룹 (`archivedGroups`)
   - `silentModeFilter = SilentMode.PROCESSED` → 처리됨 탭 그룹 (`processedGroups`). `toHiddenGroups` 규약상 `silentMode == null` legacy row 도 이 필터에 포함된다 (마이그레이션 결정).
   - IGNORE row 는 `toHiddenGroups` 가 `status == SILENT` 로 선필터하므로 두 탭 어느 쪽에도 포함되지 않음 — 오직 [ignored-archive](ignored-archive.md) 에서만 노출.
5. 탭 상태는 `rememberSaveable { HiddenTab.Archived }` — 기본은 "보관 중".
6. 상단 영역:
   - 뒤로가기 `IconButton`
   - `ScreenHeader` — eyebrow "숨긴 알림", title "보관 {archivedCount}건 · 처리 {processedCount}건", subtitle "'보관 중' 은 아직 확인하지 않은 알림, '처리됨' 은 이미 훑어본 알림" 안내
   - `HiddenTabRow` 세그먼트 컨트롤 — "보관 중 · N건" / "처리됨 · N건". 선택된 세그먼트는 `primaryContainer` 배경 + SemiBold.
7. 선택된 탭의 `visibleGroups.isEmpty()` 이면 `HiddenTabEmptyState(tab)`:
   - 보관 중 빈 상태: "보관 중인 알림이 없어요 / 지금은 모두 처리됐어요. 새로 조용히 분류된 알림이 오면 여기에 먼저 모여요."
   - 처리됨 빈 상태: "처리된 알림이 없어요 / 보관 중 알림을 상세에서 '처리 완료로 표시' 하면 이 탭에 쌓여요."
8. 그렇지 않으면:
   - `SmartSurfaceCard` 요약 문구 — 보관 중 탭은 "{앱 수}개 앱에서 {count}건을 보관 중이에요", 처리됨 탭은 "{앱 수}개 앱에서 {count}건을 처리했어요". 각각 보조 문구 + `OutlinedButton` "전체 숨긴 알림 모두 지우기" (두 탭 공통 카운트 기준).
   - 각 그룹은 `DigestGroupCard(collapsible = true)` 로 렌더. **기본 상태는 collapsed** — 앱명 / count 배지 / 한 줄 summary / chevron 만 표시. 프리뷰 items 와 그룹별 bulk action (모두 중요로 복구 / 모두 지우기) 은 숨김.
9. 그룹 헤더 탭 → `rememberSaveable(groupId)` expanded state 가 toggle. expanded 시 "최근 묶음 미리보기" 라벨 + 최근 items 최대 3건 + bulk action row 가 `AnimatedVisibility` 로 노출. chevron 은 180° 회전.
10. preview 카드 탭 → `navController.navigate(Routes.Detail.create(id))` (→ [notification-detail](notification-detail.md)). 여러 그룹을 동시에 펼칠 수 있음 (accordion 아님).
11. "전체 숨긴 알림 모두 지우기" 는 `AlertDialog` 로 확인 → `repository.deleteAllSilent()` (두 탭 모두 포함).
12. 뒤로가기 → `navController.popBackStack()`.

## Exit state

- 화면에 현재 DB 의 SILENT 알림이 ARCHIVED / PROCESSED 두 탭으로 분리 표시.
- Detail 에서 "처리 완료로 표시" 하면 해당 row 가 ARCHIVED → PROCESSED 로 이동하고, `observeAllFiltered` stream 이 emit → 보관 중 탭 count 감소, 처리됨 탭에 추가.
- ARCHIVED count 가 0 이 되면 [silent-auto-hide](silent-auto-hide.md) 의 `silentSummaryJob` 이 요약 알림을 cancel.

## Out of scope

- 요약 알림 관리 (→ [silent-auto-hide](silent-auto-hide.md))
- ARCHIVED ↔ PROCESSED 상태 bulk 전이 (현재 Detail 단건만 지원)
- 필터/검색 (현재 탭별 단순 리스트만)

## Code pointers

- `ui/screens/hidden/HiddenNotificationsScreen` — 헤더 + 탭 바 + 탭별 empty / 리스트. `HiddenTab`, `HiddenTabRow`, `HiddenTabSegment`, `HiddenTabEmptyState` 컴포지트
- `ui/components/DigestGroupCard` — 그룹 카드 재사용 (`collapsible = true`)
- `data/local/NotificationRepository#toHiddenGroups` — `silentModeFilter` 파라미터 (null/ARCHIVED/PROCESSED)
- `data/local/NotificationRepository#markSilentProcessed` — Detail 전이 mutator (→ [notification-detail](notification-detail.md))
- `data/local/NotificationRepository#deleteAllSilent`, `deleteSilentByPackage`, `restoreSilentToPriorityByPackage` — 대량 처리 (두 탭 공통)
- `data/local/NotificationDao#deleteAllSilent`, `deleteSilentByPackage` — SQL 기반 삭제
- `domain/model/SilentMode` — ARCHIVED / PROCESSED enum
- `navigation/Routes#Hidden` — `sender` / `packageName` optional query param + `create(...)` builder
- `navigation/AppNavHost` — Hidden composable 등록 (navArgs w/ nullable defaults) + deep-link LaunchedEffect
- `ui/screens/hidden/HiddenDeepLinkFilterResolver` — query param → `SilentGroupKey?` + 그룹 매칭 규칙
- `domain/model/NotificationId#buildNotificationId` — `sourceEntryKey` 있을 때 timestamp 제외해 같은 알림 slot 이 upsert 로 collapse

## Tests

- `HiddenGroupsSilentModeFilterTest` — `toHiddenGroups` 의 silentMode 필터 (null → PROCESSED 매핑 포함)
- Compose UI 단독 테스트 부재 (silent-auto-hide 통합 검증에 의존)

## Verification recipe

```bash
# 1. Hidden 화면 직접 진입 (recipe fragility 대비 cold start)
adb shell am force-stop com.smartnoti.app
adb shell am start -n com.smartnoti.app/.MainActivity \
  -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden

# 2. 기본 탭 "보관 중" 이 선택되어 있는지 uiautomator 로 확인
adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml | grep -c "보관 중"
# expected: >= 1

# 3. 처리됨 탭으로 전환 — 탭 라벨 "처리됨" 좌표로 tap
adb shell input tap <x> <y>
sleep 1
adb shell uiautomator dump /sdcard/ui2.xml && adb shell cat /sdcard/ui2.xml | grep -c "이미 확인했거나"
# expected: >= 1 (요약 카드 서브 카피)

# 4. 그룹 카드 초기 collapsed 검증 — "최근 묶음 미리보기" 문자열이 없어야 함
adb shell uiautomator dump /sdcard/ui3.xml && adb shell cat /sdcard/ui3.xml | grep -c "최근 묶음 미리보기"
# expected: 0

# 5. 그룹 헤더 탭 후 expand 검증 — preview 라벨 + bulk action 이 나타나야 함
adb shell input tap <x> <y>
sleep 1
adb shell uiautomator dump /sdcard/ui4.xml && adb shell cat /sdcard/ui4.xml | grep -c "최근 묶음 미리보기"
# expected: >= 1
```

## Known gaps

- 화면 단독 Compose 테스트 부재.
- SILENT 알림이 수백 건 쌓이면 LazyColumn 성능 미검증.
- "모두 중요로 복구" 후 원본 알림은 이미 tray 에서 제거된 상태라 복구해도 알림센터에 다시 뜨지 않음 — DB 상태만 Priority 로 바뀌어 Priority 탭에 나타남. 의도된 동작이지만 사용자는 "복구 = 시스템 알림 재게시" 를 기대할 수 있음.
- Recipe fragility (2026-04-21 관측): `MainActivity` 가 이미 top-most 로 떠있을 때 단일 `am start … -e DEEP_LINK_ROUTE hidden` 은 `Intent{has extras}` 만 delivered 로 남고 `AppNavHost` 의 `LaunchedEffect(deepLinkRoute)` 가 재발화하지 않아 Hidden 화면으로 이동하지 않음. 검증 시 `am force-stop com.smartnoti.app` 후 cold start 필수. 이 경로는 rules-feedback-loop / notification-detail recipe 에도 동일 적용.

## Change log

- 2026-04-20: 최초 구현 + 문서화 (commit `acf7c39`)
- 2026-04-20: persistent 필터 드리프트 수정 — `observeAllFiltered(hidePersistentNotifications)` 사용 (Home StatPill 과 요약 알림 count 일치)
- 2026-04-20: 앱별 그룹 카드 (`DigestGroupCard` 재사용) + `buildNotificationId` 안정화로 같은 slot 반복 업데이트가 한 row 로 collapse — 음악/미디어 알림이 반복 누적되던 UX 문제 해소
- 2026-04-20: 대량 처리 액션 추가 — 헤더의 "전체 모두 지우기" + 그룹별 "모두 중요로 복구" / "모두 지우기". 전체 지우기는 확인 다이얼로그 경유.
- 2026-04-21: 앱별 그룹 카드 기본 collapsed. `DigestGroupCard` 에 `collapsible` + `bulkActions` slot 추가 (DigestScreen 은 기본값으로 무변경). 헤더 탭으로 expand/collapse, chevron rotate + `AnimatedVisibility` 로 preview + bulk action row 동시 토글. 그룹별 expanded state 는 `rememberSaveable(groupId)` 로 유지. 관련 plan: `docs/plans/2026-04-20-hidden-inbox-collapsible-groups.md`.
- 2026-04-21: **보관 중 / 처리됨 탭 분리** — `HiddenTab` 세그먼트 + `toHiddenGroups(silentModeFilter = ...)` 기반 탭별 리스트, 탭별 empty state, 헤더 count 이원화. legacy `silentMode == null` row 는 "처리됨" 으로 매핑해 기존 사용자의 빈 "보관 중" 탭 혼동 방지 (plan Task 4, PR #107).
- 2026-04-21: Observable steps / Exit state / Code pointers 를 탭 분리 구조로 재작성. 관련 plan: `docs/plans/2026-04-21-silent-archive-vs-process-split.md` Task 6.
- 2026-04-21: tray 그룹 summary deep-link 필터 지원 — `Routes.Hidden` 에 `sender` / `packageName` 쿼리 추가, `HiddenNotificationsScreen` 이 `initialFilter` 로 Archived 탭 자동 선택 + 해당 그룹 스크롤/하이라이트. 관련 plan: `docs/plans/2026-04-21-silent-tray-sender-grouping.md` Task 4.
- 2026-04-21: Trigger 섹션을 세 가지 진입 경로 (루트 요약 · 그룹 summary 딥링크 · Android SystemUI 의 in-tray children 펼침) 로 정리하고, 그룹 summary 경로가 `DEEP_LINK_SENDER` / `DEEP_LINK_PACKAGE_NAME` extra 를 어떻게 `Routes.Hidden.create(...)` URL 로 재구성하는지 명시. silent-auto-hide 의 재작성된 Observable steps 5~9 와 용어/채널명 정렬. 관련 plan: `docs/plans/2026-04-21-silent-tray-sender-grouping.md` Task 5.
- 2026-04-21: **"보관 중" 탭이 실제로 채워지기 시작 (drift fix)** — listener 가 신규 SILENT 캡처 시 `silentMode = ARCHIVED` 로 저장하도록 배선되어 (PR #125) Hidden "보관 중" 탭이 기본 경로에서 채워진다. Detail "처리 완료로 표시" 는 DB flip 에 더해 tray 원본 cancel 까지 연계되어 (PR #126) 사용자 체감 "처리 = tray 에서 사라짐" 과 일치. Known gap "Capture 경로의 ARCHIVED 기본값 미배선 여파" 해소. 관련 plan: `docs/plans/2026-04-20-silent-archive-drift-fix.md` Task 1–3.
- 2026-04-21: IGNORE (무시) 4번째 분류 tier 가 Hidden 의 보관/처리 두 탭에서 모두 제외됨을 명시 — `toHiddenGroups` 가 이미 `status == SILENT` 로 선필터하므로 자동 배제, IGNORE row 는 [ignored-archive](ignored-archive.md) 전용. Plan: `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6 (#185 `9a5b4b9`). `last-verified` 는 ADB 검증 전까지 bump 하지 않음.
- 2026-04-21: **ADB verification on fresh APK (post-IGNORE) PASS** — emulator-5554 에서 cold-start deep-link (`am force-stop` → `am start -e DEEP_LINK_ROUTE hidden`) 후 Recipe 5단계 전부 일치: (1) 헤더 `보관 10건 · 처리 3건` + 기본 탭 "보관 중" 선택, (2) "처리됨" tap 후 요약 서브카피 `이미 확인했거나…` 1건 관측, (3) 보관/처리 두 탭 모두 초기 `최근 묶음 미리보기` 0건 (collapsed 계약), (4) Archived 그룹 헤더 tap 후 `최근 묶음 미리보기` + bulk action row (`모두 중요로 복구` / `모두 지우기`) 노출. IGNORE row 는 두 탭 어디에도 누출 없음 (`toHiddenGroups` 의 `status == SILENT` 선필터 binary 상 동작 증명). 직전 change-log 가 남겨둔 "ADB 검증 전까지 bump 하지 않음" 보류 해소.
- 2026-04-22: **Deprecated** — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 (#240) 이후 `정리함` 통합 탭 ([inbox-unified](inbox-unified.md)) 이 기본 진입 경로를 소유한다. `Routes.Hidden` 은 silent summary / 그룹 summary deep-link 타겟으로 계속 사용됨. 문서는 참조용으로 남김.
- 2026-04-26: Plan `2026-04-26-inbox-bundle-preview-see-all` ship — `DigestGroupCard` (보관 중 / 처리됨 그룹) preview 가 inline expansion 지원 ("전체 보기 · ${remaining}건 더" CTA, `rememberSaveable` 로 그룹별 `showAll` 영속). Standalone deep-link 진입 경로 (`Routes.Hidden`) 도 동일 컴포넌트를 공유하므로 `items.size > 3` 그룹의 4번째 이후 row Detail 진입 가능. ADB 검증 (Coupang 5건 → 처리됨 탭 expand → CTA → all 5 → row tap → Detail → back → 상태 복원 → 토글) 은 [inbox-unified](inbox-unified.md) Change log 에 기록.
- 2026-04-26: **ADB verification PASS (rotation sweep)** — emulator-5554 cold-start `am force-stop` → `am start -e DEEP_LINK_ROUTE hidden` 후 Recipe 5단계 전부 통과: 헤더 `보관 16건 · 처리 6건` + 기본 탭 "보관 중" 선택, "처리됨 · 6건" 탭 tap 후 요약 서브카피 `이미 확인했거나…` 1건, 두 탭 모두 초기 collapsed (`최근 묶음 미리보기` 0건), Coupang 5건 그룹 헤더 tap 후 expand → preview 라벨 + `전체 보기 · 2건 더` CTA (2026-04-26 see-all 시퀀스 회귀 확인) + bulk action row (`모두 중요로 복구` / `모두 지우기`) 노출. `last-verified` 2026-04-21 → 2026-04-26.
- 2026-04-27: Plan `2026-04-27-inbox-sort-by-priority-or-app` ship — Embedded host (`InboxScreen`) 에서 사용자가 선택한 `SmartNotiSettings.inboxSortMode` 가 `HiddenNotificationsScreen(sortMode)` 신규 파라미터로 흘러 ARCHIVED / PROCESSED 두 탭의 그룹 순서를 결정 (`InboxSortPlanner.sortGroups`). Standalone 진입 경로 (`Routes.Hidden` deep-link, tray group-summary contentIntent) 는 `sortMode` default `RECENT` 로 fallback — 기존 동작 그대로. Standalone 도 settings 를 collect 하도록 확장은 별도 plan. 활성 contract 는 [inbox-unified](inbox-unified.md).
- 2026-04-27: **ADB verification PASS (rotation sweep, post-`SettingsRepository` split #477)** — emulator-5554, APK `lastUpdateTime=2026-04-27 15:32:46`. Recipe 5단계 모두 통과: cold-start `am force-stop` → `am start -e DEEP_LINK_ROUTE hidden` 후 헤더 `보관 11건 · 처리 12건` + 기본 탭 `보관 중 · 11건` 선택, `처리됨 · 12건` 탭 tap (786,580) 후 요약 서브카피 `이미 확인했거나 이전 버전에서 넘어온 알림이에요…` 1건, 두 탭 모두 초기 collapsed (`최근 묶음 미리보기` 0건; chevron `펼치기` content-desc), Coupang 그룹 헤더 tap (262,1545) → expand → preview 라벨 + 3 row + `전체 보기 · 3건 더` CTA + bulk action row (`모두 중요로 복구` / `모두 지우기`) 모두 노출 (chevron `접기` content-desc). 두 번째 그룹 (SmartNotiDebugTester) 도 collapsed default. `SettingsRepository` per-domain split 후에도 `observeSettings()` → `hidePersistentNotifications` 흐름 무회귀. `last-verified` 2026-04-26 → 2026-04-27.
