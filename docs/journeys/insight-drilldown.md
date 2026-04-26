---
id: insight-drilldown
title: 인사이트 드릴다운
status: shipped
owner: @wooilkim
last-verified: 2026-04-26
---

## Goal

Home 의 "가장 많은 앱/이유" 칩이나 Settings 의 suppression 인사이트에서 탭했을 때, 해당 필터(앱 또는 이유)로 좁혀진 알림 리스트를 이유별 분포 차트와 함께 보여줘 원인을 파고들 수 있게 한다.

## Preconditions

- 온보딩 완료
- 드릴다운 진입 경로(Home 칩, Settings 칩 등)가 렌더된 상태

## Trigger

- `navController.navigate(Routes.Insight.createForApp(appName, range?, source?))` — 앱 기준
- `navController.navigate(Routes.Insight.createForReason(reasonTag, range?, source?))` — 이유 기준
- range/source 기본값은 `recent_24_hours` / `general`

## Observable steps

1. `InsightDrillDownScreen` 이 경로 인자(filterType / filterValue / range / source) 를 파싱.
2. `NotificationRepository.observeAll()` + `SettingsRepository.observeSettings()` 를 collect.
3. `InsightDrillDownBuilder.build(...)` 가 현재 설정(persistent 숨김 등)과 range(3h / 24h / 전체)에 따라 DIGEST + SILENT 알림을 필터링한 결과 반환.
4. 상단에 뒤로가기 + `ScreenHeader` (eyebrow "인사이트", title 은 `InsightDrillDownCopyBuilder` 가 필터별 문구 생성).
5. `ContextBadge` — `InsightContextBadgeModelBuilder` 결과 (GENERAL / SUPPRESSION 톤 색상).
6. 범위 칩 (`FilterChip`) — 최근 3시간 / 최근 24시간 / 전체 선택.
7. 이유별 분포 차트 (`InsightDrillDownReasonBreakdownChartModelBuilder`).
8. 이유 네비게이션 리스트 (`InsightDrillDownReasonNavigationModelBuilder`) — 다른 이유로 필터를 바꿔 이동.
9. 필터링된 알림 카드 리스트 — `NotificationCard` 탭 → Detail.
10. 비어있으면 `EmptyState`.

## Exit state

- 사용자는 특정 앱/이유의 알림 양상을 좁은 리스트로 확인 가능.
- 다른 이유로 재드릴다운 또는 Detail 로 이동 가능.

## Out of scope

- Home 칩 자체의 생성 (→ [home-overview](home-overview.md))
- Settings 인사이트 (Settings 화면 문서화 미완)
- PRIORITY 알림 드릴다운 (현재는 DIGEST + SILENT 만 대상)

## Code pointers

- `ui/screens/detail/InsightDrillDownScreen`
- `domain/usecase/InsightDrillDownBuilder` — 필터링
- `domain/usecase/InsightDrillDownSummaryBuilder`
- `domain/usecase/InsightDrillDownCopyBuilder`
- `domain/usecase/InsightContextBadgeModelBuilder`
- `domain/usecase/InsightDrillDownReasonBreakdownChartModelBuilder`
- `domain/usecase/InsightDrillDownReasonNavigationModelBuilder`
- `navigation/Routes#Insight`

## Tests

- `InsightDrillDownBuilderTest` — app/reason 필터링, range 파싱, 시각 윈도우 계산

## Verification recipe

```bash
# 1. 특정 앱 알림을 여러 건 게시 → 홈에 인사이트가 보이게 만듦
for i in 1 2 3 4 5; do
  adb shell cmd notification post -S bigtext -t "Coupang" "Deal$i" "오늘의 딜 $i"
done

# 2. 앱 실행 → 홈 인사이트에서 "Coupang" 칩 탭
adb shell am start -n com.smartnoti.app/.MainActivity

# 3. InsightDrillDown 화면에 Coupang 의 알림만 나오는지, range 칩이 동작하는지 확인
```

## Known gaps

- PRIORITY 분류 알림은 드릴다운 대상이 아님.
- range 선택이 URL 인자에 반영되어 back-stack 복원 시 초기값으로 돌아감. → plan: `docs/plans/2026-04-26-insight-drilldown-range-state-survival.md`

## Change log

- 2026-04-20: 초기 인벤토리 문서화
- 2026-04-22: v1 loop tick re-verify on emulator-5554 (PASS). Coupang × 5 + Shell duplicates → Home 의 `반복 알림 · N건` 칩 탭 → 반복 알림 drilldown (Digest 4/Silent 0, 24h), Shell 인사이트 카드 탭 → 앱 drilldown (Digest 9/Silent 11, 24h → Digest 6/Silent 7 on 3h range 전환). Eyebrow/title/ContextBadge/range chips/reason navigation/필터 리스트 전부 문서대로 렌더. Pre-Phase-C APK (lastUpdateTime=15:47 KST) 지만 insight drilldown 화면은 Phase A/B/C 변경과 무관해 contract 그대로 유효.
- 2026-04-24: v1 loop tick re-verify on emulator-5554 (PASS). Coupang × 5 게시 후 Home 의 SmartNoti 인사이트 카드 (`Shell 알림 ...`) 탭 → InsightDrillDown for Shell, eyebrow `인사이트` + title `Shell 인사이트` + ContextBadge (`일반 인사이트`) + range chips (`최근 3시간`/`최근 24시간`/`전체`) + 24h Digest 5/Silent 9 + reason 차트 (`이 앱에서 가장 많이 보인 이유는 '조용한 시간'`) + reason navigation (조용한 시간 8건 / 반복 알림 4건 / 사용자 규칙 1건) + 필터 카드 리스트 모두 문서대로. `전체` 칩 전환 시 Digest 7/Silent 31 로 카운트 갱신 확인.
- 2026-04-26: v1 loop tick re-verify on emulator-5554 (PASS). Coupang × 5 게시 후 Home 탭 → SmartNoti 인사이트 카드 (`Shell 알림 32개...`) 탭 → InsightDrillDown for Shell, eyebrow `인사이트` + title `Shell 인사이트` + ContextBadge (`일반 인사이트`) + range chips + 24h Digest 14/Silent 12 + reason 차트 (`이 앱에서 가장 많이 보인 이유는 '반복 알림'`) + reason navigation (반복 알림 8건 / 사용자 규칙 4건 / 프로모션 알림 4건) + 필터 카드 리스트 전부 문서대로 렌더. `전체` 칩 전환 시 Digest 17/Silent 15 로 카운트 갱신 확인.
