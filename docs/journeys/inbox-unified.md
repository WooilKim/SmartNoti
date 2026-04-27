---
id: inbox-unified
title: 정리함 통합 탭 (Digest + 보관 중 + 처리됨)
status: shipped
owner: @wooilkim
last-verified: 2026-04-27
---

## Goal

Digest 로 묶인 알림과 SILENT 로 분류되어 숨겨진 알림을 **한 탭** (`정리함`) 아래 세 개의 서브탭 (`Digest / 보관 중 / 처리됨`) 으로 통합해, 사용자가 "나중에 훑어볼 알림" 을 한 화면에서 모두 관리할 수 있게 한다. Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 이 기존의 분리된 `Digest` 탭 + `Hidden` 딥링크 진입점을 하나의 `Routes.Inbox` 로 묶은 결과.

## Preconditions

- 온보딩 완료
- 현 BottomNav 4-tab 구성 (`홈 / 정리함 / 분류 / 설정`)

## Trigger

- 사용자가 하단 네비의 "정리함" 탭 탭 → `Routes.Inbox` 진입
- Home 의 `HomeNotificationAccessInlineRow` / 기타 Home 에서 정리함 진입 링크 탭
- 기존 딥링크 (`Routes.Digest` 의 replacement notification contentIntent, `Routes.Hidden` 의 silent summary contentIntent) 는 여전히 **레거시 route 를 직접 열어** Detail 까지 연결 — 정리함 통합 탭을 우회. AppNavHost 가 `Routes.Digest` / `Routes.Hidden` 을 계속 등록 유지.

## Observable steps

1. `InboxScreen` 마운트, 다음 상태 구독:
   - `NotificationRepository.observeAllFiltered(hidePersistentNotifications)` — 필터된 전체 feed
   - `NotificationRepository.observeDigestGroupsFiltered(hidePersistentNotifications)` — Digest 앱 그룹
   - `SettingsRepository.observeSettings()` — persistent 필터
2. 헤더 행 — eyebrow "정리함" + title "알림 정리함" 두 줄이 좌측 column 으로 렌더되고 같은 Row 의 trailing 슬롯에 `InboxSortDropdown` 이 우측 정렬로 인라인 노출. 별도 subtitle 줄과 별도 sort row 는 렌더되지 않음 — F2 가 docstring-style subtitle 을 제거하고 sort dropdown 을 title 행으로 승격해 첫 화면 chrome 점유율을 ~25% → ~10% 로 축소. 카피·노출 구성은 `ui/screens/inbox/InboxHeaderChromeSpec` 가 단일 source of truth (eyebrow / title / `subtitle = null` / `sortDropdownIsInlineWithTitle = true`).
3. `InboxSortDropdown` 자체 — `정렬: <현재 모드 라벨>` + chevron icon. 사용자가 탭하면 `DropdownMenu` 가 `최신순 / 중요도순 / 앱별 묶기` 세 옵션을 보여주고, 선택 시 `SettingsRepository.setInboxSortMode(mode)` 가 호출되어 `SmartNotiSettings.inboxSortMode` 가 갱신된다 (다음 진입 시 복원). 선택된 모드는 세 서브탭 (Digest / 보관 중 / 처리됨) 모두에 동일하게 적용 — 단일 `inboxSortMode` 가 전체 정리함의 정렬 계약. `RECENT` 면 그룹 안 가장 최근 row 의 시각 desc, `BY_APP` 이면 `appName.lowercase()` asc, `IMPORTANCE` 는 status order (PRIORITY > DIGEST > SILENT) 후 recency desc — 정리함은 한 서브탭이 단일 status 만 보여주므로 `IMPORTANCE` 와 `RECENT` 의 가시적 효과는 동일 (Out of scope 참고).
4. `InboxTabRow` — 3개 세그먼트 (`Digest · N건 / 보관 중 · N건 / 처리됨 · N건`). 선택된 세그먼트는 `primaryContainer` 배경 + SemiBold.
5. 선택된 탭에 따라 기존 화면 composable 을 **재호스팅**:
   - `InboxTab.Digest` → `DigestScreen(mode = DigestScreenMode.Embedded, ...)` 본체 (그룹 카드 LazyColumn). `Embedded` 모드에서는 `DigestScreen` 자체의 `ScreenHeader` (`정리함 / 알림 정리함 / ...`) + `현재 N개의 묶음이 준비되어 있어요` SmartSurfaceCard 가 모두 렌더되지 않음 — outer `InboxScreen` 의 헤더 + sort dropdown + tab row 가 이미 같은 컨텍스트를 한 번에 전달하므로 중복 chrome 을 제거. `observeDigestGroupsFiltered` + `toDigestGroups` 의 packageName grouping 계약 그대로. `DigestScreen` 도 같은 `inboxSortMode` 를 collect 하여 `InboxSortPlanner.sortGroups(...)` 를 적용 — InboxScreen 호출 + 레거시 `Routes.Digest` deep-link 호출 두 경우 모두 동일 정렬. Standalone 진입 (`Routes.Digest` deep link) 은 기본 `DigestScreenMode.Standalone` 으로 호출되어 in-screen ScreenHeader + 요약 카드 보존.
   - `InboxTab.Archived` / `InboxTab.Processed` → `HiddenNotificationsScreen(mode = HiddenScreenMode.Embedded(SilentMode.ARCHIVED|PROCESSED), sortMode = <현재 모드>)` (`InboxToHiddenScreenModeMapper` 가 outer 탭 → embed mode 매핑). Embedded 모드에서는 자체 ScreenHeader + 내부 ARCHIVED/PROCESSED 세그먼트 row 가 렌더되지 않고 outer Inbox 탭 선택만으로 본문이 결정. Hidden 도 `InboxSortPlanner.sortGroups(...)` 로 그룹 순서 적용. Standalone 진입 (`Routes.Hidden` deep link, tray group-summary contentIntent) 은 기존대로 `HiddenScreenMode.Standalone` 으로 호출되며 sortMode default `RECENT` (Standalone 도 settings 를 collect 하도록 확장은 별도 plan).
6. 카운트 집계:
   - `digestCount` = `observeDigestGroupsFiltered` 의 모든 그룹 `count` 합.
   - `archivedCount` = `filteredNotifications.toHiddenGroups(SilentMode.ARCHIVED)` 의 `count` 합.
   - `processedCount` = `filteredNotifications.toHiddenGroups(SilentMode.PROCESSED)` 의 `count` 합.
7. Digest 탭 — `DigestScreen(mode = DigestScreenMode.Embedded)` 본체가 헤더/요약 카드 없이 그룹 카드 LazyColumn 으로 시작. `DigestGroupCard` preview 카드 탭 → `Routes.Detail.create(id)` 로 이동 (→ [notification-detail](notification-detail.md)). 그룹의 `items.size > 3` 이면 preview 끝에 "전체 보기 · ${remaining}건 더" inline CTA 가 노출되어 탭 시 카드 안에서 모든 row 가 펼쳐진다 (`rememberSaveable` 로 그룹별 `showAll` 상태 영속, Detail 진입 후 뒤로가기 시 동일 상태 복원). 토글 후 카피는 "최근만 보기". preview / "전체 보기" CTA 아래에는 그룹 단위 bulk action 행이 노출 — `모두 중요로 변경` (그룹 안 모든 DIGEST row 의 `status` → `PRIORITY`, `reasonTags` 에 `사용자 분류` dedup append) + `모두 지우기` (그룹 안 모든 DIGEST row 를 DB 에서 hard-delete) 두 OutlinedButton (`Row(weight 1f / 1f)`). 액션 후 `observeDigestGroupsFiltered` flow 가 자동 갱신 — `모두 중요로 변경` 은 그룹이 list 에서 사라지고 Home `검토 대기` count 가 즉시 증가, `모두 지우기` 는 그룹 자체가 사라진다. 확인 다이얼로그 없음 (Hidden 측 `모두 중요로 복구` / `모두 지우기` 와 동일 정책).
8. 보관 중 / 처리됨 탭 — 그룹 헤더 탭으로 expand → preview + bulk action (모두 중요로 복구 / 모두 지우기) 노출, preview 카드 탭 → Detail 진입. (→ [hidden-inbox](hidden-inbox.md) `status: deprecated` — 관측 계약은 이 journey 로 이동, 세부 UI behavior 은 `HiddenNotificationsScreen` 에서 보존)
9. BottomNav "정리함" 탭 활성 표시 + Detail 진입 후 뒤로가기 시 Inbox 탭으로 복귀.

## Exit state

- `InboxScreen` 에 현재 DB 의 DIGEST / SILENT-ARCHIVED / SILENT-PROCESSED 알림이 선택된 서브탭에 따라 렌더됨.
- IGNORE row 는 Digest / 보관 중 / 처리됨 어느 서브탭에도 포함되지 않음 (`observeDigestGroupsFiltered` + `toHiddenGroups` 가 `status` 로 선필터). 오직 [ignored-archive](ignored-archive.md) 에서만 노출.
- 시스템 tray 원본의 교체 / 숨김 동작은 각 분류별 journey 의 라우팅 계약을 그대로 따름 (→ [digest-suppression](digest-suppression.md), [silent-auto-hide](silent-auto-hide.md)).

## Out of scope

- 원본 tray 숨김 / 교체 라우팅 (→ [digest-suppression](digest-suppression.md), [silent-auto-hide](silent-auto-hide.md))
- 개별 알림 재분류 / 피드백 (→ [notification-detail](notification-detail.md), [rules-feedback-loop](rules-feedback-loop.md))
- IGNORE 아카이브 (→ [ignored-archive](ignored-archive.md))
- 통합 탭 내 cross-group bulk action (전체 Digest 삭제, 전체 SILENT 복구 등 — 현재 그룹별 (Digest / 보관 중 / 처리됨 모두) bulk 만 지원). `모두 무시` (IGNORE) bulk 는 의도적으로 제외 — IGNORE Category 자동 매핑 + Settings 의 `showIgnoredArchive` 토글 상호작용 결정 후 별도 plan.
- 서브탭별 다른 정렬 모드 (Digest 만 BY_APP, Hidden 은 RECENT 등) — 단일 `inboxSortMode` 하나가 세 서브탭 모두에 적용. 분리하면 사용자 모델이 복잡해지고 settings field 가 3 배로 늘어나므로 별도 plan.
- Cross-status mixed feed 의 `IMPORTANCE` 정렬 — 정리함 한 서브탭은 단일 status 만 보여주므로 (`Digest` 는 DIGEST, `보관 중`/`처리됨` 은 SILENT) `IMPORTANCE` 와 `RECENT` 의 정렬 결과가 동일. PRIORITY > DIGEST > SILENT 의 우선순위가 정리함에서 발현되려면 세 서브탭을 하나로 합쳐야 하므로 (architecture 변경) 별도 plan.
- DAO query 의 `ORDER BY` 변경 / DB index 추가 — 현재는 in-memory `InboxSortPlanner` 만 사용 (수백 건 단위 가정). 행 수가 폭증할 경우 DB 단으로 이주는 별도 plan.
- Standalone `Routes.Hidden` deep-link 의 `inboxSortMode` 적용 — 현재 Standalone 호출자는 `sortMode` 기본값 `RECENT` 로 fallback. settings collect 까지 확장은 별도 plan.

## Code pointers

- `ui/screens/inbox/InboxScreen` — 헤더 + 서브탭 세그먼트 + 본문 위임. F2 이후 outer header 는 `ScreenHeader` 컴포넌트를 사용하지 않고 inline `Row { Column { eyebrow + title }, InboxSortDropdown }` 로 직접 렌더 — title 행에 sort dropdown 을 trailing 으로 배치하기 위함.
- `ui/screens/inbox/InboxHeaderChromeSpec` — Inbox 헤더 chrome 의 단일 source of truth (eyebrow / title / `subtitle = null` / sort dropdown placement booleans). pure data object 라 `InboxHeaderChromeContractTest` 가 JVM 단에서 회귀 가드.
- `ui/screens/inbox/InboxToHiddenScreenModeMapper` — outer InboxTab → HiddenScreenMode.Embedded 매핑 (pure)
- `ui/screens/digest/DigestScreen` — Digest 서브탭 본체 (재사용, `mode: DigestScreenMode` 파라미터로 standalone/embedded 분기). `Embedded` 모드는 자체 ScreenHeader + 요약 SmartSurfaceCard 를 모두 끄고 그룹 LazyColumn 만 렌더. `groups.isEmpty()` 분기에서 [DigestEmptyStateAction] 의 카피 + `EmptyState` 의 `action` 슬롯으로 inline `숨길 앱 선택하기` CTA 노출 (호스트가 plumb 하는 `onOpenSuppressedAppsSettings` lambda 로 Settings 진입).
- `ui/screens/digest/DigestScreenMode` — sealed class (Standalone / Embedded). `HiddenScreenMode` mirror — Standalone 은 `Routes.Digest` deep-link 호환, Embedded 는 InboxScreen Digest 서브탭 위임 모드. `shouldRenderHeader()` 가 in-screen 헤더 + 요약 카드 가시성을 결정. `DigestScreenModeContractTest` 가 boolean 계약 고정.
- `ui/screens/digest/DigestEmptyStateAction` — Digest 서브탭 empty-state 의 title / suppress opt-in subtitle / `숨길 앱 선택하기` CTA 라벨 단일 source-of-truth (legacy `Routes.Digest` deep-link 와 inbox-unified Digest 서브탭이 동일 컴포넌트를 reuse 하므로 두 진입 경로의 카피 drift 방지). `DigestEmptyStateContractTest` 가 contract 고정.
- `ui/screens/hidden/HiddenNotificationsScreen` — 보관 중 / 처리됨 서브탭 본체 (재사용, mode 파라미터로 standalone/embedded 분기)
- `ui/screens/hidden/HiddenScreenMode` — sealed class (Standalone / Embedded). Standalone 은 deep-link 호환, Embedded 는 InboxScreen 위임 모드
- `data/local/NotificationRepository#observeDigestGroupsFiltered` + `toDigestGroups`
- `data/local/NotificationRepository#observeAllFiltered` + `toHiddenGroups(silentModeFilter = ...)`
- `data/local/NotificationRepository#restoreDigestToPriorityByPackage` + `data/local/NotificationRepository#deleteDigestByPackage` — Digest 서브탭 그룹 단위 bulk action 의 단일 query 위임 (`ApplyCategoryActionToNotificationUseCase` single-row 경로와 의도적으로 분리)
- `ui/screens/digest/DigestScreen#digestGroupBulkActionsSpec` — bulkActions slot wiring 의 pure helper (packageName 키 + 두 button 라벨), `DigestScreenBulkActionsWiringTest` 가 contract 고정
- `domain/model/SilentMode` — ARCHIVED / PROCESSED
- `navigation/Routes#Inbox` + `navigation/Routes#Digest` + `navigation/Routes#Hidden` (후자 둘은 deep-link 호환용으로 유지)
- `navigation/BottomNavItem` — 4-tab (`홈 / 정리함 / 분류 / 설정`)
- `ui/components/DigestGroupCard` — `PREVIEW_LIMIT` (3건) + `showAll` `rememberSaveable` 토글 + inline "전체 보기 · ${remaining}건 더" CTA (그룹 안 모든 row 의 Detail 진입 경로). bulkActions 는 CTA 아래에 그대로 렌더되어 그룹 전체 (DB 의 모든 row) 를 대상으로 동작 — `showAll` 상태와 무관.
- `ui/screens/inbox/InboxSortDropdown` + `labelFor` — `ScreenHeader` 와 `InboxTabRow` 사이 정렬 모드 dropdown + 모드별 한글 라벨 매핑 (`최신순 / 중요도순 / 앱별 묶기`). `labelFor` 는 pure helper 라 `InboxSortDropdownLabelTest` 가 contract 고정.
- `domain/model/InboxSortMode` — `RECENT / IMPORTANCE / BY_APP` enum. 추가는 backward-compatible append.
- `domain/usecase/InboxSortPlanner` — flat row / group 두 정렬 메서드 (in-memory pure helper). DAO ORDER BY 미변경.
- `data/settings/SmartNotiSettings#inboxSortMode` + `SettingsRepository#setInboxSortMode` + `applyPendingMigrations` 의 `INBOX_SORT_MODE` default 스탬프. `RECENT.name` 이 default.

## Tests

- `InboxScreen` 직접 UI 테스트 부재 — 하위 composable 테스트 + 집계 로직 테스트로 간접 커버
- `HiddenGroupsSilentModeFilterTest` — `toHiddenGroups` 의 silentMode 필터 (Hidden 재사용분)
- `BottomNavItemsTest` — 4-tab 순서 + 분류/정리함 라벨 pin
- `DigestGroupCardSeeAllTest` — `digestGroupCardPreviewState(itemsSize, showAll)` 의 visibility 계약 (`itemsSize <= 3` → CTA 부재, `> 3 && !showAll` → "전체 보기 · ${remaining}건 더" + 첫 3건만, `> 3 && showAll` → "최근만 보기" + 모든 row)
- `DigestScreenBulkActionsWiringTest` — Digest 서브탭의 그룹 단위 bulk action wiring (`packageName` 키 + 두 button 라벨 `모두 중요로 변경` / `모두 지우기`) 가 미래 PR 에서 빠지지 않도록 pure helper (`digestGroupBulkActionsSpec`) 의 contract 를 고정
- `NotificationRepositoryDigestBulkActionsTest` — `restoreDigestToPriorityByPackage` / `deleteDigestByPackage` 의 happy path + 격리 + 빈 그룹 + `사용자 분류` dedup 케이스
- `InboxSortPlannerTest` — `sortFlatRows` / `sortGroups` 의 RECENT / IMPORTANCE / BY_APP 케이스 + 빈 list / 단일 status 그룹 가드
- `InboxSortDropdownLabelTest` — `labelFor(mode)` 의 세 한글 라벨 매핑 + 라벨이 모드별로 distinct 함을 가드
- `InboxHeaderChromeContractTest` — `InboxHeaderChromeSpec` 의 eyebrow / title / `subtitle = null` / sort dropdown inline placement 5 case. 회귀 PR 이 docstring subtitle 을 다시 추가하거나 sort dropdown 을 별도 row 로 강등하면 즉시 fail.

## Verification recipe

```bash
# 1. Digest 로 분류될 반복 알림 다건 포스팅
for i in 1 2 3 4; do
  adb shell cmd notification post -S bigtext -t "Coupang" "Deal$i" "오늘의 딜 ${i}건"
done

# 2. SILENT ARCHIVED 가 쌓일 정도로 일반 알림 몇 건 포스팅
for i in 1 2; do
  adb shell cmd notification post -S bigtext -t "일반" "Promo$i" "프로모션 테스트"
done

# 3. 앱 실행
adb shell am start -n com.smartnoti.app/.MainActivity

# 4. BottomNav "정리함" 탭 탭 → `ScreenHeader 알림 정리함` + `InboxTabRow` 세그먼트 3개 (Digest / 보관 중 / 처리됨) 확인
# 5. "Digest" 세그먼트 기본 선택 상태에서 앱별 그룹 카드 렌더
# 6. "보관 중" 탭 → HiddenNotificationsScreen 마운트, ARCHIVED 카드 렌더
# 7. "처리됨" 탭 → HiddenNotificationsScreen 의 처리됨 세그먼트로 전환
# 8. 각 preview 카드 탭 → Detail 진입 → 뒤로가기로 동일 서브탭 복귀 확인
```

## Known gaps

- Digest 서브탭 count badge 는 앱 그룹 내 item 수의 합으로 계산 — 묶기 전 개별 알림 수가 아님. UI 가 의도된 동작이나 copy 로 명시 안 함.
- IGNORE 카운트는 어느 서브탭 헤더에도 표시되지 않음 (의도) — Settings 의 `showIgnoredArchive` 토글을 켜야만 별도 경로로 노출.
- Recipe 는 journey-tester 가 ADB 로 아직 end-to-end 검증하지 않음 — `last-verified` 비어 있음.
- 기존 `digest-inbox` / `hidden-inbox` 문서는 `status: deprecated` + `superseded-by: inbox-unified` 로 전환되지만, tray deep-link (`Routes.Digest` / `Routes.Hidden` ) 는 legacy route 를 직접 열어 통합 탭을 우회하므로 두 문서의 "진입 경로 = 딥링크" 서브셋은 여전히 계약 — 해당 부분은 deprecated 표시와 함께 Code pointers + Verification 으로 남긴다.
- **2026-04-28 ui-ux sweep (MODERATE × 5)** — 사용자 정성 피드백 "정리함이 정돈된 느낌이 전혀 없어" 에 대응한 audit (emulator-5554) 에서 5개 시각 부채 발견: ~~(F2) ScreenHeader + subtitle + sort dropdown row + InboxTabRow 가 첫 화면 25% 를 chrome 으로 점유~~ — **shipped 2026-04-28 (Change log entry 참고, PR #506-cohort F2)**. 잔여 4건: (F1) Digest 서브탭의 `DigestGroupCard` 가 카드 안에 카드를 중첩 (preview row 도 자체 outlined surface), (F3) 같은 화면에 orange accent 가 6회 등장 (group `Nn건` badge × 2 + 행별 `Digest` chip × 4) — accent 절제 원칙 위반, (F4) 한 화면 안에 4종의 카드 시각 언어 (summary / group / preview / nav) 가 corner radius / border / padding 모두 다름, (F5) 보관 중 / 처리됨 의 summary 카드가 탭 라벨과 그룹 카드와 카운트를 3중으로 반복. 후속: `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` 의 사용자 우선순위 (`f2 부터 차례대로`) 에 따라 F3 → F1 → F5 → F4 순으로 별도 PR 분기.

## Change log

- 2026-04-22: Plan `inbox-denest-and-home-recent-truncate` (PR pending) shipped — outer Inbox 탭 선택이 본문의 single source of truth 가 되도록 `HiddenScreenMode` (Standalone / Embedded) sealed class 도입. `InboxScreen` 은 outer "보관 중" / "처리됨" 진입 시 `InboxToHiddenScreenModeMapper.mapToMode(...)` 로 `HiddenScreenMode.Embedded(...)` 를 위임 → 자체 ScreenHeader + ARCHIVED/PROCESSED segment row 가 사라짐. Standalone 진입 (`Routes.Hidden` deep link) 은 기존대로 헤더 + 탭 row 유지 — ADB smoke 로 회귀 없음 확인. Observable steps 4 / Code pointers / Known gaps 갱신.
- 2026-04-26: Plan `2026-04-26-inbox-bundle-preview-see-all` ship — `DigestGroupCard` 의 expanded preview 영역에 inline "전체 보기 · ${remaining}건 더" CTA 추가 (`PREVIEW_LIMIT = 3`, `rememberSaveable` 로 그룹별 `showAll` 영속). `items.size > 3` 일 때만 CTA 노출, 탭 시 모든 row 가 카드 안에서 펼쳐지고 카피가 "최근만 보기" 로 토글. Detail 진입 후 뒤로가기 시 동일 그룹의 expanded-all 상태가 유지되므로 historical row (예: quiet-hours bundle 의 4번째 이후) 의 Detail 검증 경로가 회복됨. ADB 검증 on emulator-5554: Coupang 5건 그룹 (com.coupang.mobile, status SILENT, silentMode null → 처리됨 탭) 에서 expand → preview 3건 (`shoppingbody3/4/5`) + CTA "전체 보기 · 2건 더" → 탭 → 5건 모두 + CTA "최근만 보기" + bulkActions 그대로 → 4번째 row `shoppingbody2` 탭 → Detail 마운트 ("왜 이렇게 처리됐나요?" + chip row `발신자 있음 / 사용자 규칙 / 쇼핑 앱` + 전달 모드 카드) → 뒤로가기 → expanded-all 상태 복원 (5건 모두 + "최근만 보기") → 토글 → CTA 다시 "전체 보기 · 2건 더" + 4/5번째 row 사라짐. 회귀 없음 가드: Shell 1건 그룹 expand → CTA 부재. 그룹 max items 측정 (Risks open question): `SELECT packageName, COUNT(*) FROM notifications WHERE status='DIGEST' GROUP BY packageName ORDER BY COUNT(*) DESC LIMIT 5;` → `com.android.shell|8` (단일 그룹, 100건 임계 한참 아래) — inline LazyColumn 안 mount cost 안전. `last-verified` 는 verification recipe 를 처음부터 다시 돌리지 않았으므로 갱신 보류 (per `.claude/rules/docs-sync.md`).
- 2026-04-22: 신규 문서화 — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11 (#240) 구현 결과물. `InboxScreen` 이 Digest + Hidden 내용을 세그먼트 서브탭으로 통합. 레거시 `digest-inbox` / `hidden-inbox` journey 는 `status: deprecated` 처리되고 이 문서가 현재 계약을 소유. `Routes.Digest` / `Routes.Hidden` 은 tray deep-link 호환성을 위해 등록 유지. `last-verified` 는 journey-tester 의 ADB recipe 실행 전까지 비워둠 (per `.claude/rules/docs-sync.md`).
- 2026-04-26: Plan `2026-04-26-inbox-digest-group-bulk-actions` ship — Digest 서브탭의 `DigestGroupCard` 가 expanded preview / "전체 보기" CTA 아래에 그룹 단위 bulk action 행 (`모두 중요로 변경` / `모두 지우기`) 노출. 두 OutlinedButton 의 onClick 은 신규 repository 메서드 `restoreDigestToPriorityByPackage(pkg)` / `deleteDigestByPackage(pkg)` 단일 query 로 위임 — row N 회 호출이 아니라 packageName 기준 in-Kotlin transaction (restore) + DAO 단일 DELETE (delete). reasonTags 의 `사용자 분류` dedup append 의미는 `ApplyCategoryActionToNotificationUseCase` (Detail single-row 경로) 와 동일. 확인 다이얼로그 없음 — Hidden 측 동일 카피 (`모두 중요로 복구` / `모두 지우기`) 와 일관성 우선. ADB 검증 on emulator-5554: 광고 4건 seed (`com.android.shell` 그룹에 흡수, Digest count 24건 → Shell 21건 + Notifier 3건) → BottomNav `정리함` (407,2274) → Digest 서브탭 default → Notifier 그룹 `모두 중요로 변경` tap → Digest count 24건 → 21건 (Notifier 그룹 사라짐, 3 row promoted to PRIORITY) → Shell 그룹 expand → `모두 지우기` (770,1981) tap → Digest count 21건 → 0건 (Shell 21 row hard-delete) → empty state (`아직 정리된 알림이 없어요` + `반복되거나 덜 급한 알림을 여기에 모아둘게요`) 노출. 보관 중 17건 / 처리됨 10건 unaffected (status 격리 검증). DRIFT 없음. `last-verified` 는 verification recipe 를 처음부터 다시 돌리지 않았으므로 갱신 보류 (per `.claude/rules/docs-sync.md`).
- 2026-04-26: Verification recipe end-to-end PASS on emulator-5554 (cold-start). Coupang 4건 + 일반 2건 posting 후 BottomNav `정리함` (407,2274) tap → `ScreenHeader` eyebrow `정리함` + title `알림 정리함` + subtitle `Digest 묶음과 숨긴 알림을 한 화면에서 훑어볼 수 있어요.` + `InboxTabRow` 3 세그먼트 (`Digest · 18건 / 보관 중 · 16건 / 처리됨 · 6건`) 모두 documented copy 일치. Default `Digest` 선택 + 앱별 그룹 카드 (`SmartNoti Test Notifier 관련 알림 3건` 등) 렌더 (Observable steps 1–5). `보관 중` 세그먼트 (541,484) tap → Embedded HiddenScreen 본문에서 `2개 앱에서 16건을 보관 중이에요.` 요약 노출 (Observable step 4 InboxToHiddenScreenModeMapper 일치, embedded 내부 헤더/세그먼트 row 부재). `처리됨` (868,484) tap → `이미 확인했거나 이전 버전에서 넘어온 알림이에요. 필요하면 한 번에 지울 수 있어요.` 요약 카피 일치. Digest 탭 복귀 후 preview card `오늘만 특가 안내` (540,1750) tap → Detail (`알림 상세` + `왜 이렇게 처리됐나요?` + `전달 모드 · Digest 묶음 전달`) 진입 → KEYCODE_BACK → 같은 Inbox 서브탭 + 카운트 (`Digest · 18건 / 보관 중 · 16건 / 처리됨 · 6건`) 복원 (Observable step 8). DRIFT 없음. `last-verified` 2026-04-24 → 2026-04-26.
- 2026-04-27: Plan `2026-04-27-inbox-sort-by-priority-or-app` ship — 정리함 통합 탭 (`InboxScreen`) 의 `ScreenHeader` 와 `InboxTabRow` 사이에 `InboxSortDropdown` 노출 (`최신순 / 중요도순 / 앱별 묶기`). 단일 `SmartNotiSettings.inboxSortMode` 가 세 서브탭 (Digest / 보관 중 / 처리됨) 모두에 적용 — Digest 는 `DigestScreen` 이 settings 를 collect 해서 `InboxSortPlanner.sortGroups(...)` 호출, Hidden 두 탭은 `InboxScreen` 이 `sortMode` 를 prop 으로 흘려 `HiddenNotificationsScreen` (Embedded) 에서 동일 helper 호출. Standalone `Routes.Hidden` deep-link 호출자는 default `RECENT` fallback 유지. `IMPORTANCE` 는 정리함 한 서브탭이 단일 status 만 보여주므로 `RECENT` 와 가시 효과 동일 — Out of scope 에 명시 + cross-status mixed feed 는 별도 plan. DAO query 의 `ORDER BY` 변경 없음 (in-memory `InboxSortPlanner`). 신규 테스트: `InboxSortPlannerTest` (8 case) + `InboxSortDropdownLabelTest` (4 case). DataStore migration 은 `applyPendingMigrations` 안에서 `INBOX_SORT_MODE = RECENT.name` default 스탬프 (Task 2, PR #416). ADB end-to-end 검증은 후속 sweep 으로 위임 (Task 6 deferred). Observable steps 3 신규 + 4–9 renumber + Out of scope / Code pointers / Tests 갱신.
- 2026-04-27: Plan `2026-04-27-digest-empty-state-suppress-opt-in-cta` ship — Digest 서브탭의 empty-state (groups.isEmpty()) 가 suppress opt-in 유도 카피 (`숨길 앱으로 지정한 앱의 반복 알림을 여기에 모아드려요`) + inline `숨길 앱 선택하기` CTA (`FilledTonalButton`) 를 노출. CTA 탭 → `Routes.Settings` 로 launchSingleTop navigate (Settings 안의 숨길 앱 선택 섹션이 별도 sub-route 가 아니므로 Settings 화면으로 진입 후 사용자가 해당 섹션으로 스크롤 — sub-route 가 미래에 생기면 lambda 만 갱신). `DigestScreen` 시그니처에 required `onOpenSuppressedAppsSettings` 추가 — InboxScreen 도 동일 콜백을 호스트로부터 받아 forward. 카피/라벨 단일화는 `DigestEmptyStateAction` object + headless `DigestEmptyStateContractTest` 로 회귀 방지. Normal state (groups.isNotEmpty()) 는 무변경. ADB smoke on emulator-5554: 정리함 진입 후 모든 Digest 그룹 (`Digest · 30건` → `Digest · 0건`) 을 그룹별 `모두 지우기` 로 drain → empty 분기 진입 시 새 카피 + CTA 노출 확인 (uiautomator dump: `아직 정리된 알림이 없어요` + `숨길 앱으로 지정한 앱의 반복 알림을 여기에 모아드려요` + `숨길 앱 선택하기` Button bounds[359,1540][722,1645]) → CTA 탭 → Settings (`설정` eyebrow + title) 진입 + BottomNav `설정` 활성 확인. 레거시 `Routes.Digest` deep-link 진입 경로는 동일 composable + 동일 lambda 를 share 하므로 contract 동일 — 별도 ADB 검증은 후속 sweep 으로 위임 (replacement notification 시뮬레이션 환경 부재). `last-verified` 는 verification recipe 전체를 재실행하지 않았으므로 갱신 보류.
- 2026-04-27: Plan `2026-04-27-inbox-unified-double-header-collapse` ship — `DigestScreen` 에 `mode: DigestScreenMode = Standalone` 도입 (Hidden 측 `HiddenScreenMode` mirror). `InboxScreen` 이 Digest 서브탭 위임 시 `mode = DigestScreenMode.Embedded` 를 전달해 in-screen `ScreenHeader` (`정리함 / 알림 정리함 / ...`) + `현재 N개의 묶음이 준비되어 있어요` SmartSurfaceCard 를 모두 suppress — outer `InboxScreen` 의 헤더 + sort dropdown + tab row 가 이미 같은 컨텍스트를 전달하므로 중복 chrome 제거. Standalone deep-link (`Routes.Digest`) 진입은 기본 mode 로 헤더 + 요약 카드 보존. ADB smoke on emulator-5554: BottomNav `정리함` (407,2274) tap → uiautomator dump 에서 `알림 정리함` 텍스트 노드 1회만 등장 (이전: 2회), `현재 N개의 묶음이 준비되어 있어요` 부재, `Digest · 4건 / 보관 중 · 11건 / 처리됨 · 12건` tab row 헤더 직후 노출. 신규 테스트 `DigestScreenModeContractTest` (3 case) + 기존 `DigestScreenBulkActionsWiringTest` / `DigestEmptyStateContractTest` 회귀 없음. Standalone deep-link 진입 ADB 검증은 환경 부재 (replacement notification 시뮬레이션 불가) — contract test + 기본 mode preservation 으로 가드. `last-verified` 는 verification recipe 전체를 재실행하지 않았으므로 갱신 보류 (per `.claude/rules/docs-sync.md`). Commit: acaa878.
- 2026-04-27: Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` F2 ship — 정리함 통합 탭 헤더 chrome 축소. (1) `ScreenHeader` 컴포넌트 호출을 inline `Row { Column { eyebrow + title }, InboxSortDropdown }` 로 교체해 docstring-style subtitle 줄 제거 + sort dropdown 을 title 행 trailing 슬롯으로 승격. (2) 헤더와 tab row 사이의 dedicated sort row (~48dp) 가 사라져 첫 화면 chrome 점유율이 ~25% → ~10% 로 축소, 첫 group card (Shell 광고 preview) 가 above-the-fold 로 진입. (3) 카피·구성 contract 는 `ui/screens/inbox/InboxHeaderChromeSpec` 단일 object 로 응축해 `InboxHeaderChromeContractTest` (5 case) 가 회귀 가드 — docstring subtitle 재추가 / sort dropdown 별도 row 강등 두 가지 regression vector 모두 fail. behavior 변화 없음 — sort dropdown 의 `setInboxSortMode` wiring + persisted 정렬 모드 + 세 서브탭 wiring 전부 동일. ADB 검증 on emulator-5554: BottomNav `정리함` (407,2274) tap 후 uiautomator dump 에서 (a) `정리함` eyebrow @ y=174-219, `알림 정리함` title @ y=230-305, `정렬:` label @ y=218-263 (title row 와 vertically aligned, 우측 x=767-837) — 인라인 배치 확인, (b) `Digest 묶음과 숨긴 알림을 한 화면에서 훑어볼 수 있어요.` 텍스트 노드 부재 — subtitle 제거 확인, (c) `Digest · 4건 / 보관 중 · 11건 / 처리됨 · 12건` tab row @ y=387-436 — 헤더 직후 노출 확인, (d) sort dropdown tap → `최신순 / 중요도순 / 앱별 묶기` 세 옵션 그대로 — 동작 회귀 없음. Observable steps 2–3 갱신 (헤더 행 분리 + sort dropdown 인라인 표기), Code pointers 에 `InboxHeaderChromeSpec` + `InboxScreen` 갱신 노트 추가, Tests 에 `InboxHeaderChromeContractTest` 추가, Known gaps 의 F2 항목을 shipped 표기로 업데이트. `last-verified` 는 verification recipe 전체를 재실행하지 않았으므로 갱신 보류 (per `.claude/rules/docs-sync.md`).
- 2026-04-27: Verification recipe end-to-end PASS on emulator-5554 after fresh `:app:assembleDebug` + reinstall (PR #418 merged 00:15 UTC, prior APK was stale). Coupang 4건 + 일반 2건 seed → BottomNav `정리함` (407,2274) → 헤더 + 신규 `InboxSortDropdown` row (`정렬: 최신순` + chevron content-desc `정렬 모드 선택`) + 3 세그먼트 (`Digest · 11건 / 보관 중 · 18건 / 처리됨 · 11건`) 렌더 (Observable steps 1–4 일치). Dropdown tap → 메뉴 3 옵션 (`최신순 / 중요도순 / 앱별 묶기`) 모두 노출, `앱별 묶기` 선택 → 라벨 `정렬: 앱별 묶기` 로 즉시 갱신 + Digest 그룹 순서 `Coupang → 일반 → Shell` 알파벳 asc 로 재정렬 (Observable step 3 정렬 계약 일치). `보관 중` (541,642) tap → embedded HiddenScreen 본문 `2개 앱에서 18건을 보관 중이에요.` + Shell 17건 / Notifier 1건 그룹 (Observable step 4 mapper). `처리됨` (868,642) tap → `3개 앱에서 11건을 처리했어요.` + Coupang 6건 우선 (BY_APP sort 전파 확인). Digest 복귀 후 preview row (540,1850) tap → Detail (`알림 상세` + `왜 이렇게 처리됐나요?` + `발신자 있음 / 조용한 시간 / 쇼핑 앱` chip + `사용자가 설정한 시간 정책에 따라 자동으로 분류됐어요.`) 진입 → KEYCODE_BACK → Inbox Digest 탭 + `정렬: 앱별 묶기` 상태 보존 (Observable step 9 복귀 + Settings 영속). 정렬을 `최신순` 으로 복구 후 cleanup. DRIFT 없음. `last-verified` 2026-04-26 → 2026-04-27.
