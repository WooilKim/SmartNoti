---
id: home-overview
title: 홈 개요 (요약 + 인사이트)
status: shipped
owner: @wooilkim
last-verified: 2026-04-22
---

## Goal

앱을 처음 열었을 때 "SmartNoti 가 지금 뭘 하고 있나" 를 한 화면에 요약한다. 분류 건수, 실제 알림 연결 상태, 빠른 시작 추천 적용 여부, 주요 인사이트, 최근 알림까지 한 번의 스크롤로 훑을 수 있게 한다.

## Preconditions

- 온보딩 완료 (`observeOnboardingCompleted() == true`)

## Trigger

앱 실행 직후 기본 진입 화면, 또는 사용자가 하단 네비의 "홈" 탭 탭.

## Observable steps

1. `HomeScreen` 이 repository/settings/RulesRepository 구독 시작.
2. 상단 `ScreenHeader` — "SmartNoti" + "중요한 알림만 먼저 보여드리고 있어요".
3. `StatPill` — `즉시 N / Digest N / 조용히 N` 컬러칩 (분류 카운트 live). IGNORE 는 기본 뷰에서 제외되므로 StatPill 및 아래의 최근 알림 리스트, QuickActionCard, Insight/Timeline 집계 어느 지점에도 포함되지 않음 — 오직 Settings 의 opt-in 토글을 켠 뒤 [ignored-archive](ignored-archive.md) 화면에서만 노출 (단, weekly/daily insights 의 "조용히 정리" 요약에는 별도 `ignoredCount` 스트림으로 집계된다).
4. `HomePassthroughReviewCard` — PRIORITY 카운트를 받아 "SmartNoti 가 건드리지 않은 알림 N건" + "이 판단이 맞는지 검토하고 필요하면 규칙으로 만들 수 있어요" + "검토하기" 액션 라벨로 렌더. 카운트 0 일 때는 "검토 대기 알림 없음" 의 empty-state 카피로 전환. 카드 전체를 탭하면 `onPriorityClick` → `Routes.Priority.route` 로 이동 (검토 화면, → [priority-inbox](priority-inbox.md)). Rules UX v2 Phase A 이후 Priority 가 BottomNav 에서 제거되면서, 이 카드가 검토 화면으로 진입하는 유일한 UI 엔트리 포인트.
5. `HomeNotificationAccessCard` — `HomeNotificationAccessSummaryBuilder` 결과로 리스너 연결 상태 + 최근 캡처 현황.
6. `QuickActionCard` × 2 — "중요 알림 지금 봐야 할 알림 N개" / "정리함 묶인 알림 N개" ("열기" 탭 시 각 탭으로 이동). 중요 알림 카드 역시 검토 화면으로 연결됨 (passthrough review card 와 동일 route).
7. `QuickStartAppliedCard` — 온보딩 quick-start 프리셋 적용 결과 요약.
8. `InsightCard` — `HomeNotificationInsightsBuilder` 가 반환하는 "가장 많은 앱/이유 + 점유율" 정보. 칩 탭 시 `Routes.Insight.createForApp` 또는 `createForReason` 으로 네비.
9. `TimelineCard` — `HomeNotificationTimelineBuilder` 결과 (bucket 별 바차트, `HomeTimelineBarChartModelBuilder` 로 렌더 모델 변환).
10. 최근 알림 리스트 — 일반 `NotificationCard` 로 최상단 몇 건.
11. 카드/리스트 탭 → Detail 또는 Priority(검토)/Digest/Insight 로 네비.

## Exit state

- 사용자는 한 스크롤로 앱의 현재 상태를 파악.
- 카드/칩 탭을 통해 다음 journey (Priority / Digest / Insight / Detail) 로 자연스럽게 이동.

## Out of scope

- 상세 분류 로직 (→ [notification-capture-classify](notification-capture-classify.md))
- 각 탭의 전체 리스트 (→ [priority-inbox](priority-inbox.md), [digest-inbox](digest-inbox.md) 등)
- 설정 변경 (→ Settings 화면, 미문서화)

## Code pointers

- `ui/screens/home/HomeScreen`
- `ui/components/HomePassthroughReviewCard` — passthrough 검토 카드 (count + empty/active 상태)
- `domain/usecase/HomeNotificationInsightsBuilder`
- `domain/usecase/HomeNotificationAccessSummaryBuilder`
- `domain/usecase/HomeNotificationTimelineBuilder`
- `domain/usecase/HomeQuickStartAppliedSummaryBuilder`
- `domain/usecase/HomeReasonBreakdownChartModelBuilder`
- `domain/usecase/HomeTimelineBarChartModelBuilder`
- `navigation/Routes#Home`, `navigation/BottomNavItem` ("홈" 라벨 — Priority 탭은 Phase A 이후 제거됨)

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

- 스크롤이 길어지면 위치 복원 UX 미검증.
- 인사이트가 0건일 때의 카드 표시 규칙이 현재 모든 builder 에 균일하지 않음.

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-21: Rules UX v2 Phase A shipped (PR #140/#141/#142/#143). Home 에 `HomePassthroughReviewCard` 가 StatPill 아래에 마운트되어 PRIORITY count 를 표시하고 탭 시 검토 화면으로 이동. Priority 는 더 이상 BottomNav 탭이 아니며, 이 카드가 검토 화면 유일한 UI 엔트리. Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase A
- 2026-04-21: IGNORE (무시) 4번째 분류 tier 가 Home 의 기본 뷰에서 제외되도록 배선 — StatPill / 최근 알림 리스트 / QuickActionCard / Insight/Timeline 집계 모두 `observePriority` · `observeDigest` · `observeAllFiltered` 를 사용하므로 `status=IGNORE` row 는 자동 필터 아웃. `SuppressionInsightsBuilder` / `InsightDrillDownSummaryBuilder` 는 `ignoredCount` 를 별도 스트림으로 계산해 DIGEST/SILENT 와 분리 집계 (#185 `9a5b4b9`, plan `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6). IGNORE row 는 [ignored-archive](ignored-archive.md) 에서만 노출. `last-verified` 는 ADB 검증 전까지 bump 하지 않음.
