---
id: ignored-archive
title: 무시됨 아카이브 (opt-in IGNORE 뷰)
status: shipped
owner: @wooilkim
last-verified: 2026-04-27
---

## Goal

`Category.action == IGNORE` 에 속한 Rule 매치로 즉시 정리된 알림을 **기본 뷰에서는 전부 숨기고**, Settings 의 opt-in 토글이 켜졌을 때만 아카이브 화면으로 별도 노출한다. "보이지 않음" 과 "없었던 것" 을 분리해, 파괴적인 IGNORE 결정에도 감사·복구 경로를 남긴다.

> 2026-04-22 plan `categories-split-rules-actions` 이후 IGNORE 는 Rule 의 속성이 아니라 **Category 의 action** 이다. 아카이브에 노출되는 조건은 여전히 `status == NotificationStatusUi.IGNORE` 이지만, 그 status 를 만드는 경로는 "매치된 Rule → 소유 Category → `CategoryConflictResolver` winner → `Category.action.toDecision() == NotificationDecision.IGNORE`" 로 바뀌었다 (→ [notification-capture-classify](notification-capture-classify.md)).

## Preconditions

- 온보딩 완료
- 최소 하나 이상의 `Category.action == IGNORE` 분류에 묶인 Rule 이 존재해야 아카이브가 비어있지 않음. 2026-04-22 plan `categories-runtime-wiring-fix` (#245) 이후 Detail 의 "무시" 버튼은 제거되어 피드백 경로에서 IGNORE 를 만들 수 없음 — IGNORE 를 원하면 Detail 의 "분류 변경" → "새 분류 만들기" 에서 Category action 을 IGNORE 로 선택하거나, 기존 IGNORE Category 에 할당해야 함 (→ [rules-feedback-loop](rules-feedback-loop.md)).
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
   - `SmartSurfaceCard` — "보관 중 N건" + "최신 순으로 정렬돼 있어요. 길게 누르면 여러 건을 한 번에 지울 수 있어요." + 헤더 두 OutlinedButton (`모두 PRIORITY 로 복구` / `모두 지우기`). multi-select 활성 시 두 버튼은 숨겨지고 [`IgnoredArchiveActionBar`](../../app/src/main/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveActionBar.kt) (카운트 + `모두 지우기` + `취소`) 가 헤더 카드 바로 아래에 inline 으로 마운트.
   - LazyColumn 이 `NotificationCard` 로 개별 row 렌더 (그룹 collapse 없음 — 의도적으로 plain list). 각 카드 우하단 `PRIORITY 로 복구` TextButton 이 multi-select 비활성 시 노출.
7. 카드 인터랙션:
   - 단일 탭 (multi-select 비활성) → `navController.navigate(Routes.Detail.create(id))` (→ [notification-detail](notification-detail.md)). Detail 에서는 `StatusBadge` 가 IGNORE 케이스로 중립 회색 tone 렌더. 재분류는 Detail 의 단일 "분류 변경" CTA 경유.
   - long-press → `IgnoredArchiveMultiSelectState.enterSelection(id)`. 선택 상태에서 다른 카드 탭은 `toggle(id)` 로 selection 가산/감산. 빈 set 이 되면 자동 비활성.
   - 우하단 `PRIORITY 로 복구` TextButton → `repository.updateNotification(...)` 단일 write (status=PRIORITY + reasonTags 에 `사용자 분류` dedup-append) + snackbar `"이 알림을 PRIORITY 로 복구했어요"`.
8. 헤더 bulk action:
   - `모두 PRIORITY 로 복구` → 즉시 `repository.restoreAllIgnoredToPriority()` + snackbar `"무시된 알림 N건을 PRIORITY 로 복구했어요"` (확인 다이얼로그 없음 — 비파괴).
   - `모두 지우기` → `AlertDialog` ("무시된 알림 N건을 모두 지울까요? / 되돌릴 수 없어요...") → 확인 시 `repository.deleteAllIgnored()` + snackbar `"무시된 알림 N건을 모두 지웠어요"`.
9. multi-select bulk action: `IgnoredArchiveActionBar` 의 `모두 지우기` → 동일 패턴 `AlertDialog` ("선택한 알림 N건을 지울까요?") → 확인 시 `repository.deleteIgnoredByIds(selected)` + snackbar `"선택한 알림 N건을 지웠어요"`. 성공 후 multi-select 자동 종료.

## Exit state

- 토글이 ON 인 동안 아카이브 화면이 최신 IGNORE row 를 실시간 표시.
- 토글을 OFF 로 되돌리면 `AppNavHost` 의 `produceState` 가 `false` 를 emit → 다음 composition 에서 `Routes.IgnoredArchive` composable 이 제거되어 진입 불가. 진입 중이었다면 뒤로가기 이후 재진입 불가.
- 기본 뷰 (Home / Priority / Digest / Hidden) 는 토글 상태와 무관하게 IGNORE 를 항상 제외.

## Out of scope

- Detail 단일 분류 변경 외 개별 row 액션 — multi-select bulk delete + 헤더 bulk (전체 복구 / 전체 삭제) + per-row PRIORITY 복구 세 액션 한정. 다른 status (SILENT/DIGEST) 로 복구하려면 여전히 Detail 의 "분류 변경" 을 거쳐야 함.
- IGNORE row 의 retention / 자동 삭제 정책 (현재 무제한 보관)
- 아카이브 그룹핑 / 필터 / 검색 (plain list 만)
- IGNORE 룰 편집 (→ [rules-management](rules-management.md))
- per-row "다른 Category 로 보내기" inline (Detail 경유 유지)
- 5초 undo snackbar (현재 destructive bulk 는 confirm dialog 로만 가드)

## Code pointers

- `ui/screens/ignored/IgnoredArchiveScreen` — 화면 본체 (empty state + 리스트 + 헤더 bulk + multi-select + per-row 복구)
- `ui/screens/ignored/IgnoredArchiveMultiSelectState` — pure-state holder (long-press 진입 / toggle / clear, mirror of `PriorityScreenMultiSelectState`)
- `ui/screens/ignored/IgnoredArchiveActionBar` — 멀티셀렉트 활성 시 헤더 카드 아래 inline 마운트되는 sticky bar (count + 모두 지우기 + 취소)
- `data/local/NotificationRepository#observeIgnoredArchive` — IGNORE row 만 추출, 최신순 정렬
- `data/local/NotificationRepository#restoreAllIgnoredToPriority` — 헤더 `모두 PRIORITY 로 복구` 가 호출하는 in-Kotlin transaction (status flip + `사용자 분류` reasonTag dedup-append)
- `data/local/NotificationRepository#deleteAllIgnored` / `deleteIgnoredByIds` — 헤더 `모두 지우기` / multi-select `모두 지우기` 의 단일 DAO DELETE 래퍼
- `data/local/NotificationDao#deleteAllIgnored` / `deleteIgnoredByIds` — Room queries (`status='IGNORE'` scope, byIds 는 추가로 `id IN (:ids)` 가드)
- `data/settings/SettingsRepository#setShowIgnoredArchive`, `SmartNotiSettings.showIgnoredArchive` — opt-in 플래그
- `ui/screens/settings/SettingsScreen#IgnoredArchiveSettingsCard` — 토글 + 진입 버튼
- `navigation/Routes#IgnoredArchive` — 조건부 route
- `navigation/AppNavHost` — `produceState { showIgnoredArchive }` + `if (showIgnoredArchive) { composable(...) }` 로 graph 등록 gate
- `domain/model/NotificationStatusUi.IGNORE` — 필터 키
- `ui/components/StatusBadge` — IGNORE 케이스 중립 회색 렌더 (Detail 경유 시)

## Tests

- `NotificationRepositoryTest` — `observeIgnoredArchive` 가 IGNORE 만 반환하고 기본 뷰 (`observePriority/Digest/Silent`, `toHiddenGroups`) 는 IGNORE 를 제외하는지
- `NotificationRepositoryIgnoredBulkTest` — `restoreAllIgnoredToPriority` / `deleteAllIgnored` / `deleteIgnoredByIds` 의 status-scope, dedup-append, empty-set, mixed-status guard 8 케이스 (plan `2026-04-27-ignored-archive-bulk-restore-and-clear` Tasks 1+6)
- `IgnoredArchiveMultiSelectStateTest` — long-press 진입 / toggle 가산·감산 / 빈 set 자동 비활성 / cancel·clear 의 7 케이스
- `SuppressionInsightsBuilderTest` / `InsightDrillDownSummaryBuilderTest` — `ignoredCount` 가 DIGEST/SILENT 와 분리 집계되는지

## Verification recipe

```bash
# 1. Settings → "고급 규칙 편집 열기" 에서 IGNORE 룰 생성 — 예: 키워드 "광고" + 액션 "무시 (즉시 삭제)"
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

- 화면 단독 Compose UI 테스트 부재 (repository + multi-select state + settings 단위 테스트로만 커버).
- (resolved 2026-04-27, plan `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md`) 아카이브 안에서 "이 row 되살리기" / "모두 삭제" / bulk delete 미구현 — 복구는 헤더 `모두 PRIORITY 로 복구` / per-row `PRIORITY 로 복구`, 삭제는 헤더 `모두 지우기` / 멀티셀렉트 `모두 지우기` 로 가능. SILENT/DIGEST 등 PRIORITY 외 status 로 복구하려면 여전히 Detail 의 "분류 변경" 경로.
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
- 2026-04-22: **Detail "무시" 버튼 제거 반영** — Detail 의 4-버튼 grid ("중요로 고정" / "Digest로 유지" / "조용히 유지" / "무시") 가 단일 "분류 변경" CTA 로 대체됨 (→ [notification-detail](notification-detail.md)). IGNORE 는 여전히 `Category.action == IGNORE` 경로로 생성 가능하지만 **Detail 피드백 경유 즉시 IGNORE 표시는 불가** — 사용자는 "분류 변경" → 기존 IGNORE Category 선택 혹은 "새 분류 만들기" 에서 action=IGNORE 선택을 거쳐야 후속 동일 알림이 IGNORE 로 라우팅된다. 아카이브 필터 / 진입 경로 / StatusBadge 계약은 불변. Plan: `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Tasks 2+6 (#245), Task 7 (this PR). `last-verified` 는 변경 없음.
- 2026-04-24: verification sweep — emulator-5554 에서 Settings 진입 → "무시된 알림 아카이브 표시" 토글 OFF→ON 시 같은 카드에 "무시됨 아카이브 열기" OutlinedButton 노출 확인. 버튼 탭 → `IgnoredArchiveScreen` 마운트, eyebrow "아카이브" / title "무시됨" / subtitle "IGNORE 규칙이 매치된 알림은 여기에 쌓여요. 기본 뷰에는 나타나지 않아요." + EmptyState "무시된 알림이 없어요 / IGNORE 액션 규칙을 만들면 여기에 쌓이기 시작해요." 로 doc 와 일치 (현재 DB 에 IGNORE row 0건). 토글 OFF 복귀 → 버튼 제거 관측. PASS. `last-verified` 를 2026-04-24 로 갱신.
- 2026-04-27: bulk action surface shipped — plan `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md` Tasks 3-7. Added `IgnoredArchiveMultiSelectState` (long-press 진입 / toggle / 자동 비활성), `IgnoredArchiveActionBar` (count + 모두 지우기 + 취소), 헤더 OutlinedButton 두 개 (`모두 PRIORITY 로 복구` 즉시 / `모두 지우기` AlertDialog confirm), per-row `PRIORITY 로 복구` TextButton, `deleteIgnoredByIds(Set)` repository + DAO query (status='IGNORE' guard). Tasks 1+2 (`restoreAllIgnoredToPriority` / `deleteAllIgnored` repository ops) 는 이전 PR #432 에서 분리 ship. Observable steps / Out-of-scope / Code pointers / Tests / Known gaps 동기화. **Verification 부분 완료**: emulator-5554 에서 settings 토글 ON → 진입 → empty state ("무시된 알림이 없어요") + EmptyState branch SnackbarHost 마운트 회귀 없음 확인. 비-empty branch 의 헤더 버튼 / multi-select / per-row 복구 시각 확증은 IGNORE row seed 가 필요한데 디버그 inject hook 부재 + IGNORE Category 가 현재 DB 에 없어 본 sweep 에서 미수행. plan Risks #4 의 `combinedClickable` long-press emulator 한계와 함께 후속 verification PR 로 분리. `last-verified` 는 2026-04-26 유지 (recipe 의 비-empty 경로 미실행).
- 2026-04-26: rotation re-verification (emulator-5554) — cold-start `am force-stop` 후 Settings 탭 진입, 스크롤하여 "무시된 알림 아카이브 표시" 카드 노출. 토글 OFF→ON 후 같은 카드에 "무시됨 아카이브 열기" OutlinedButton (bounds [391,574][690,623]) 1건 등장. 버튼 탭 → `IgnoredArchiveScreen` 마운트, eyebrow "아카이브" / title "무시됨" / subtitle "IGNORE 규칙이 매치된 알림은 여기에 쌓여요. 기본 뷰에는 나타나지 않아요." + EmptyState "무시된 알림이 없어요" + "IGNORE 액션 규칙을 만들면 여기에 쌓이기 시작해요." 로 Observable steps 5 (empty state) 정확히 일치 (현재 DB IGNORE row 0건). 뒤로가기 → 토글 OFF → 같은 카드에서 "무시됨 아카이브 열기" 사라짐 (Exit state). DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26.
- 2026-04-27: post-#433 bulk-affordance end-to-end sweep (emulator-5554, build `lastUpdateTime=2026-04-27 11:56:47`). 직접 sqlite `INSERT` 로 IGNORE row 3건 seed → 화면 재진입 시 헤더 카드 "보관 중 3건" + helper "최신 순으로 정렬돼 있어요. 길게 누르면 여러 건을 한 번에 지울 수 있어요." + 두 OutlinedButton (`모두 PRIORITY 로 복구` / `모두 지우기`) + per-row `PRIORITY 로 복구` TextButton 노출 (Observable step 6). 헤더 `모두 지우기` 탭 → AlertDialog "무시된 알림 3건을 모두 지울까요? / 되돌릴 수 없어요. SmartNoti 내 기록만 지우는 거라 시스템 알림센터에는 영향이 없어요." + `취소` / `모두 지우기` (Observable step 8 dialog 텍스트 일치, 취소로 가드). 첫 카드 long-press → `IgnoredArchiveActionBar` 등장 ("1개 선택됨" / `모두 지우기` / `취소`), 헤더 두 OutlinedButton + per-row `PRIORITY 로 복구` 모두 숨김 처리됨 (Observable step 7). 두번째 카드 탭 → "2개 선택됨" 으로 가산 → ActionBar `모두 지우기` 탭 → AlertDialog "선택한 알림 2건을 지울까요? / 되돌릴 수 없어요. 선택한 IGNORE 알림만 SmartNoti 기록에서 사라져요." + `취소` / `지우기`. 확인 → snackbar `"선택한 알림 2건을 지웠어요"`, 카운트 3→1, multi-select 자동 종료, 헤더 두 버튼 복귀 (Observable step 9 일치). per-row `PRIORITY 로 복구` 탭 → snackbar `"이 알림을 PRIORITY 로 복구했어요"`, 리스트 비고 EmptyState 분기 전환 (Observable step 7). 추가 IGNORE row 2건 seed + 화면 재진입 → 헤더 `모두 PRIORITY 로 복구` 탭 → confirm dialog 없이 즉시 snackbar `"무시된 알림 2건을 PRIORITY 로 복구했어요"` (Observable step 8 의 비파괴 직접 실행 일치). 시드 row 정리 cleanup. DRIFT 없음. `last-verified` 2026-04-26 → 2026-04-27.
