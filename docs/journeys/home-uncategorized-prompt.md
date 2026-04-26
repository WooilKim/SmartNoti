---
id: home-uncategorized-prompt
title: 새 앱 분류 유도 카드 (Home)
status: shipped
owner: @wooilkim
last-verified: 2026-04-26
---

## Goal

아직 어느 분류에도 묶이지 않은 새 앱이 최근 알림을 보내기 시작하면 Home 최상단에 "**새 앱 분류 유도 카드**" 를 띄워 사용자를 자연스럽게 분류 생성 플로우로 유입한다. 파괴적 기본 동작을 피하기 위해 사용자는 "나중에" 를 탭해 24시간 스누즈할 수 있으며, 분류 커버리지가 충분히 넓어지면 (uncovered 앱 수 < 임계) 카드는 자동으로 사라진다.

## Preconditions

- 온보딩 완료
- 최근 7일 이내 `NotificationRepository.observeAllFiltered(...)` 에 `packageName` 이 포함된 알림이 **최소 3개 서로 다른 패키지** 로 쌓여 있고, 그 중 어느 Category 의 `appPackageName` 에도 pin 되어 있지 않음
- `SettingsRepository.observeUncategorizedPromptSnoozeUntilMillis()` < `System.currentTimeMillis()` (스누즈 해제 상태)

## Trigger

앱 실행 → Home 화면 composition → `UncategorizedAppsDetector.detect(...)` 가 `UncategorizedAppsDetection.Prompt` 를 반환하면 카드 mount.

## Observable steps

1. `HomeScreen` 이 다음을 구독:
   - `NotificationRepository.observeAllFiltered(hidePersistentNotifications)` — 최근 알림 feed
   - `CategoriesRepository.observeCategories()` — 현재 분류
   - `SettingsRepository.observeUncategorizedPromptSnoozeUntilMillis()` — 스누즈 만료 epoch millis
2. `UncategorizedAppsDetector.detect(notifications, categories, nowMillis, snoozeUntilMillis)` 가 pure function 으로 다음을 계산:
   - `coveredPackages` = `categories.mapNotNull { it.appPackageName }`
   - `cutoff = nowMillis - 7_DAYS`
   - `newestByPackage` = notifications 에서 `postTime >= cutoff` 이고 `packageName !in coveredPackages` 인 row 의 packageName → 최신 row 맵
   - `newestByPackage.size >= 3` 이면 `UncategorizedAppsDetection.Prompt(uncoveredCount, sampleAppLabels = top3 newest-first, samplePackageNames = top3)` 반환, 아니면 `None`
   - `nowMillis < snoozeUntilMillis` 면 무조건 `None`
3. `Prompt` 일 때 Home LazyColumn 최상단에 `HomeUncategorizedAppsPromptCard` mount:
   - `ContextBadge` "분류 제안"
   - Title "새 앱 {N}개가 알림을 보내고 있어요"
   - Body — `sampleAppLabels` 3개까지 joinToString. 4개 이상이면 "외 {N-3}개 앱을 분류하시겠어요?"
   - `OutlinedButton` "분류 만들기" + `TextButton` "나중에"
4. "분류 만들기" 탭 → `HomeUncategorizedPromptPrefillExtractor.extract(prompt)` 가 첫 sample (`samplePackageNames[0]` + `sampleAppLabels[0]`) 을 추출 → `onCreateCategoryClick(prefillPackage, prefillLabel)` 호출 → `navigateToTopLevel(Routes.Categories.create(prefillPackage = pkg, prefillLabel = label))` → 분류 탭 진입 직후 `CategoriesScreen` 의 nav-arg 가 `LaunchedEffect` 를 통해 `CategoryEditorTarget.New` + `CategoryEditorPrefill(name = label, appPackageName = pkg, defaultAction = PRIORITY)` 로 editor 다이얼로그 자동 오픈. 사용자가 dismiss 하면 `rememberSaveable("uncategorizedPrefillConsumed")` lock 으로 같은 destination 재진입 시 재발화 없음 — 빈 editor 가 떠야 한다 (→ [categories-management](categories-management.md)).
5. "나중에" 탭 → coroutineScope 에서 `SettingsRepository.setUncategorizedPromptSnoozeUntilMillis(now + 24h)` 실행 → 다음 composition 에서 `nowMillis < snoozeUntilMillis` 가 true 가 되어 detector 가 `None` 반환 → 카드 unmount.
6. 24시간이 지나거나 사용자가 3개 미만이 될 때까지 앱별 Category 를 만들면 카드 자동 비표시.

## Exit state

- "분류 만들기" → 사용자는 `Routes.Categories.create(prefillPackage, prefillLabel)` 로 이동해 첫 sample app 으로 prefill 된 editor 다이얼로그가 자동으로 떠 있는 상태에서 신규 Category 생성을 시작. dismiss 후 같은 destination 재진입 시 재오픈 없음 (single-consume).
- "나중에" → DataStore `settings` 에 24시간 뒤 epoch millis 가 persist, 다음 composition 에서 prompt 제거.
- covered 앱이 늘어 `uncoveredCount < 3` 이 되면 카드는 snooze 없이도 자동으로 숨겨짐.

## Out of scope

- 분류 생성 wizard 자체의 UI (현재는 기존 `CategoryEditorScreen` 다이얼로그로 진입; 전용 multi-step wizard 는 후속 plan)
- 신규 Category 가 생성된 후 detector 가 해당 앱을 커버로 판단하는 로직 — `Category.appPackageName` 이 설정돼야만 커버됨. 키워드/사람 기반 Category 만 만들면 여전히 uncovered 로 판단
- 새 앱 감지 외의 Home 구성 요소 (StatPill / PassthroughReview / Insight / Timeline 등 → [home-overview](home-overview.md))

## Code pointers

- `ui/screens/home/HomeScreen` — 최상단 prompt mount 분기 (`UncategorizedAppsDetection.Prompt` 케이스에서 `item { HomeUncategorizedAppsPromptCard(...) }` 추가)
- `ui/screens/home/HomeUncategorizedAppsPromptCard` — 카드 composable
- `domain/usecase/UncategorizedAppsDetector` — pure detector, THRESHOLD=3, SAMPLE_SIZE=3, 7-day window
- `domain/usecase/UncategorizedAppsDetection` — `None` / `Prompt` sealed class
- `data/settings/SettingsRepository#setUncategorizedPromptSnoozeUntilMillis` + `observeUncategorizedPromptSnoozeUntilMillis`
- `navigation/Routes#Categories` — FAB 타겟. `create(prefillPackage, prefillLabel)` 헬퍼가 query-arg URL 을 조립; 둘 다 비어 있으면 bare `"categories"` 로 collapse.
- `ui/screens/home/HomeUncategorizedPromptPrefillExtractor` — 첫 sample 추출 pure 헬퍼 (`samplePackageNames[0]` + `sampleAppLabels[0]`, blank package 는 absent 로 취급)
- `ui/screens/categories/CategoriesScreen#prefillPackage` / `#prefillLabel` — nav-arg pickup. `LaunchedEffect` 가 첫 composition 에서 editor 자동 오픈 + `rememberSaveable("uncategorizedPrefillConsumed")` 로 single-consume.
- `ui/screens/home/HomeUncategorizedPromptSnooze.TWENTY_FOUR_HOURS_MILLIS` — 24h 상수

## Tests

- `UncategorizedAppsDetectorTest` — 8 케이스:
  - 임계 미만 → None
  - 임계 이상 → Prompt + sampleAppLabels 최신순
  - 스누즈 상태 → None
  - 7일 이전 알림은 계산 제외
  - `Category.appPackageName` 이 커버하는 앱은 제외
  - 동일 패키지의 중복 알림은 1회만 계산 (dedup)
  - Sample 순서 newest-first
  - blank packageName 제외

## Verification recipe

```bash
# 1. 현재 Category 상태 확인 — 이미 3개 이상 앱을 Category 로 pin 해뒀다면
#    카드가 안 뜰 수 있음. 분류 탭에서 app-pin Category 를 삭제하거나
#    다른 package 에서 알림을 게시해야 함.

# 2. 서로 다른 3개 package 로부터 알림 게시 (cmd notification 은
#    기본 `com.android.shell` 패키지이므로 여러 앱 환경이 필요하면
#    실제 앱을 켜거나 pm install 한 테스트 앱을 활용)
adb shell cmd notification post -S bigtext -t "App1" T1 "알림1"
# 2번째, 3번째 앱 — 실 device 환경 의존

# 3. 앱 실행
adb shell am start -n com.smartnoti.app/.MainActivity

# 4. Home 최상단에 "새 앱 N개가 알림을 보내고 있어요" 카드가 뜨는지 확인
#    — N >= 3 이어야 렌더됨

# 5. "나중에" 탭 → 카드 즉시 사라지는지 확인
# 6. 앱 재실행 → 24시간 내에는 카드가 다시 뜨지 않음을 확인
#    (DataStore `uncategorized_prompt_snooze_until_millis` 값 조회로 교차 검증)

# 7. `am force-stop` 후 재진입 → 24시간 경과 후 다시 뜸 (wall-clock 의존)
```

## Known gaps

- `cmd notification post` 는 기본 `com.android.shell` 단일 packageName 으로 쌓이므로 실기기 / 여러 테스트 앱 환경이 없으면 THRESHOLD=3 을 충족시키기 어려움. 재현성 때문에 journey-tester recipe 는 실제 `com.smartnoti.testnotifier` 같은 extra 앱 설치 후 검증하는 편이 안정적.
- 카드 copy 가 `sampleAppLabels` 를 ", " 로 join 하여 라벨 길면 긴 줄로 줄바꿈 — FlowRow 등 레이아웃 조정은 후속 UI polish.
- (resolved 2026-04-26, plan `docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md`) "분류 만들기" 가 첫 sample app 으로 prefill 된 editor 다이얼로그를 자동으로 띄움 (4-step 마찰 → 1-step). Multi-app 동시 분류 / 전용 multi-step wizard 는 별도 후속 — 본 plan 은 첫 sample 1건만 prefill (사용자는 editor 의 multi-rule selector 로 추가 가능).
- ~~Recipe end-to-end 는 journey-tester 가 ADB 로 검증 전 (`last-verified` 비어 있음).~~ **Resolved 2026-04-26**: emulator-5554 ADB 로 PASS 확인 (Verification log 참고).
- `UncategorizedAppsDetector` 는 `Category.appPackageName` 으로만 커버 판정 — KEYWORD 타입 Rule 만 소속된 Category 는 커버리지 기여 없음. 의도된 동작이지만 사용자 입장에서는 "키워드 Category 가 있어도 카드가 계속 뜬다" 고 느낄 수 있음.

## Change log

- 2026-04-22: 신규 문서화 — plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 10 (#240) 구현 결과물. `UncategorizedAppsDetector` (pure) + `HomeUncategorizedAppsPromptCard` + `SettingsRepository` snooze 필드 shipped. `last-verified` 는 journey-tester 의 ADB recipe 실행 전까지 비워둠 (per `.claude/rules/docs-sync.md`).
- 2026-04-26: journey-tester 가 emulator-5554 에서 첫 end-to-end ADB 검증 → PASS. `last-verified` 공백 → 2026-04-26.
- 2026-04-26: "분류 만들기" 카드 탭이 editor 다이얼로그를 첫 sample app prefill 상태로 자동 오픈하도록 변경 — plan `docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md` (#409 Routes prefill, #410 AppNavHost + CategoriesScreen auto-open + lock, plus the Task 6 Home wiring PR). 신규 pure `HomeUncategorizedPromptPrefillExtractor` (`samplePackageNames[0]` + `sampleAppLabels[0]`, blank package → null) 가 카드 onCreateCategory 콜백에서 첫 sample 을 추출 → `Routes.Categories.create(prefillPackage, prefillLabel)` 로 navigation. `CategoriesScreen` 의 `LaunchedEffect` 가 `CategoryEditorTarget.New` + `CategoryEditorPrefill(name = label, appPackageName = pkg, defaultAction = PRIORITY, pendingRule = null)` 로 다이얼로그 자동 오픈. dismiss/save 후 `rememberSaveable("uncategorizedPrefillConsumed")` lock 으로 같은 destination 재진입 시 재발화 없음. ADB smoke (emulator-5554, plan Task 7): 분류 탭 BottomNav 진입은 회귀 없음 (bare `categories` URL 이 새 패턴에 정상 매칭, FAB 미탭 시 editor 미오픈) — 카드 → editor 자동 오픈 end-to-end 는 emulator 의 `cmd notification post` 가 단일 `com.android.shell` 패키지로 묶이는 한계로 본 sweep 에서 prompt 가 `None` (uncoveredCount < 3). 본격 e2e 는 실기기/멀티 테스트 앱 환경에서 다음 journey-tester sweep 에 위임 — recipe 는 본 문서 Verification recipe 그대로 유효.
