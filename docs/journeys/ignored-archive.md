---
id: ignored-archive
title: 무시됨 아카이브 (opt-in IGNORE 뷰)
status: shipped
owner: @wooilkim
last-verified: 2026-04-22
---

## Goal

`Category.action == IGNORE` 에 속한 Rule 매치로 즉시 정리된 알림을 **기본 뷰에서는 전부 숨기고**, Settings 의 opt-in 토글이 켜졌을 때만 아카이브 화면으로 별도 노출한다. "보이지 않음" 과 "없었던 것" 을 분리해, 파괴적인 IGNORE 결정에도 감사·복구 경로를 남긴다.

> 2026-04-22 plan `categories-split-rules-actions` 이후 IGNORE 는 Rule 의 속성이 아니라 **Category 의 action** 이다. 아카이브에 노출되는 조건은 여전히 `status == NotificationStatusUi.IGNORE` 이지만, 그 status 를 만드는 경로는 "매치된 Rule → 소유 Category → `CategoryConflictResolver` winner → `Category.action.toDecision() == NotificationDecision.IGNORE`" 로 바뀌었다 (→ [notification-capture-classify](notification-capture-classify.md)).

## Preconditions

- 온보딩 완료
- 최소 하나 이상의 `Category.action == IGNORE` 분류에 묶인 Rule 이 존재하거나, Detail 의 "무시" 피드백 버튼으로 IGNORE 를 적용한 이력이 있어야 아카이브가 비어있지 않음
- Settings 의 `showIgnoredArchive` 토글이 ON (default OFF). 2026-04-22 픽스 (#199) 이후 route 는 상시 등록되고 토글은 Settings 의 "무시됨 아카이브 열기" 버튼 가시성만 gate 함 (→ Known gaps)

## Trigger

- Settings 탭 → "무시됨 / 무시된 알림 보기" 카드 → `showIgnoredArchive` 토글 ON → 같은 카드에 나타나는 `무시됨 아카이브 열기` OutlinedButton 탭
- 토글 OFF 인 동안에는 아카이브 버튼이 렌더되지 않아 진입 불가. `Routes.IgnoredArchive` composable 자체는 2026-04-22 fix (#199) 이후 상시 등록되지만, deep-link 타겟이 없으므로 토글 OFF 상태에서는 도달 경로가 없음

## Observable steps

1. `SettingsScreen` 의 `IgnoredArchiveSettingsCard` 에서 토글 ON → `SettingsRepository.setShowIgnoredArchive(true)` 가 DataStore 에 persist.
2. `AppNavHost` 가 `observeSettings().showIgnoredArchive` 를 `produceState` 로 collect → `true` 로 재구성되면 `Routes.IgnoredArchive.route` 가 navigation graph 에 composable 로 추가됨.
3. 같은 Settings 카드 안 `무시됨 아카이브 열기` 버튼 탭 → `navController.navigate(Routes.IgnoredArchive.route)` (`launchSingleTop = true`) 로 진입.
4. `IgnoredArchiveScreen` 마운트, `NotificationRepository.observeIgnoredArchive()` 구독. Flow 는 `observeAll()` 을 `status == NotificationStatusUi.IGNORE` 로 필터링하고 `postedAtMillis` 기준 내림차순 정렬한 리스트를 emit.
5. 빈 상태:
   - `ScreenHeader` — eyebrow "아카이브" / title "무시됨" / subtitle "IGNORE 규칙이 매치된 알림은 여기에 쌓여요. 기본 뷰에는 나타나지 않아요." (copy 는 "IGNORE 규칙" 이라고 적혀 있으나 실제 구현상 "`Category.action = IGNORE` 에 묶인 Rule" 을 의미 — 후속 copy polish 대상)
   - `EmptyState` — "무시된 알림이 없어요 / IGNORE 액션 규칙을 만들면 여기에 쌓이기 시작해요."
6. 알림이 있을 때:
   - `ScreenHeader` subtitle 이 "SmartNoti 가 IGNORE 규칙으로 즉시 정리한 알림이에요. 원본 알림센터에서도 사라진 상태예요." 로 전환.
   - `SmartSurfaceCard` — "보관 중 N건" + "최신 순으로 정렬돼 있어요. 탭하면 상세 화면에서 어떤 규칙이 걸렸는지 확인할 수 있어요."
   - LazyColumn 이 `NotificationCard` 로 개별 row 렌더 (그룹 collapse / bulk action 없음 — 의도적으로 plain list).
7. 카드 탭 → `navController.navigate(Routes.Detail.create(id))` (→ [notification-detail](notification-detail.md)). Detail 에서는 `StatusBadge` 가 IGNORE 케이스로 중립 회색 tone 렌더, 피드백 버튼 "무시" 는 이미 IGNORE 인 row 에서도 눌림 (결과적으로 noop upsert) — Detail 계약의 일부.

## Exit state

- 토글이 ON 인 동안 아카이브 화면이 최신 IGNORE row 를 실시간 표시.
- 토글을 OFF 로 되돌리면 `AppNavHost` 의 `produceState` 가 `false` 를 emit → 다음 composition 에서 `Routes.IgnoredArchive` composable 이 제거되어 진입 불가. 진입 중이었다면 뒤로가기 이후 재진입 불가.
- 기본 뷰 (Home / Priority / Digest / Hidden) 는 토글 상태와 무관하게 IGNORE 를 항상 제외.

## Out of scope

- 개별 row 를 아카이브 안에서 재분류하거나 복구하는 UI (현재 Detail 의 기존 피드백 버튼 3종 + "무시" 만 지원; bulk action 미구현)
- IGNORE row 의 retention / 자동 삭제 정책 (현재 무제한 보관)
- 아카이브 그룹핑 / 필터 / 검색 (plain list 만)
- IGNORE 룰 편집 (→ [rules-management](rules-management.md))

## Code pointers

- `ui/screens/ignored/IgnoredArchiveScreen` — 화면 본체 (empty state + 리스트 두 갈래)
- `data/local/NotificationRepository#observeIgnoredArchive` — IGNORE row 만 추출, 최신순 정렬
- `data/settings/SettingsRepository#setShowIgnoredArchive`, `SmartNotiSettings.showIgnoredArchive` — opt-in 플래그
- `ui/screens/settings/SettingsScreen#IgnoredArchiveSettingsCard` — 토글 + 진입 버튼
- `navigation/Routes#IgnoredArchive` — 조건부 route
- `navigation/AppNavHost` — `produceState { showIgnoredArchive }` + `if (showIgnoredArchive) { composable(...) }` 로 graph 등록 gate
- `domain/model/NotificationStatusUi.IGNORE` — 필터 키
- `ui/components/StatusBadge` — IGNORE 케이스 중립 회색 렌더 (Detail 경유 시)

## Tests

- `NotificationRepositoryTest` — `observeIgnoredArchive` 가 IGNORE 만 반환하고 기본 뷰 (`observePriority/Digest/Silent`, `toHiddenGroups`) 는 IGNORE 를 제외하는지
- `SuppressionInsightsBuilderTest` / `InsightDrillDownSummaryBuilderTest` — `ignoredCount` 가 DIGEST/SILENT 와 분리 집계되는지

## Verification recipe

```bash
# 1. Rules 탭에서 IGNORE 룰 생성 — 예: 키워드 "광고" + 액션 "무시 (즉시 삭제)"
#    (→ rules-management recipe)

# 2. 해당 키워드 포함 알림 게시
adb shell cmd notification post -S bigtext -t "프로모션" IgnoreTest "광고 테스트 알림"

# 3. tray 에서 즉시 사라지고 Home / Priority / Digest / Hidden 어디에도 나타나지 않는지 확인
adb shell am start -n com.smartnoti.app/.MainActivity
# Home StatPill 3값 (즉시/Digest/조용히) 에 포함되지 않음을 관측

# 4. Settings → "무시된 알림 아카이브 표시" 토글 ON → "무시됨 아카이브 열기" 탭
#    → 방금 그 row 가 최신 첫 줄로 보이는지 확인

# 5. 토글 OFF 후 뒤로가기 → 다시 Settings 진입 → 아카이브 버튼 사라짐 확인
```

## Known gaps

- 화면 단독 Compose UI 테스트 부재 (repository + settings 단위 테스트로만 커버).
- 아카이브 안에서 "이 row 되살리기" / "모두 삭제" / bulk 재분류 미구현 — 복구가 필요하면 Detail 로 가서 "중요로 고정" / "Digest로 보내기" / "조용히 처리" 중 하나를 눌러야 함.
- IGNORE row 의 retention 정책 미구현 — 물리 삭제 / 오래된 row 자동 정리는 후속. Plan `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` out-of-scope.
- Weekly insights 에서 IGNORE 카운트가 DIGEST / SILENT 와 별도 스트림으로 노출되는지 (Insights builder contract) 는 test-level 로만 검증되고, 화면 레이블이 실제로 "삭제 N건" 같은 copy 로 분리되어 보이는지는 아직 미검증 — Task 8 verification 에서 확인 필요.
- Preconditions / Trigger / Observable steps / Exit state / Code pointers 가 아직 "route 가 조건부로 등록됨" 을 전제로 기술되어 있으나, 2026-04-22 픽스 이후 route 는 상시 등록되고 토글은 Settings 버튼 가시성만 gate 함. 다음 verification 패스에서 문구 정비 필요 (별도 journey-tester PR).
- (resolved 2026-04-22) toggle-ON 직후 즉시 버튼 탭 시 `IllegalArgumentException` 크래시 — PR #199 Option A 로 해소. 단, 초고속 OFF→ON→탭 시퀀스에서 recomposition 타이밍에 따라 버튼 탭이 드물게 noop (onClick lambda 가 null→valid 전환 사이에 탭 소실) 되는 경우는 관측됨. 크래시는 아니고 사용자는 재탭으로 정상 진입. 완전 해소는 onClick 을 항상 non-null 로 두고 내부에서 토글 상태 검사하도록 바꾸는 별도 과제로 가능.

## Change log

- 2026-04-21: 신규 문서화 — plan `docs/plans/2026-04-21-ignore-tier-fourth-decision.md` Task 6 (#185 `9a5b4b9`) 구현 결과물. Settings 토글 + `AppNavHost` 조건부 route + `IgnoredArchiveScreen` + `NotificationRepository.observeIgnoredArchive()` 가 함께 shipped. Detail "무시" 피드백 버튼 (#187 `57df6ac`, Task 6a) 과 직결 — IGNORE 결정의 두 진입 경로 (룰 직접 생성 / Detail 피드백) 가 모두 이 화면으로 수렴. `last-verified` 는 실제 recipe 실행 전까지 비워둠 (per `.claude/rules/docs-sync.md`).
- 2026-04-21: 첫 verification sweep 실행 — 키워드 IGNORE 룰 생성 → 매칭 알림 포스팅 → tray 즉시 제거, Home StatPill 불포함 (28 captured vs 27 classified), Settings 토글 ON→버튼 노출→아카이브 진입→`보관 중 1건` + NotificationCard 렌더 + 카드 탭 시 Detail `무시됨` 뱃지 정상 관측. PASS. 단, Known gaps 에 toggle-ON 직후 즉시 탭 시 한 차례 크래시 관측 기록 (race-condition, 재현 안정성 낮음).
- 2026-04-22: toggle OFF→ON 직후 same-composition 탭 race 해소 — plan `docs/plans/2026-04-22-ignored-archive-first-tap-nav-race.md` (PR #199). Option A 적용: `AppNavHost` 가 `Routes.IgnoredArchive` 를 상시 등록하고, 토글은 Settings 버튼 가시성만 gate. `IgnoredArchiveNavGate.isRouteRegistered` 이 unconditional `true` 로 전환되어 "button visible ⇒ route registered" 불변식이 단일 읽기 지점으로 고정됨.
- 2026-04-22: post-fix verification — APK rebuild (`lastUpdateTime=2026-04-22 03:46:30`) 후 OFF→ON→즉시 버튼 탭 시퀀스 3회 반복. 크래시 0건, `IgnoredArchiveScreen` 정상 마운트 확인 (`보관 중 1건` 렌더). 직전 sweep 에서 보고된 `IllegalArgumentException: Navigation destination ... ignored_archive cannot be found` 재현 불가. `last-verified` 를 2026-04-22 로 갱신.
- 2026-04-22: **Rule/Category 분리 아키텍처** 반영 — Goal/Preconditions 에서 IGNORE 를 "Rule 의 속성" 대신 "`Category.action = IGNORE` 의 결과" 로 재정의. `observeIgnoredArchive` 의 필터 자체 (`status == NotificationStatusUi.IGNORE`) 는 불변. classifier cascade 가 Category.action 기반으로 바뀐 뒤에도 IGNORE status 생성 경로는 논리적으로 동일 (Rule 매치 → winning Category 의 action 적용). Plan: `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 (#236), P2 (#239). `last-verified` 는 변경 없음.
