---
id: silent-auto-hide
title: 조용히 분류된 알림 — ARCHIVED / PROCESSED 하위 상태로 보관/처리
status: shipped
owner: @wooilkim
last-verified: 2026-04-27
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
- 원본 cancel 경로는 `NotificationSuppressionPolicy` 를 통과해야 한다. 2026-04-24 부터 `SmartNotiSettings.suppressSourceForDigestAndSilent` 의 default 가 `true` 이고, 빈 `suppressedSourceApps` 가 "모든 앱 opt-in" 으로 해석되므로 신규/미설정 사용자에게도 SILENT 의 원본 cancel 분기가 정상 작동한다. 동일 분기를 [`digest-suppression`](digest-suppression.md) 과 공유.

## Trigger

외부 앱이 알림을 게시하고, 분류 결과가 SILENT.

## Observable steps

1. `SourceNotificationRoutingPolicy.route(SILENT, *, *, silentMode)` 가 호출되고 라우팅이 결정된다 (#511 fix, 2026-04-27):
   - `silentMode` 와 무관하게 모든 SILENT decision 은 `cancelSourceNotification = true AND notifyReplacementNotification = true` (Option C cancel-after-post invariant). 이전 `silentMode == ARCHIVED → cancel = false` 분기는 Quiet Hours SILENT 원본이 트레이에 잔존하던 #511 의 직접 원인이었고 PR #513 에서 통일됐다.
   - `silentMode` 자체는 row 에 그대로 영속화 — Hidden 인박스의 보관 중 / 처리됨 탭 라우팅은 변경 없음.
2. 라우팅 결과에 따라 리스너가 `SourceCancellationGateway.cancelSource(sourceKey)` 위임 (`ListenerSourceCancellationGateway` → `SmartNotiNotificationListenerService.cancelSourceEntryIfConnected(...)`) 으로 main thread 에서 tray 원본을 제거 + replacement 게시. 동일 트랜잭션이라 원본+replacement 가 트레이에 동시 존재하는 창은 ms 단위 (#511 Railway-style 4–6 entry pile 해소).
3. `NotificationRepository.save(...)` 로 DB 에 `status = SILENT` row 저장. 이때 `silentMode` 는 저장된 `NotificationUiModel` 값 그대로 — 현재 capture 경로는 기본 `null` 로 저장해 기존 cancel 동작을 유지하고, ARCHIVED 전이 배선은 `silent-archive-vs-process-split` plan 의 후속 Task 에서 완결한다 (Known gap 참조).
4. `SmartNotiNotificationListenerService.silentSummaryJob` 이 `repository.observeAll()` 과 `settingsRepository.observeSettings()` 를 `combine` 으로 구독, `filterSilentArchivedForSummary(hidePersistentNotifications)` 로 **ARCHIVED rows** 전체 목록과 count 를 함께 `SilentSummarySnapshot` 으로 전달. `distinctUntilChanged` 로 불필요한 재게시를 차단.
5. 루트 요약 갱신: count 가 직전 값과 다르면 `SilentHiddenSummaryNotifier.post(count)` 호출 (동일 값 연속시 생략). PROCESSED 전이로 ARCHIVED count 가 줄어도 여기서 갱신된다. `smartnoti_silent_summary` 채널(IMPORTANCE_MIN) 로 다음 속성의 알림 게시:
   - title `"보관 중인 조용한 알림 {N}건"`
   - text `"탭: 보관함 열기 · 스와이프: 확인으로 처리"`
   - bigText 는 탭/스와이프 의미를 상세히 설명
   - action button `"숨겨진 알림 보기"`
   - `autoCancel = true`, `silent = true`, visibility `SECRET`
   - **Auto-dismiss timeout**: `ReplacementNotificationTimeoutPolicy.timeoutMillisFor(settings, SILENT)` 가 non-null 을 반환하면 (default ON / 30분) builder 가 `setTimeoutAfter(...)` 호출. 사용자가 swipe 하지 않아도 NotificationManager 가 해당 시간 후 자동 cancel. 이 timeout 은 **트레이 항목에만** 적용되고 DB row (`status=SILENT`, `silentMode=ARCHIVED`) 는 그대로 — Hidden "보관 중" 탭에서는 그대로 보임. 다음 SILENT 가 들어와 count 가 변하면 동일 timeout 으로 다시 게시.
6. 그룹 요약 갱신: 같은 snapshot 에서 `SilentGroupTrayPlanner.plan(previousState, rows)` 를 호출해 per-sender / per-app 그룹별 post/cancel 액션을 계산. 그룹 키는 `SilentNotificationGroupingPolicy.groupKeyFor(row)` — sender trim 이 non-blank 면 `SilentGroupKey.Sender(name)`, 아니면 `SilentGroupKey.App(packageName)` fallback. planner 는 plan Q3-A 규약을 강제한다: **group member 가 2건 이상일 때만** 그룹 summary + children 이 tray 에 노출되고, 1건으로 떨어지면 그 그룹의 summary/child 가 모두 cancel 된다 (SILENT 는 tray 에 혼자 있지 않는다).
7. 그룹 요약 posting: planner 가 돌려준 `summaryPosts` / `childPosts` 에 대해 `SilentHiddenSummaryNotifier.postGroupSummary(key, count, preview, rootDeepLink)` + `postGroupChild(notificationId, entity, key)` 를 main dispatcher 에서 실행. 모두 `smartnoti_silent_group` 채널(IMPORTANCE_MIN), `setGroup(groupTagFor(key))` + summary 는 `setGroupSummary(true)`. 그룹 summary 의 title 은 `"{sender 또는 package} · 조용히 {N}건"`, InboxStyle 로 최근 preview 최대 3줄. Summary `contentIntent` 는 `Routes.Hidden.create(sender=...)` 또는 `packageName=...` 딥링크. **루트 요약과 동일한 auto-dismiss timeout 적용** — group summary, group child 의 builder 모두 `ReplacementNotificationTimeoutPolicy` 결과를 동일 minutes 값으로 받으므로 timeout 으로 한꺼번에 사라진다. 직후 새 SILENT 가 들어와 N≥2 가 다시 충족되면 planner 가 다시 게시.
8. 사용자가 그룹 summary 를 tray 에서 탭 → Android SystemUI 가 같은 `setGroup` 값의 children 을 in-tray 에서 펼친다 (SmartNoti UI 진입 없이). 사용자가 summary 의 "숨겨진 알림 보기" action 또는 summary 본문을 탭하면 PendingIntent 로 `MainActivity` 기동, extras 에 `DEEP_LINK_ROUTE = "hidden"` + `DEEP_LINK_SENDER` 또는 `DEEP_LINK_PACKAGE_NAME` 동봉. `MainActivity.extractDeepLinkRoute()` 가 이를 `Routes.Hidden.create(...)` URL 로 재구성해 `AppNavHost` 의 `LaunchedEffect` 가 해당 그룹 필터가 걸린 Hidden 화면으로 네비게이트 (→ [hidden-inbox](hidden-inbox.md)).
9. 루트 요약을 탭하면 위와 동일한 경로로 Hidden 화면에 진입하되 필터 없이 "보관 중" 탭 전체 목록 렌더링. `autoCancel` 로 요약 알림이 자동 dismiss 된다.
10. 사용자가 Hidden 카드 탭 → `Routes.Detail` 로 이동. Detail 에서 ARCHIVED 인 알림에는 "조용히 보관 중" 섹션과 **"처리 완료로 표시"** 버튼이 노출된다.
11. "처리 완료로 표시" 탭 → `NotificationRepository.markSilentProcessed(id)` 가 해당 row 의 `silentMode` 를 `ARCHIVED → PROCESSED` 로 upsert + `MarkSilentProcessedTrayCancelChain` 이 활성 listener 에 tray cancel 을 위임. `reasonTags` 에 `사용자 처리` 가 추가된다. (이미 PROCESSED 거나 SILENT 가 아니면 no-op false 반환.)
12. Repository 업데이트가 `observeAll` 로 흘러가면서 Hidden 화면의 탭별 리스트, 루트 요약 count, 그리고 `SilentGroupTrayPlanner` 의 그룹 요약이 동시에 갱신된다. 해당 그룹이 1건 이하로 떨어지면 group summary + 잔존 child 가 cancel 되어 tray 에서 사라진다.

## Exit state

- 시스템 tray:
  - ARCHIVED 알림 원본: SILENT 라우팅에 따라 cancel 됨 (라우팅 분기의 결과 — ARCHIVED 경로가 원본을 유지하는 건 현재 배선 상 제한적). SmartNoti replacement 알림 없음.
  - 루트 요약 (`smartnoti_silent_summary` 채널): 전체 ARCHIVED count ≥ 1 일 때 게시. `replacementAutoDismissEnabled` (default ON) 이면 `replacementAutoDismissMinutes` (default 30) 후 NotificationManager 가 자동 cancel.
  - 그룹 요약 + children (`smartnoti_silent_group` 채널): 같은 sender/app group 이 ARCHIVED 2건 이상일 때 Android 가 group header + expanded children 으로 묶어서 렌더. 1건인 그룹은 summary 도 child 도 게시되지 않음 (Q3-A). 동일 timeout 으로 summary + child 가 함께 자동 cancel.
  - PROCESSED / legacy null 알림: tray 에 원본 없음, 그룹 요약에도 포함되지 않음.
  - DB row: timeout 발화 후에도 `status=SILENT` row 는 그대로 — Hidden "보관 중" 탭에 그대로 노출.
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
- **PRIORITY 원본 알림에 대한 auto-dismiss timeout** — `ReplacementNotificationTimeoutPolicy` 가 PRIORITY/IGNORE 에 대해 항상 null 을 반환하고, `notifySuppressedNotification` 도 PRIORITY 분기에서 early-return 하므로 PRIORITY 원본은 SmartNoti 의 timeout 통제 밖.

## Code pointers

- `domain/model/SilentMode` — ARCHIVED / PROCESSED enum
- `domain/model/NotificationUiModel#silentMode` — UI 모델 필드 (nullable)
- `notification/SourceNotificationRoutingPolicy` — SILENT × silentMode 분기
- `notification/SmartNotiNotificationListenerService#onListenerConnected` — `silentSummaryJob` 등록 (루트 요약 + 그룹 요약 planner 양쪽 dispatch)
- `notification/SilentHiddenSummaryNotifier` — 루트 요약 (`smartnoti_silent_summary`) + 그룹 summary/child (`smartnoti_silent_group`) 채널 · 빌더 · PendingIntent · `groupTagFor` / `groupSummaryNotificationIdFor` / `groupChildNotificationId` · 세 builder 모두 `ReplacementNotificationTimeoutPolicy` 결과로 `setTimeoutAfter` 적용
- `domain/usecase/ReplacementNotificationTimeoutPolicy` — auto-dismiss timeout 결정 helper (SILENT decision + `enabled=true` + `minutes>0` 일 때만 millis 반환)
- `ui/screens/settings/ReplacementAutoDismissPickerSpec` — Settings duration picker 의 preset / label
- `data/settings/SettingsModels` — `replacementAutoDismissEnabled`, `replacementAutoDismissMinutes` (silent-auto-hide 와 digest-suppression 이 공유)
- `notification/SilentGroupTrayPlanner` — 순수 plan 함수 (previousState + currentSilent → summary/child posts + cancels + nextState). N≥2 임계 강제
- `domain/usecase/SilentNotificationGroupingPolicy` — sender trim → `SilentGroupKey.Sender` / packageName fallback → `SilentGroupKey.App`
- `notification/SilentArchivedSummaryCount` — `filterSilentArchivedForSummary` (루트 요약 row 집계)
- `notification/MarkSilentProcessedTrayCancelChain` — Detail "처리 완료로 표시" 의 tray cancel 연계
- `notification/SourceCancellationGateway` + `ListenerSourceCancellationGateway` — replacement post 직후 source cancel 위임 port (Option C, #511)
- `data/local/MigrateOrphanedSourceCancellationRunner` — 1회성 cohort 정리 sweep (DataStore flag `migrate_orphaned_source_cancellation_v1_applied` 게이트, 리스너 unbind 시 deferred)
- `data/local/ActiveSourceNotificationInspector` — 리스너 bind 여부 + sourceEntryKey 활성 여부 조회 port
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
- **MessagingStyle 힌트 미사용** (resolved 2026-04-27, plan `docs/plans/2026-04-27-silent-sender-messagingstyle-gate.md`) — `SilentNotificationGroupingPolicy` 는 `sender` 필드만 보고 MessagingStyle 여부를 직접 조회하지 않는다. 쇼핑/뉴스 앱이 상품명/기사 제목을 `sender` 로 흘리면 그 title 이 Sender 그룹 키가 되어 잘못 묶일 수 있음. Plan Q2 의 후속 개선 포인트. → plan: `docs/plans/2026-04-27-silent-sender-messagingstyle-gate.md`
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
- 2026-04-24: Verification re-run on emulator-5554 post-#292 (default-on suppression toggle + empty-set=all-apps + v1 migration). Live `dumpsys` confirmed root summary copy/channel/importance/vis, group summaries with N≥2 threshold (Promo=4, three other senders=2), and capture default ARCHIVED branch (source `shell_cmd` retained on fresh post; root count 26→27, Promo group 4→5 within 4s). PASS — Observable steps 1/3/5/7 and Exit state hold; #292's policy change does not regress the ARCHIVED path.
- 2026-04-24: **SILENT 원본 cancel 경로의 default-on 보장** — `NotificationSuppressionPolicy` 가 빈 `suppressedSourceApps` 를 "모든 앱 opt-in" 으로 해석하도록 변경되고, `SmartNotiSettings.suppressSourceForDigestAndSilent` 의 default 를 `true` 로 flip. 기존 사용자에게는 `SettingsRepository.applyPendingMigrations` 의 v1 one-shot 마이그레이션이 한 번 덮어씀. SILENT 도 같은 policy 분기를 사용하므로 신규/미설정 사용자에게도 capture 경로의 cancel 이 정상 작동한다 (PROCESSED 분기 — ARCHIVED 분기는 이미 cancel 을 건너뛰는 것이 의도). 관련 plan: `docs/plans/2026-04-24-duplicate-notifications-suppress-defaults-ac.md`. 커밋: d9c3ff6 / e2a472d / 8aada48 / 704dfc7.
- 2026-04-27: **루트 요약 + 그룹 summary/child 에 auto-dismiss timeout 적용** — `SilentHiddenSummaryNotifier` 의 세 게시 경로 (archived summary, group summary, group child) 모두 builder 가 `ReplacementNotificationTimeoutPolicy.timeoutMillisFor(settings, SILENT)` 결과를 `setTimeoutAfter(...)` 로 적용. `SmartNotiSettings.replacementAutoDismissEnabled` (default ON) + `replacementAutoDismissMinutes` (default 30) 도입, `SettingsRepository.applyPendingMigrations()` 의 v2 one-shot (`replacement_auto_dismiss_migration_v2_applied` DataStore 키 게이트) 으로 기존 사용자에게도 default 주입. Settings 의 "고급 숨김 옵션" 서브섹션에 토글 + duration preset 노출. Group summary 와 child 가 같은 timeout 값을 받으므로 동시에 자동 cancel; 새 SILENT 가 들어와 임계 N≥2 가 다시 충족되면 planner 가 다시 게시. DB row 는 timeout 발화 후에도 그대로. 관련 plan: `docs/plans/2026-04-27-tray-replacement-auto-dismiss-timeout.md` (Tasks 1-4 shipped via #415 + #417).
- 2026-04-27: **SILENT 트레이 sender 그룹 키가 MessagingStyle 힌트 게이트로 좁혀짐** — `MessagingStyleSenderResolver` 가 listener 의 sender 추출을 게이트해, `EXTRA_TITLE` 은 `EXTRA_TEMPLATE` 가 표준/Compat MessagingStyle 이거나 `EXTRA_MESSAGES` 가 non-empty 일 때만 sender 로 채택된다. 비-messaging 앱(쇼핑/뉴스/프로모션)이 상품명·기사 제목을 title 로 흘려도 sender=null 로 떨어져 `SilentNotificationGroupingPolicy` 의 기존 App fallback 분기로 묶인다 (관측: 두 promo 알림이 `smartnoti_silent_group_sender:상품A/B` 로 분리되지 않고 `smartnoti_silent_group_app:com.android.shell` 한 그룹으로 클러스터). 메신저 알림은 `smartnoti_silent_group_sender:엄마AdbTest` 로 기존처럼 묶인다. PERSON 룰 매칭은 messaging-style 인 발신자에서만 발화하도록 정확도가 함께 좁혀진다 (의도된 변경). 기존 row 마이그레이션은 수행하지 않음 — 신규 row 부터 정상 키로 저장되며 휘발성 SILENT cohort 가 자연 교체. 관련 plan: `docs/plans/2026-04-27-silent-sender-messagingstyle-gate.md`.
- 2026-04-27: Verification re-run on emulator-5554 post-#427 (`MessagingStyleSenderResolver` gate). Scenario A (`-S messaging --conversation MomMessenger_51456`, two messages) clustered under `smartnoti_silent_group_sender:MomMessenger_51456` with N=2 group summary + child — Sender key path intact for true MessagingStyle. Scenario B (`-S bigtext` with two distinct promo titles `PromoProductA_*` / `PromoProductB_*`) did NOT split into two single-member Sender groups; both clustered under `smartnoti_silent_group_app:com.android.shell` (App-fallback) — confirms non-MessagingStyle titles are no longer adopted as `sender`. Root summary `보관 중인 조용한 알림 8건` on `smartnoti_silent_summary` (importance=1, vis=SECRET, pri=-2) intact (step 5). PASS — Observable steps 5/6/7 + Exit state hold; #427 ships its intended grouping behavior. Known gaps "MessagingStyle 힌트 미사용" already marked resolved in same PR.
- 2026-04-27: **#511 fix shipped — Option C cancel-after-post invariant** (PR #513 `5db1208` + closure PR #516). `SourceNotificationRoutingPolicy.route(SILENT, *, *, *)` now returns `cancelSourceNotification = true AND notifyReplacementNotification = true` for **every** SILENT decision — `SilentMode == ARCHIVED` no longer keeps the source in the tray. Previously the ARCHIVED branch returned `cancelSourceNotification = false`, which was the root cause of #511 (Quiet Hours → SILENT/ARCHIVED → Gmail source stayed alongside `smartnoti_silent_group` child for the Railway-style 4–6-entry pile). `MigrateOrphanedSourceCancellationRunner` (gated by DataStore flag `migrate_orphaned_source_cancellation_v1_applied`) sweeps any historical row with `replacementNotificationIssued = 1 AND sourceEntryKey IS NOT NULL` whose source is still live and asks the listener to cancel; the runner defers without flipping the flag if the listener isn't yet bound (R4 mitigation). `SilentMode` is still persisted on the row so inbox-tab routing (보관 중 / 처리됨) is unchanged — only the source-tray decision flipped. Observable step 1 (ARCHIVED branch) is therefore now consistent with PROCESSED — both cancel the source. Exit state's "ARCHIVED 알림 원본: SILENT 라우팅에 따라 cancel 됨" already anticipated this and is now uniformly true for SILENT (no longer "라우팅 분기의 결과"). Live ADB verification on R3CY2058DLJ (lastUpdateTime 2026-04-28 05:43): `cmd notification post -t Issue511E2E_$TS Iss511E2EPath_$TS body` → logcat shows source `pkg=com.android.shell|2020|Iss511E2EPath_1777325956` removed with `reason: 10` REASON_LISTENER_CANCEL at 06:39:16.230 (25ms after post at 06:39:16.205), then `com.smartnoti.app: notify(2081278449, ..., channel=smartnoti_replacement_silent_min_off_secret_noheadsup)` at 06:39:16.231, followed by `smartnoti_silent_summary` (id=23057) + `smartnoti_silent_group_app:com.android.shell` summary (id=1238202785, GROUP_SUMMARY) + 2 children. Active tray confirms source absent (`NotificationRecord(...)` grep for `Iss511E2EPath` empty). DataStore confirms migration flag set (one-shot ran on cold start). 23 rows now carry `replacementNotificationIssued=1` post-fix vs. 0 in the pre-install Railway cohort. **Code pointers added**: `notification/SourceCancellationGateway` (interface + `ListenerSourceCancellationGateway` impl), `data/local/MigrateOrphanedSourceCancellationRunner`, `data/local/ActiveSourceNotificationInspector`, `SmartNotiNotificationListenerService#cancelSourceEntryIfConnected` / `#activeSourceKeysSnapshotIfConnected`. **Known gap removed**: "Quiet Hours SILENT 의 원본 잔존" (resolved by Option C). `last-verified` 2026-04-27 → 2026-04-27 unchanged (same day; live ADB capture above counts as recipe execution).

- 2026-04-26: v1 loop tick re-verify on emulator-5554 (PASS) post-#327 (`<queries>` manifest fix for app-label visibility). `dumpsys notification --noredact` confirmed root summary `보관 중인 조용한 알림 31건` + text `탭: 보관함 열기 · 스와이프: 확인으로 처리` + action `숨겨진 알림 보기` + channel `smartnoti_silent_summary` (importance=1, vis=SECRET, pri=-2) — Observable step 5 계약 일치. 그룹 채널 `smartnoti_silent_group` 의 N≥2 임계 강제도 다수 sender groupKey (`smartnoti_silent_group_sender:FooBarSender`, `…PathBTest_003943`, `…PathATest_0422`) 로 확인 (step 6/7). 콜드-스타트 deep-link (`am force-stop` → `am start -e DEEP_LINK_ROUTE hidden`) → `Hidden` 화면 헤더 `보관 31건 · 처리 2건` + 기본 탭 `보관 중 · 31건` + 요약 카피 `1개 앱에서 31건을 보관 중이에요.` (step 8/9, hidden-inbox cross-cut). `처리됨 · 2건` 탭 전환 시 서브카피 `이미 확인했거나 이전 버전에서 넘어온 알림이에요.` 1건 노출. 그룹 카드 초기 collapsed (preview 라벨 0건), chevron (`펼치기`) tap → `최근 묶음 미리보기` + bulk action row (`모두 중요로 복구` / `모두 지우기`) 노출 (스크롤 후 확인). DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26 bump.
