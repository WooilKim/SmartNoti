---
id: silent-auto-hide
title: 조용히 분류된 알림 — ARCHIVED / PROCESSED 하위 상태로 보관/처리
status: shipped
owner: @wooilkim
last-verified: 2026-04-22
---

## Goal

SILENT 로 분류된 알림은 즉시 볼 필요가 없다는 판단이지만, 사용자 의도에 따라 두 가지 후속 상태로 나뉜다:

- **조용히 보관 (`SilentMode.ARCHIVED`)** — 아직 확인 대기 중. 시스템 tray 에 원본을 그대로 두고 Hidden 인박스 "보관 중" 탭에 카드로 노출.
- **조용히 처리됨 (`SilentMode.PROCESSED`)** — 사용자가 확인 완료를 표시. Tray 원본은 제거되고 Hidden 인박스 "처리됨" 탭에 읽기 전용 아카이브로 남음.

두 상태 모두에서 SmartNoti 는 `숨겨진 알림 N건` 요약 알림으로 **ARCHIVED 개수만** 별도로 고지한다. PROCESSED 는 이미 tray 바깥이므로 요약 count 에 포함되지 않는다.

## Preconditions

- NotificationListener + POST_NOTIFICATIONS 권한 허용
- 알림이 SILENT 로 분류됨 (→ [notification-capture-classify](notification-capture-classify.md))
- 대상 알림이 protected 가 아님 (→ [protected-source-notifications](protected-source-notifications.md))

## Trigger

외부 앱이 알림을 게시하고, 분류 결과가 SILENT.

## Observable steps

1. `SourceNotificationRoutingPolicy.route(SILENT, *, *, silentMode)` 가 호출되고 라우팅이 결정된다:
   - `silentMode == SilentMode.ARCHIVED` → `cancelSourceNotification = false` (tray 에 원본 유지).
   - `silentMode == SilentMode.PROCESSED` 또는 `null` (legacy / 현재 capture 기본값) → `cancelSourceNotification = true, notifyReplacementNotification = false`.
2. 라우팅이 cancel 이면 리스너가 main thread 에서 `cancelNotification(sbn.key)` 로 tray 원본 제거. ARCHIVED 면 cancel 을 건너뛰고 원본이 그대로 남는다.
3. `NotificationRepository.save(...)` 로 DB 에 `status = SILENT` row 저장. 이때 `silentMode` 는 저장된 `NotificationUiModel` 값 그대로 — 현재 capture 경로는 기본 `null` 로 저장해 기존 cancel 동작을 유지하고, ARCHIVED 전이 배선은 `silent-archive-vs-process-split` plan 의 후속 Task 에서 완결한다 (Known gap 참조).
4. `SmartNotiNotificationListenerService.silentSummaryJob` 이 `repository.observeAll()` 과 `settingsRepository.observeSettings()` 를 `combine` 으로 구독, `filterSilentArchivedForSummary(hidePersistentNotifications)` 로 **ARCHIVED rows** 전체 목록과 count 를 함께 `SilentSummarySnapshot` 으로 전달. `distinctUntilChanged` 로 불필요한 재게시를 차단.
5. 루트 요약 갱신: count 가 직전 값과 다르면 `SilentHiddenSummaryNotifier.post(count)` 호출 (동일 값 연속시 생략). PROCESSED 전이로 ARCHIVED count 가 줄어도 여기서 갱신된다. `smartnoti_silent_summary` 채널(IMPORTANCE_MIN) 로 다음 속성의 알림 게시:
   - title `"보관 중인 조용한 알림 {N}건"`
   - text `"탭: 보관함 열기 · 스와이프: 확인으로 처리"`
   - bigText 는 탭/스와이프 의미를 상세히 설명
   - action button `"숨겨진 알림 보기"`
   - `autoCancel = true`, `silent = true`, visibility `SECRET`
6. 그룹 요약 갱신: 같은 snapshot 에서 `SilentGroupTrayPlanner.plan(previousState, rows)` 를 호출해 per-sender / per-app 그룹별 post/cancel 액션을 계산. 그룹 키는 `SilentNotificationGroupingPolicy.groupKeyFor(row)` — sender trim 이 non-blank 면 `SilentGroupKey.Sender(name)`, 아니면 `SilentGroupKey.App(packageName)` fallback. planner 는 plan Q3-A 규약을 강제한다: **group member 가 2건 이상일 때만** 그룹 summary + children 이 tray 에 노출되고, 1건으로 떨어지면 그 그룹의 summary/child 가 모두 cancel 된다 (SILENT 는 tray 에 혼자 있지 않는다).
7. 그룹 요약 posting: planner 가 돌려준 `summaryPosts` / `childPosts` 에 대해 `SilentHiddenSummaryNotifier.postGroupSummary(key, count, preview, rootDeepLink)` + `postGroupChild(notificationId, entity, key)` 를 main dispatcher 에서 실행. 모두 `smartnoti_silent_group` 채널(IMPORTANCE_MIN), `setGroup(groupTagFor(key))` + summary 는 `setGroupSummary(true)`. 그룹 summary 의 title 은 `"{sender 또는 package} · 조용히 {N}건"`, InboxStyle 로 최근 preview 최대 3줄. Summary `contentIntent` 는 `Routes.Hidden.create(sender=...)` 또는 `packageName=...` 딥링크.
8. 사용자가 그룹 summary 를 tray 에서 탭 → Android SystemUI 가 같은 `setGroup` 값의 children 을 in-tray 에서 펼친다 (SmartNoti UI 진입 없이). 사용자가 summary 의 "숨겨진 알림 보기" action 또는 summary 본문을 탭하면 PendingIntent 로 `MainActivity` 기동, extras 에 `DEEP_LINK_ROUTE = "hidden"` + `DEEP_LINK_SENDER` 또는 `DEEP_LINK_PACKAGE_NAME` 동봉. `MainActivity.extractDeepLinkRoute()` 가 이를 `Routes.Hidden.create(...)` URL 로 재구성해 `AppNavHost` 의 `LaunchedEffect` 가 해당 그룹 필터가 걸린 Hidden 화면으로 네비게이트 (→ [hidden-inbox](hidden-inbox.md)).
9. 루트 요약을 탭하면 위와 동일한 경로로 Hidden 화면에 진입하되 필터 없이 "보관 중" 탭 전체 목록 렌더링. `autoCancel` 로 요약 알림이 자동 dismiss 된다.
10. 사용자가 Hidden 카드 탭 → `Routes.Detail` 로 이동. Detail 에서 ARCHIVED 인 알림에는 "조용히 보관 중" 섹션과 **"처리 완료로 표시"** 버튼이 노출된다.
11. "처리 완료로 표시" 탭 → `NotificationRepository.markSilentProcessed(id)` 가 해당 row 의 `silentMode` 를 `ARCHIVED → PROCESSED` 로 upsert + `MarkSilentProcessedTrayCancelChain` 이 활성 listener 에 tray cancel 을 위임. `reasonTags` 에 `사용자 처리` 가 추가된다. (이미 PROCESSED 거나 SILENT 가 아니면 no-op false 반환.)
12. Repository 업데이트가 `observeAll` 로 흘러가면서 Hidden 화면의 탭별 리스트, 루트 요약 count, 그리고 `SilentGroupTrayPlanner` 의 그룹 요약이 동시에 갱신된다. 해당 그룹이 1건 이하로 떨어지면 group summary + 잔존 child 가 cancel 되어 tray 에서 사라진다.

## Exit state

- 시스템 tray:
  - ARCHIVED 알림 원본: SILENT 라우팅에 따라 cancel 됨 (라우팅 분기의 결과 — ARCHIVED 경로가 원본을 유지하는 건 현재 배선 상 제한적). SmartNoti replacement 알림 없음.
  - 루트 요약 (`smartnoti_silent_summary` 채널): 전체 ARCHIVED count ≥ 1 일 때 게시.
  - 그룹 요약 + children (`smartnoti_silent_group` 채널): 같은 sender/app group 이 ARCHIVED 2건 이상일 때 Android 가 group header + expanded children 으로 묶어서 렌더. 1건인 그룹은 summary 도 child 도 게시되지 않음 (Q3-A).
  - PROCESSED / legacy null 알림: tray 에 원본 없음, 그룹 요약에도 포함되지 않음.
- Hidden 화면:
  - "보관 중" 탭: ARCHIVED 카드만 표시, count = 루트 요약의 N.
  - "처리됨" 탭: PROCESSED + legacy null silentMode row 표시 (마이그레이션 결정).
  - 그룹 summary 딥링크로 진입시 해당 sender/app 그룹이 스크롤/하이라이트된 상태.
- 재분류·전이로 ARCHIVED count 가 0 이 되면 `silentSummaryJob` 이 루트 요약을 `cancel()`, 동시에 `SilentGroupTrayPlanner` 가 모든 그룹 summary + children 을 cancel.

## Out of scope

- Protected 알림 (→ [protected-source-notifications](protected-source-notifications.md) — 그쪽이 먼저 평가되어 cancel 건너뜀)
- Digest 원본 교체 (→ digest-suppression)
- Priority 원본 유지 (→ [priority-inbox](priority-inbox.md))
- IGNORE (무시) — SILENT 와 구분되는 별도 tier. SILENT 는 "조용히 유지" (tray 원본 유지 + Hidden 인박스 노출) 인 반면 IGNORE 는 "즉시 삭제" (tray 원본 cancel + 기본 뷰 전부 제외, [ignored-archive](ignored-archive.md) opt-in 만) — `SourceNotificationRoutingPolicy` / 이 journey 의 routing 은 IGNORE 에 적용되지 않음 (listener 의 early-return 분기가 앞서 처리).
- 시간 기반 자동 아카이브/삭제 (plan 의 Non-goals)
- Swipe-dismiss 를 자동 "처리됨" 으로 해석 (plan Open question — 현재 미배선)

## Code pointers

- `domain/model/SilentMode` — ARCHIVED / PROCESSED enum
- `domain/model/NotificationUiModel#silentMode` — UI 모델 필드 (nullable)
- `notification/SourceNotificationRoutingPolicy` — SILENT × silentMode 분기
- `notification/SmartNotiNotificationListenerService#onListenerConnected` — `silentSummaryJob` 등록 (루트 요약 + 그룹 요약 planner 양쪽 dispatch)
- `notification/SilentHiddenSummaryNotifier` — 루트 요약 (`smartnoti_silent_summary`) + 그룹 summary/child (`smartnoti_silent_group`) 채널 · 빌더 · PendingIntent · `groupTagFor` / `groupSummaryNotificationIdFor` / `groupChildNotificationId`
- `notification/SilentGroupTrayPlanner` — 순수 plan 함수 (previousState + currentSilent → summary/child posts + cancels + nextState). N≥2 임계 강제
- `domain/usecase/SilentNotificationGroupingPolicy` — sender trim → `SilentGroupKey.Sender` / packageName fallback → `SilentGroupKey.App`
- `notification/SilentArchivedSummaryCount` — `filterSilentArchivedForSummary` (루트 요약 row 집계)
- `notification/MarkSilentProcessedTrayCancelChain` — Detail "처리 완료로 표시" 의 tray cancel 연계
- `data/local/NotificationRepository#markSilentProcessed` — ARCHIVED → PROCESSED 전이 mutator
- `data/local/NotificationRepository#toHiddenGroups` — `silentModeFilter` 파라미터
- `domain/usecase/NotificationFeedbackPolicy#markSilentProcessed` — in-memory 전이 + `사용자 처리` 태그
- `ui/screens/hidden/HiddenNotificationsScreen` — 보관/처리 탭 분리
- `ui/screens/detail/NotificationDetailScreen` — "처리 완료로 표시" 버튼 (ARCHIVED 시에만 표시)
- `navigation/Routes#Hidden`, `navigation/AppNavHost` — deep-link 처리
- `MainActivity#extractDeepLinkRoute`

## Tests

- `SourceNotificationRoutingPolicyTest` — SILENT × silentMode 매트릭스 (ARCHIVED / PROCESSED / null)
- `NotificationRepositoryTest` — `silentMode` round-trip + `markSilentProcessed` 전이
- `HiddenGroupsSilentModeFilterTest` — `toHiddenGroups` 의 silentMode 필터 (null → PROCESSED 매핑 포함)
- `SilentArchivedSummaryCountTest` — ARCHIVED 만 집계, persistent 필터 전파 확인
- `NotificationFeedbackPolicyTest` — `markSilentProcessed` idempotent / 비-SILENT no-op
- (SilentHiddenSummaryNotifier 자체 테스트는 Robolectric 의존으로 아직 없음)

## Verification recipe

```bash
# 1. SILENT 로 분류될 법한 알림 게시 (광고성 텍스트 / 프로모션 프리셋 활성 상태에서)
adb shell cmd notification post -S bigtext -t "Promo" Promo1 "오늘만 30% 할인"

# 2. 현재 capture 경로는 legacy cancel (silentMode=null) 유지 → tray 에서 원본 사라지고
#    DB row 는 silentMode=null 로 저장, Hidden "처리됨" 탭에 노출되어야 함
adb shell dumpsys notification --noredact | grep -A2 smartnoti_silent_summary

# 3. 요약 탭 시뮬레이션 — Hidden 화면 기본 "보관 중" 탭 진입
adb shell am force-stop com.smartnoti.app
adb shell am start -n com.smartnoti.app/.MainActivity \
  -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden

# 4. "처리됨" 탭으로 전환 → legacy null silentMode 알림이 모여 있는지 확인
#    (recipe 의 uiautomator tap 좌표는 대응 탭 라벨 "처리됨 · Nn건" 기준)

# 5. 향후 ARCHIVED capture 배선 이후 확장: 보관 경로로 저장된 알림이
#    tray 에 그대로 남고 Detail 의 "처리 완료로 표시" 로 PROCESSED 전이되는지.
#    현재는 Known gap 상태라 이 단계는 SKIP.
```

## Known gaps

- **Cross-status 그룹핑 미구현** — sender-단위 그룹은 현재 SILENT (ARCHIVED) 만 대상으로 한다. 같은 sender 의 PRIORITY / DIGEST 알림은 기존 채널로 독립 게시되어, 예컨대 "엄마" PRIORITY 1 + DIGEST 2 + SILENT 3 이 "엄마 6건" 단일 그룹으로 묶이지 않는다. plan silent-tray-sender-grouping Non-goals 에 명시된 후속 작업.
- **MessagingStyle 힌트 미사용** — `SilentNotificationGroupingPolicy` 는 `sender` 필드만 보고 MessagingStyle 여부를 직접 조회하지 않는다. 쇼핑/뉴스 앱이 상품명/기사 제목을 `sender` 로 흘리면 그 title 이 Sender 그룹 키가 되어 잘못 묶일 수 있음. Plan Q2 의 후속 개선 포인트.
- **기존 SILENT row 마이그레이션** — 업그레이드 이전 SILENT 는 `silentMode = null` 로 남아 있고, Hidden "처리됨" 탭에 모여 보인다. 이미 tray 에서 사라진 상태라 UX 상 모순은 없지만 사용자에게는 "언제 처리됐는지 불명" 으로 보일 수 있음.
- 사용자가 요약을 swipe dismiss 한 뒤 count 가 변하지 않으면 재게시되지 않음 (의도된 동작 — swipe 를 "확인함" 으로 해석). 요약 알림 본문 카피에 해당 의미를 명시.
- 2026-04-21: 에뮬레이터에 설치된 0.1.0 APK (lastUpdateTime 2026-04-20 15:05) 가 `acf7c39` (초기 구현) 의 copy 를 serve 중 — `android.text=탭해서 숨겨진 알림 보기` + bigText `탭하면 전체 목록을 확인할 수 있어요.` 로, `50e04ef` 에서 갱신된 `탭: 목록 보기 · 스와이프: 확인으로 처리` + bigText `옆으로 밀어 없애면 확인한 것으로 처리돼요` 가 반영되어 있지 않음. 소스 (`SilentHiddenSummaryNotifier#post`) 는 이미 새 copy 를 가지고 있으므로 contract drift 아닌 env noise — 릴리즈 빌드 재설치로 해소.

## Change log

- 2026-04-20: 최초 구현 + 문서화 (commit `acf7c39`, PR #2)
- 2026-04-20: 요약 count 에 persistent 필터 반영 — Home StatPill / Hidden 헤더와 동일 수치 보장
- 2026-04-20: `buildNotificationId` 안정화 — 같은 notification slot 업데이트가 별도 row 로 누적되던 문제 수정. 음악/미디어 알림 개수가 부풀려지지 않음
- 2026-04-21: `SilentMode` enum 도입 (ARCHIVED / PROCESSED) + Entity / UI 모델 필드 추가 (plan Task 1, PR #91).
- 2026-04-21: 라우팅 정책 분기 — ARCHIVED 는 source cancel 건너뜀, PROCESSED / null 은 legacy cancel (plan Task 2, PR #100).
- 2026-04-21: Detail 기반 "처리 완료로 표시" 액션 + `NotificationRepository.markSilentProcessed` / `NotificationFeedbackPolicy.markSilentProcessed` 추가 (plan Task 3, PR #103). In-app 상태 flip 까지 — tray cancel 연계는 후속.
- 2026-04-21: `silentSummaryJob` 이 `countSilentArchivedForSummary` 로 ARCHIVED 만 집계하도록 변경 — PROCESSED 전이 시 요약 count 자동 감소 (plan Task 5, PR #110).
- 2026-04-21: Observable steps / Exit state / Code pointers / Known gaps 를 ARCHIVED ↔ PROCESSED 하위 상태로 전면 재작성. capture 경로의 ARCHIVED 기본값 배선은 Known gap 으로 명시 (plan Task 6).
- 2026-04-21: **Per-sender / per-app 그룹 요약 도입** — `SilentGroupTrayPlanner` + `SilentHiddenSummaryNotifier.postGroupSummary/postGroupChild` 로 같은 sender (fallback: packageName) 에 속한 ARCHIVED 알림이 2건 이상일 때 `smartnoti_silent_group` 채널에서 group header + expanded children 으로 tray 에 렌더. 루트 요약 (`smartnoti_silent_summary`) 은 전체 count 용으로 유지. 그룹 summary 탭은 `DEEP_LINK_SENDER` / `DEEP_LINK_PACKAGE_NAME` extra 로 Hidden 화면의 해당 그룹 필터 진입. Observable steps 5~9 를 루트 요약 + 그룹 요약 듀얼 경로로 재작성, Exit state 에 2채널 tray 표현 반영, Code pointers 에 planner / policy / chain 추가, Known gaps 에 cross-status 그룹핑 · MessagingStyle 힌트 부재 항목 추가. 관련 plan: `docs/plans/2026-04-21-silent-tray-sender-grouping.md` Task 5. PR 이전 단계: #105 / #116 / #122 / #127.
- 2026-04-21: **Capture 경로 ARCHIVED 기본값 배선 (drift fix)** — listener 가 SILENT decision 에 대해 `SourceNotificationRoutingPolicy.route(..., silentMode = SilentMode.ARCHIVED)` 를 호출해 신규 SILENT 캡처가 tray 원본을 유지하고 DB row 도 `silentMode = ARCHIVED` 로 영속화된다. protected / persistent 경로는 legacy `null` 유지. 이로써 Observable step 3 이 경고하던 "capture 기본값 미배선" Known gap 이 해소되어 "보관 중" 탭이 실제 유저 알림으로 채워지기 시작. 관련 plan: `docs/plans/2026-04-20-silent-archive-drift-fix.md` Task 1–2. PR #125.
- 2026-04-21: **Detail "처리 완료로 표시" 가 tray cancel 연계 (drift fix)** — `markSilentProcessed(id)` 성공 후 `MarkSilentProcessedTrayCancelChain` 이 활성 listener 인스턴스에 `cancelNotification(sourceEntryKey)` 를 위임해 tray 원본도 제거된다. 활성 listener 가 없으면 best-effort no-op (DB 전이는 보존). Observable step 11 / Code pointers 는 이미 이 경로를 기술하고 있었고, 본 Change log 로 Known gap 해소를 기록. 관련 plan: `docs/plans/2026-04-20-silent-archive-drift-fix.md` Task 3. PR #126.
