---
id: silent-auto-hide
title: 조용히 분류된 알림 — ARCHIVED / PROCESSED 하위 상태로 보관/처리
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
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
4. `SmartNotiNotificationListenerService.silentSummaryJob` 이 `repository.observeAll()` 과 `settingsRepository.observeSettings()` 를 `combine` 으로 구독, `countSilentArchivedForSummary(hidePersistentNotifications)` 로 **ARCHIVED 개수만** 집계.
5. count 가 직전 값과 다르면 `SilentHiddenSummaryNotifier.post(count)` 호출 (동일한 값이 연속되면 재게시하지 않음). PROCESSED 전이로 ARCHIVED count 가 줄어도 여기서 갱신된다.
6. `smartnoti_silent_summary` 채널(IMPORTANCE_MIN) 로 알림 게시:
   - title `"숨겨진 알림 {N}건"` — N = ARCHIVED 개수
   - text `"탭: 목록 보기 · 스와이프: 확인으로 처리"`
   - bigText 는 탭/스와이프 의미를 상세히 설명
   - action button `"숨겨진 알림 보기"`
   - `autoCancel = true`, `silent = true`, visibility `SECRET`
7. 사용자가 요약 본문 또는 액션 탭:
   1. PendingIntent 가 `MainActivity` 를 FLAG_ACTIVITY_NEW_TASK + CLEAR_TOP 로 기동하며 extra `com.smartnoti.app.extra.DEEP_LINK_ROUTE = "hidden"` 전달
   2. `MainActivity.extractDeepLinkRoute()` 에서 파싱, `pendingDeepLinkRoute` state 갱신
   3. `AppNavHost` 의 `LaunchedEffect(pendingDeepLinkRoute)` 가 `navController.navigate("hidden")` 호출
   4. `HiddenNotificationsScreen` 가 기본 "보관 중" 탭에서 ARCHIVED 리스트 렌더링 (→ [hidden-inbox](hidden-inbox.md))
   5. `autoCancel` 로 요약 알림 자동 dismiss
8. 사용자가 Hidden 카드 탭 → `Routes.Detail` 로 이동. Detail 에서 ARCHIVED 인 알림에는 "조용히 보관 중" 섹션과 **"처리 완료로 표시"** 버튼이 노출된다.
9. "처리 완료로 표시" 탭 → `NotificationRepository.markSilentProcessed(id)` 가 해당 row 의 `silentMode` 를 `ARCHIVED → PROCESSED` 로 upsert. `reasonTags` 에 `사용자 처리` 가 추가된다. (이미 PROCESSED 거나 SILENT 가 아니면 no-op false 반환.) Tray 원본 cancel 은 이 지점에서 배선되는 것이 목표지만, 현재 Detail 경로는 DB flip 만 수행하고 tray cancel 호출은 후속 listener-service 배선에 달려 있다 (Known gap 참조).
10. Repository 업데이트가 `observeAll` 로 흘러가면서 Hidden 화면의 탭별 리스트와 `silentSummaryJob` 의 ARCHIVED count 가 동시에 갱신된다.

## Exit state

- 시스템 tray:
  - ARCHIVED 알림: 원본이 남아 있음 + 요약 1건 (사용자가 요약을 swipe dismiss 하지 않았다면).
  - PROCESSED / legacy null 알림: tray 에 원본 없음.
- Hidden 화면:
  - "보관 중" 탭: ARCHIVED 카드만 표시, count = 요약의 N.
  - "처리됨" 탭: PROCESSED + legacy null silentMode row 표시 (마이그레이션 결정).
- 재분류·전이로 ARCHIVED count 가 0 이 되면 `silentSummaryJob` 이 `cancel()` 로 요약 제거.

## Out of scope

- Protected 알림 (→ [protected-source-notifications](protected-source-notifications.md) — 그쪽이 먼저 평가되어 cancel 건너뜀)
- Digest 원본 교체 (→ digest-suppression)
- Priority 원본 유지 (→ [priority-inbox](priority-inbox.md))
- 시간 기반 자동 아카이브/삭제 (plan 의 Non-goals)
- Swipe-dismiss 를 자동 "처리됨" 으로 해석 (plan Open question — 현재 미배선)

## Code pointers

- `domain/model/SilentMode` — ARCHIVED / PROCESSED enum
- `domain/model/NotificationUiModel#silentMode` — UI 모델 필드 (nullable)
- `notification/SourceNotificationRoutingPolicy` — SILENT × silentMode 분기
- `notification/SmartNotiNotificationListenerService#onListenerConnected` — `silentSummaryJob` 등록
- `notification/SilentHiddenSummaryNotifier` — 채널 / 빌더 / PendingIntent
- `notification/SilentArchivedSummaryCount` — `countSilentArchivedForSummary` (요약 count)
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

- **Capture 경로의 ARCHIVED 기본값 미배선** — `SmartNotiNotificationListenerService` 가 라우팅 정책을 호출할 때 `silentMode` 를 전달하지 않아, 새로 분류된 SILENT 알림은 여전히 legacy cancel 경로를 탄다. 그 결과 신규 SILENT 는 "처리됨" 탭(null → PROCESSED 매핑)에 쌓이고 "보관 중" 탭은 현재 경로로는 채워지지 않는다. 라우팅/전이 인프라 (Task 1–5) 는 shipped, 실제 capture 배선은 `silent-archive-vs-process-split` plan 의 후속 Task 로 예정.
- **Detail 전이의 tray cancel 부재** — `markSilentProcessed(id)` 는 DB row 만 flip 하고 tray 원본 cancel 을 호출하지 않는다. ARCHIVED 배선이 들어온 뒤 Detail 전이에서 `NotificationListenerService#cancelNotification` 을 연계해야 사용자 기대 "처리 완료 = tray 에서 사라짐" 과 일치한다.
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
