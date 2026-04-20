---
id: home-overview
title: 홈 개요 (요약 + 인사이트)
status: shipped
owner: @wooilkim
last-verified: 2026-04-21
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
3. `StatPill` — `즉시 N / Digest N / 조용히 N` 컬러칩 (분류 카운트 live).
4. `HomeNotificationAccessCard` — `HomeNotificationAccessSummaryBuilder` 결과로 리스너 연결 상태 + 최근 캡처 현황.
5. `QuickActionCard` × 2 — "중요 알림 지금 봐야 할 알림 N개" / "정리함 묶인 알림 N개" ("열기" 탭 시 각 탭으로 이동).
6. `QuickStartAppliedCard` — 온보딩 quick-start 프리셋 적용 결과 요약.
7. `InsightCard` — `HomeNotificationInsightsBuilder` 가 반환하는 "가장 많은 앱/이유 + 점유율" 정보. 칩 탭 시 `Routes.Insight.createForApp` 또는 `createForReason` 으로 네비.
8. `TimelineCard` — `HomeNotificationTimelineBuilder` 결과 (bucket 별 바차트, `HomeTimelineBarChartModelBuilder` 로 렌더 모델 변환).
9. 최근 알림 리스트 — 일반 `NotificationCard` 로 최상단 몇 건.
10. 카드/리스트 탭 → Detail 또는 Priority/Digest/Insight 로 네비.

## Exit state

- 사용자는 한 스크롤로 앱의 현재 상태를 파악.
- 카드/칩 탭을 통해 다음 journey (Priority / Digest / Insight / Detail) 로 자연스럽게 이동.

## Out of scope

- 상세 분류 로직 (→ [notification-capture-classify](notification-capture-classify.md))
- 각 탭의 전체 리스트 (→ [priority-inbox](priority-inbox.md), [digest-inbox](digest-inbox.md) 등)
- 설정 변경 (→ Settings 화면, 미문서화)

## Code pointers

- `ui/screens/home/HomeScreen`
- `domain/usecase/HomeNotificationInsightsBuilder`
- `domain/usecase/HomeNotificationAccessSummaryBuilder`
- `domain/usecase/HomeNotificationTimelineBuilder`
- `domain/usecase/HomeQuickStartAppliedSummaryBuilder`
- `domain/usecase/HomeReasonBreakdownChartModelBuilder`
- `domain/usecase/HomeTimelineBarChartModelBuilder`
- `navigation/Routes#Home`, `navigation/BottomNavItem` ("홈" 라벨)

## Tests

- 각 builder 별 unit test (예: `HomeNotificationInsightsBuilderTest`, `HomeNotificationTimelineBuilderTest` 등)

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
