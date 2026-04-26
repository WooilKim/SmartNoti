---
id: home-overview
title: 홈 개요 (요약 + 인사이트)
status: shipped
owner: @wooilkim
last-verified: 2026-04-26
---

## Goal

앱을 처음 열었을 때 "SmartNoti 가 지금 뭘 하고 있나" 를 한 화면에 요약한다. 분류 건수, 실제 알림 연결 상태, 빠른 시작 추천 적용 여부, 주요 인사이트, 최근 알림까지 한 번의 스크롤로 훑을 수 있게 한다.

## Preconditions

- 온보딩 완료 (`observeOnboardingCompleted() == true`)

## Trigger

앱 실행 직후 기본 진입 화면, 또는 사용자가 하단 네비의 "홈" 탭 탭.

## Observable steps

1. `HomeScreen` 이 repository/settings/RulesRepository/**CategoriesRepository** + `UncategorizedAppsDetector` 구독 시작.
2. **(최상단)** `HomeUncategorizedAppsPromptCard` — `UncategorizedAppsDetector.detect(...)` 가 `Prompt` 를 반환할 때만 mount. "새 앱 N개가 알림을 보내고 있어요" + "분류 만들기" / "나중에" 액션. "분류 만들기" 탭 → `navigateToTopLevel(Routes.Categories.route)` (→ [home-uncategorized-prompt](home-uncategorized-prompt.md)). "나중에" 탭 → 24시간 snooze persist → 카드 즉시 비표시.
3. `ScreenHeader` — "SmartNoti" + "중요한 알림만 먼저 보여드리고 있어요".
4. `StatPill` (`primaryContainer` hero 카드 내부) — 타이틀 "오늘 알림 N개 중 중요한 M개를 먼저 전달했어요" + `즉시 N / Digest N / 조용히 N` 컬러칩 (분류 카운트 live). IGNORE 는 기본 뷰에서 제외되므로 StatPill 및 아래 최근 알림 리스트, Insight/Timeline 집계 어느 지점에도 포함되지 않음 — 오직 Settings 의 opt-in 토글을 켠 뒤 [ignored-archive](ignored-archive.md) 화면에서만 노출 (단, weekly/daily insights 의 "조용히 정리" 요약에는 별도 `ignoredCount` 스트림으로 집계된다).
5. `HomePassthroughReviewCard` — `priorityCount > 0` 일 때만 mount. PRIORITY 카운트 + "검토하기" 라벨. 카드 탭 → `onPriorityClick` → `Routes.Priority.route` (→ [priority-inbox](priority-inbox.md)). PRIORITY 가 0 이면 카드 자체가 비표시 (declutter per Task 10).
6. Access card — connected 면 `HomeNotificationAccessInlineRow` (1-row), disconnected / error 면 full `HomeNotificationAccessCard`. Task 10 declutter 로 Home 상시 full-card 를 connected 시 inline 으로 강등.
7. `QuickStartAppliedCard` — 온보딩 quick-start 프리셋 적용 결과 요약 (7-day TTL + tap-to-ack gate: `HomeQuickStartAppliedVisibility.shouldShow(...)` 로 제어). 카드 탭 시 `SettingsRepository.setQuickStartAppliedCardAcknowledgedAtMillis(...)` 저장 후 "고급 규칙 편집" 경로로 이동.
8. `InsightCard` — `insights.filteredCount > 0` 일 때만 mount (Task 10 declutter). "가장 많은 앱/이유 + 점유율" 정보. 칩 탭 시 `Routes.Insight.createForApp` 또는 `createForReason` 으로 네비.
9. `TimelineCard` — `HomeNotificationTimelineBuilder` 결과 (bucket 별 바차트, `HomeTimelineBarChartModelBuilder` 로 렌더 모델 변환). 비어 있으면 mount 생략.
10. 최근 알림 리스트 ("방금 정리된 알림") — `HomeRecentNotificationsTruncation` 으로 최신 5건만 카드로 렌더 (`DEFAULT_CAPACITY = 5`). 그 이상이 있으면 마지막에 `HomeRecentMoreRow` ("전체 N건 보기 →") 가 mount, 탭 시 `Routes.Inbox` 로 진입해 기본 Digest sub-tab 에서 사용자가 outer 탭으로 ARCHIVED/PROCESSED 를 다시 고를 수 있음.
11. 카드/리스트 탭 → Detail 또는 검토 화면 / 정리함 / Insight 로 네비. (기존 `QuickActionCard × 2` ("중요 알림" / "정리함") 는 Task 10 에서 제거 — BottomNav 의 `정리함` 탭이 이를 대신함.)

## Exit state

- 사용자는 한 스크롤로 앱의 현재 상태를 파악.
- 카드/칩 탭을 통해 다음 journey (Priority / Digest / Insight / Detail) 로 자연스럽게 이동.

## Out of scope

- 상세 분류 로직 (→ [notification-capture-classify](notification-capture-classify.md))
- 각 탭의 전체 리스트 (→ [priority-inbox](priority-inbox.md), [digest-inbox](digest-inbox.md) 등)
- 설정 변경 (→ Settings 화면, 미문서화)

## Code pointers

- `ui/screens/home/HomeScreen`
- `ui/screens/home/HomeRecentNotificationsTruncation` — pure-function (visible, hiddenCount) builder for the "방금 정리된 알림" cap + footer row
- `ui/screens/home/HomeUncategorizedAppsPromptCard` — 새 앱 분류 유도 카드
- `domain/usecase/UncategorizedAppsDetector` — 분류 유도 pure detector (→ [home-uncategorized-prompt](home-uncategorized-prompt.md))
- `ui/components/HomePassthroughReviewCard` — passthrough 검토 카드 (count + empty/active 상태)
- `ui/screens/home/HomeNotificationAccessInlineRow` — connected 시 strip
- `ui/screens/home/HomeNotificationAccessCard` — disconnected 시 full card
- `ui/screens/home/HomeQuickStartAppliedCard` + `HomeQuickStartAppliedVisibility` — TTL + ack gate
- `domain/usecase/HomeNotificationInsightsBuilder`
- `domain/usecase/HomeNotificationAccessSummaryBuilder`
- `domain/usecase/HomeNotificationTimelineBuilder`
- `domain/usecase/HomeQuickStartAppliedSummaryBuilder`
- `domain/usecase/HomeReasonBreakdownChartModelBuilder`
- `domain/usecase/HomeTimelineBarChartModelBuilder`
- `data/categories/CategoriesRepository` — 새 앱 감지의 coverage 소스
- `data/settings/SettingsRepository#observeUncategorizedPromptSnoozeUntilMillis` — 24h snooze
- `navigation/Routes#Home`, `navigation/BottomNavItem` (4-tab: 홈 / 정리함 / 분류 / 설정)

## Tests

- 각 builder 별 unit test (예: `HomeNotificationInsightsBuilderTest`, `HomeNotificationTimelineBuilderTest` 등)
- `HomePassthroughReviewCardStateTest` — passthrough 카드의 count→state 매핑 (empty vs active copy, 음수 clamp)

## Verification recipe

```bash
# 1. 알림 몇 건 게시해 다양한 분류가 생기게 함
adb shell cmd notification post -S bigtext -t "엄마" Mom "전화 좀"
adb shell cmd notification post -S bigtext -t "Coupang" Deal "오늘의 딜"
adb shell cmd notification post -S bigtext -t "광고" Promo "30% 할인"

# 2. 앱 실행 → 홈 탭에 StatPill 3건 카운트 반영, QuickActionCard 갯수 맞는지 확인
adb shell am start -n com.smartnoti.app/.MainActivity
```

## Known gaps

- 인사이트가 0건일 때의 카드 표시 규칙이 현재 모든 builder 에 균일하지 않음.
- (resolved 2026-04-22) "스크롤이 길어지면 위치 복원 UX 미검증" — `HomeRecentNotificationsTruncation` 의 5건 cap 으로 Home 의 최대 스크롤 길이 자체가 짧아져 시급성이 사라짐. 더 긴 리뷰는 정리함에서 수행.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Rules UX v2 Phase A shipped (PR #140/#141/#142/#143). Home 에 `HomePassthroughReviewCard` 가 StatPill 아래에 마운트되어 PRIORITY count 를 표시하고 탭 시 검토 화면으로 이동. Priority 는 더 이상 BottomNav 탭이 아니며, 이 카드가 검토 화면 유일한 UI 엔트리. Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase A
- 2026-04-21: IGNORE (무시) 4번째 분류 tier 가 Home 의 기본 뷰에서 제외되도록 배선 — StatPill / 최근 알림 리스트 / QuickActionCard / Insight/Timeline 집계 모두 `observePriority` · `observeDigest` · `observeAllFiltered` 를 사용하므로 `status=IGNORE` row 는 자동 필터 아웃. `SuppressionInsightsBuilder` / `InsightDrillDownSummaryBuilder` 는 `ignoredCount` 를 별도 스트림으로 계산해 DIGEST/SILENT 와 분리 집계 (#185 `9a5b4b9`, plan `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6). IGNORE row 는 [ignored-archive](ignored-archive.md) 에서만 노출. `last-verified` 는 ADB 검증 전까지 bump 하지 않음.
- 2026-04-22: Plan `inbox-denest-and-home-recent-truncate` (PR pending) shipped — "방금 정리된 알림" 이 `HomeRecentNotificationsTruncation` (DEFAULT_CAPACITY=5) 로 head 5건 + `HomeRecentMoreRow` ("전체 N건 보기 →") footer 로 truncate. footer 탭 시 `Routes.Inbox` 로 이동. Observable step 10 + Code pointers + Known gaps 갱신. `last-verified` 는 ADB smoke 로 검증된 구간만 (Home truncate + Inbox de-nest + standalone deep-link 회귀 없음) 동일 일자 유지.
- 2026-04-22: Plan `categories-split-rules-actions` Phase P3 Task 10 (#240) declutter 반영 — 최상단에 `HomeUncategorizedAppsPromptCard` 가 `UncategorizedAppsDetection.Prompt` 일 때만 mount (→ [home-uncategorized-prompt](home-uncategorized-prompt.md)). Access card 는 connected 시 `HomeNotificationAccessInlineRow` 1-row 로 축소 (disconnected 시에만 full card). 기존 `QuickActionCard × 2` ("중요 알림" / "정리함") 제거 — BottomNav 의 `정리함` 탭이 대체. `HomeQuickStartAppliedCard` 는 7일 TTL + tap-to-ack gate (`HomeQuickStartAppliedVisibility`) 추가. `HomePassthroughReviewCard` / `InsightCard` / `TimelineCard` 는 count 0 시 mount 생략. Observable steps / Code pointers 전면 갱신. `last-verified` 는 declutter 재검증 전까지 현 일자 유지.
- 2026-04-26: ADB end-to-end re-verification (emulator-5554, fresh APK `lastUpdateTime=2026-04-26 15:43:43`). Home 탭 진입 후 UI dump 결과 — Step 3 `ScreenHeader` "SmartNoti" + "중요한 알림만 먼저 보여드리고 있어요" 정확 렌더, Step 4 `StatPill` "오늘 알림 52개 중 중요한 10개를 먼저 전달했어요" + `즉시 10 / Digest 20 / 조용히 22` chip 카운트 live 반영, Step 5 `HomePassthroughReviewCard` (`priorityCount=10 > 0`) "검토 대기 10" + "검토하기" mount, Step 6 connected `HomeNotificationAccessInlineRow` "연결됨" + "실제 알림이 연결되어 있어요" 1-row 강등, Step 8 `InsightCard` 활성 (가장 많은 앱 = Shell, 주된 이유 = 반복 알림 + 3개 reason chip), Step 11 BottomNav 4-tab "홈 / 정리함 / 분류 / 설정". Step 2 (uncategorized prompt) 는 24h snooze persist 로 비표시 — 의도된 declutter. DRIFT 없음. `last-verified: 2026-04-25 → 2026-04-26`.
