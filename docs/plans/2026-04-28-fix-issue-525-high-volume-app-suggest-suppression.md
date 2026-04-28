---
status: shipped
shipped: 2026-04-28
fixes: 525
priority: P1
last-updated: 2026-04-28
superseded-by: docs/journeys/inbox-unified.md
---

# Fix #525 — 고빈도 앱 자동 묶음 처리 제안 (Inbox suggestion card)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Source issue: https://github.com/WooilKim/SmartNoti/issues/525 (P1 release-prep, depends on #511 (forward-only cancel) + #524 (one-tap orphan cleanup) for the value chain — accepting a suggestion only feels good once the cleanup also drains the historical orphans). Loop is in release-prep / issue-driven mode per merged meta-plan #479. Default daily threshold = 10/day average — surface as an Open Question for user confirmation; do not assume override.

**Goal:** R3CY2058DLJ-style 트레이 (네이버 24건, 카카오톡 20건, 쿠팡이츠 11건 등 7+ 앱이 압도적으로 많은 알림을 보내는 상태) 에서 사용자가 정리함 (`Routes.Inbox`) 의 첫 화면 첫 그룹 위에 단일 `InboxSuggestionCard` 를 본다 — 카드는 가장 시끄러운 앱 한 건 (e.g. `네이버에서 최근 7일간 평균 24건/일이 와있어요`) 의 앱 아이콘 (`AppIconResolver`, #510 PR #521 재사용) + 본문 + 3 버튼 (`예, 묶을게요` / `나중에` / `무시`) 만 노출. `[예]` → packageName 이 `suppressedSourceApps` 에 즉시 추가되고 `TrayOrphanCleanupRunner` (#524) 가 해당 packageName 한정으로 1회 동기 실행되어 트레이의 기존 잔존 알림도 같이 정돈, 카드 dismiss. `[나중에]` → 24h sticky hide. `[무시]` → `suggestedSuppressionDismissed` 에 영구 추가 + 다시 노출 안 됨. 사용자가 Settings 에 깊이 들어가지 않고도 manually 7+ 앱을 suppress list 로 옮기던 friction 이 한 번에 해소된다.

**Architecture:**
- 신규 `HighVolumeAppDetector` (`data/local`) 가 7-day rolling DB 쿼리 + threshold + 두 exclusion set 필터 + sort desc 를 단일 query 에 응축. `NotificationDao` 에 `@Query` 한 개 추가 (`countByPackageSince(sinceMillis): List<HighVolumeAppRow>` — `(packageName, appName, count)` projection). 다른 DAO 메서드와 동일한 Flow 또는 suspend 시그니처. 기존 `observeCapturedApps` 패턴 mirror — `CapturedAppOption` 처럼 `HighVolumeAppCandidate(packageName, appName, count: Int, avgPerDay: Double)` data class 를 새로 둔다.
- 신규 `HighVolumeAppSuggestionPolicy` (pure helper, `domain/usecase`) 가 detector 결과 + `SmartNotiSettings.{suppressedSourceApps, suppressedSourceAppsExcluded, suggestedSuppressionDismissed, suggestedSuppressionSnoozedUntil}` + clock → `HighVolumeAppCandidate?` (top-1 only) 를 결정. Excluded set 도 reuse — 사용자가 sticky 해제한 앱을 suggestion 으로도 다시 권유하지 않는다 (#524 의 sticky-exclude 의지 일관성).
- `SettingsRepository` 에 두 신규 path: `setSuggestedSuppressionDismissed(pkg, dismissed)` (영구) + `setSuggestedSuppressionSnoozeUntil(pkg, untilMillis?)` (24h hide). 두 경로 모두 sticky-exclude 와 동일한 atomic `edit { ... }` 패턴. `SettingsModels.SmartNotiSettings` 에 `suggestedSuppressionDismissed: Set<String>` + `suggestedSuppressionSnoozeUntil: Map<String, Long>` 두 필드 추가 (default 빈 collection — fresh install + 기존 사용자 모두 무영향).
- 신규 `InboxSuggestionCard` Composable (`ui/screens/inbox/`) 는 `InboxScreen` 의 LazyColumn 첫 item 위에 (sub-tab body 와 분리된) 별도 sticky-not-sticky `item { }` 으로 호스팅 — `InboxTabRow` 아래 + 첫 group card 위. Card 는 기존 `InboxCardLanguage.Primary` (corner 16dp / border 1dp / padding 16dp) 를 reuse 해 시각 언어 통일. 본문 1줄 + 3 OutlinedButton row (Hidden 의 bulk action row pattern mirror). 카피 / 라벨은 `InboxSuggestionCardSpec` (object) 로 응축해 `InboxSuggestionCardSpecTest` 가 회귀 가드.
- `[예]` 의 cleanup-trigger 경로는 #524 의 `TrayOrphanCleanupRunner` 를 그대로 재사용 — 단, 기존 `cleanup()` 이 모든 후보를 도는 것 대신 **scoped overload** `cleanup(targetPackages: Set<String>)` 한 메서드를 추가 (Tasks 4 에서 detail). 단일 packageName 만 cancel → idempotent. Listener 가 unbound 면 `notBound = true` 만 반환하고 토스트 한 줄 ("트레이 정리는 알림 권한이 활성일 때만 가능해요"). suggestion 자체의 dismiss 는 listener bind 와 무관하게 항상 진행 (suppress list 추가는 DataStore 만 건드리므로).
- DataStore migration 은 default 가 빈 collection 이므로 noop. PRIORITY-classified packages 는 detector 단계에서 제외 — `NotificationEntity.status == 'PRIORITY'` 인 row 만 보유한 packageName 은 후보에서 빠진다 (별도 SUM-by-status 서브쿼리). 정확한 SQL 형태는 Task 2 에서 결정.

**Tech Stack:** Kotlin, Room (`@Query` 추가만), DataStore (Set<String> + Map<String, Long>), Jetpack Compose (Material3, `OutlinedButton` × 3), Hilt (기존 module 에 detector / policy 바인딩), JUnit + Robolectric (DAO 테스트), MockK (policy 테스트), ADB e2e on R3CY2058DLJ.

---

## Product intent / assumptions

- **사용자가 원하는 affordance 는 "한 곳에서 많이 오는 앱을 쉽게 묶어달라" — 7+ 앱을 manually Settings 까지 들어가 toggle 하는 대신 inbox 에 inline 으로 제안.** 자동 (백그라운드) 으로 suppress 하지 않는다 — 사용자가 `[예]` 를 명시적으로 누르도록 해 trust + agency 보존. (#524 의 "사용자가 청소했다" mental model 과 동일 원칙.)
- **Top-1 only.** 한 화면에 후보 N개를 stack 하면 chrome 점유율이 다시 늘고 (메타플랜 `2026-04-28-meta-inbox-organized-feel-overhaul.md` F2 의 ~25% chrome 회귀 위험), 사용자도 압도된다. 가장 시끄러운 한 앱만 surface. `[예]` / `[나중에]` / `[무시]` 후 다음 ranked 후보가 다음 viewport 진입 시 자동 promote.
- **PRIORITY-classified packages 는 후보 제외.** 사용자가 이미 PRIORITY 로 분류해 둔 앱 (예: 친구 메신저, 가족 connection) 은 alarming-level 의 노이즈가 아니라 의도된 신호. detector 가 status='PRIORITY' row 만 가진 packageName 을 자동 제외 (Task 2 의 SQL 가드). 추후 mixed status 앱 (PRIORITY + DIGEST 섞임) 의 동작은 Open Question.
- **Threshold = 7 일 평균 ≥ 10 건/일** (default). R3CY2058DLJ 의 데이터 상 24/20/11/11/10/10 건이 후보가 되고, 5 건 이하 (Samsung 알림 등) 는 자연스럽게 빠진다. 사용자별 알림 양은 매우 다르므로 추후 Settings 에서 preset (5/10/20) 를 선택할 수 있게 해야 할 가능성 — Risks / open questions 의 Open Question.
- **`[나중에]` 는 24h sticky-hide.** Map<String, Long> 으로 packageName → 다시 보일 시각 (millis) 저장. 다음 진입에서 `untilMillis > now` 면 후보에서 제외, 아니면 expired entry 를 차일에 invalidate. 24h 는 임의 default — 사용자가 한 번 미루면 하루 동안은 같은 카드를 다시 안 본다는 직관에 따른 값.
- **`[무시]` 는 `suggestedSuppressionDismissed` 에 sticky 추가.** packageName 이 한 번 무시되면 detector 가 다시는 후보로 올리지 않는다. 사용자가 명시적으로 의지를 밝힌 케이스이므로 #524 의 sticky-exclude 와 동일 정책. Settings 에 별도 list/clear UI 는 v1 에서 추가하지 않음 (필요해지면 v2 — 앱 그룹별 cleanup 기능과 묶어서 결정).
- **`[예]` 는 immediate suppress + cleanup.** 사용자가 의지를 밝힌 시점 = 트레이가 깨끗해지는 시점. `suppressedSourceApps` 에 packageName 추가 + `TrayOrphanCleanupRunner.cleanup(setOf(pkg))` 호출 (#524 가 제공하는 scoped overload) 동기 invoke. 두 단계가 한 transaction 으로 묶이지는 않지만 둘 다 idempotent — 부분 실패 (DataStore commit 후 listener unbind) 는 다음 알림 도착 시 자동 정정 (#511 forward-only fix).
- **Open question for user (Risks 절에 명시):** Threshold default 가 10/day 가 적절한가? Settings 에 5/10/20 preset 노출하는 것이 v1 에 포함되어야 하는가, v2 로 분리해도 되는가? 본 plan 의 default 결정 — v1 은 hard-coded 10/day + Settings preset 미노출, v2 plan 으로 preset 분리.

---

## Task 1: Failing test — `HighVolumeAppDetectorTest` [IN PROGRESS via PR #532]

**Objective:** Issue body 의 정확한 fixture (네이버 24 / 카카오톡 20 / 쿠팡이츠 11 / 마이제네시스 11 / 삼성헬스 10 / 삼성캘린더 10 / 그 외 5건 이하) 가 들어왔을 때 detector 가 6개 후보를 avg-per-day desc 로 반환하고, 이미 `suppressedSourceApps` 멤버인 앱 / `suggestedSuppressionDismissed` 멤버인 앱 / `suppressedSourceAppsExcluded` 멤버인 앱 / 5건 이하 (threshold 미달) 앱을 제외하는지 RED 상태로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/data/local/HighVolumeAppDetectorTest.kt`

**Steps:**
1. `FakeNotificationDao` 또는 `Robolectric` 기반 in-memory `SmartNotiDatabase` — 두 가지 중 codebase 의 기존 DAO 테스트 패턴 (e.g. `NotificationRepositoryDigestBulkActionsTest`) 와 일치하는 쪽을 선택. 30 days fixture (오늘 - 7day window 안에 6 packageName × 다양한 daily count + 7day window 밖에 stale row 가 noise 로 섞임) seed.
2. Fixture A (`returns_top_n_above_threshold_excludes_suppressed_and_dismissed`):
   - `com.nhn.android.search` × 24 (avg 24/day, 7day total 24 → meets), `com.kakao.talk` × 20, `com.coupang.eats` × 11, `com.genesis.oneapp` × 11, `net.samsung.android.health.research.kor` × 10, `com.samsung.android.calendar` × 10
   - `com.nondrop.minor` × 5 (threshold 미달 — 제외)
   - `currentSuppressedSourceApps = setOf("com.kakao.talk")` (이미 등록 — 제외)
   - `currentSuggestedSuppressionDismissed = setOf("com.coupang.eats")` (영구 dismiss — 제외)
   - `currentSuppressedSourceAppsExcluded = setOf("com.genesis.oneapp")` (sticky-exclude — 제외)
   - 호출 `detector.detect(threshold = 10, windowDays = 7)` → `List<HighVolumeAppCandidate>` size = 3, 첫 entry = 네이버 (24/day), 그 다음 = 삼성헬스 (10/day), 그 다음 = 삼성캘린더 (10/day). 두 동률은 packageName asc tiebreak.
3. Fixture B (`empty_db_returns_empty`): seed 없이 호출 → emptyList.
4. Fixture C (`only_priority_packages_excluded`):
   - `com.friend.messenger` × 30 (모두 status='PRIORITY')
   - 호출 → emptyList. (PRIORITY-only packageName 가드)
5. Fixture D (`mixed_status_priority_and_digest_includes_in_count`):
   - `com.naver.shopping` × 5 PRIORITY + 15 DIGEST + 4 SILENT (총 24)
   - 호출 → 1 entry (`com.naver.shopping`, count=24). DIGEST/SILENT row 도 시끄러움 신호이므로 count 에 포함 — Open Question 으로 plan 에 명시 (PRIORITY 만 가진 앱 vs. mixed 의 차이는 status='PRIORITY' row 의 비중이 N% 이상이면 후보 제외 같은 더 정교한 룰로 분기 가능).
6. Fixture E (`stale_rows_outside_window_excluded`):
   - `com.naver.android.search` × 50 row 모두 `postedAtMillis < now - 7day` (stale)
   - `com.naver.android.search` × 8 row 가 window 안 (총 8 → 미달)
   - 호출 → emptyList.
7. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.local.HighVolumeAppDetectorTest"` 로 RED 확인.

## Task 2: Implement `HighVolumeAppDetector` + DAO query

**Objective:** Task 1 의 5 개 fixture 를 GREEN. 단일 DB 쿼리 pass + Kotlin 측 필터.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/data/local/HighVolumeAppCandidate.kt`
- 신규: `app/src/main/java/com/smartnoti/app/data/local/HighVolumeAppDetector.kt`
- 수정: `app/src/main/java/com/smartnoti/app/data/local/NotificationDao.kt` (단일 `@Query` 추가)

**Steps:**
1. `data class HighVolumeAppCandidate(val packageName: String, val appName: String, val count: Int, val avgPerDay: Double)` data class. `avgPerDay = count.toDouble() / windowDays.coerceAtLeast(1)` 로 detector 가 채운다.
2. NotificationDao 에 다음 형태의 `@Query` 추가:
   ```kotlin
   @Query("""
       SELECT packageName, appName, COUNT(*) AS count
       FROM notifications
       WHERE postedAtMillis >= :sinceMillis
         AND packageName NOT IN (
           SELECT DISTINCT packageName FROM notifications
           WHERE status = 'PRIORITY' GROUP BY packageName
           HAVING COUNT(*) = (SELECT COUNT(*) FROM notifications n2 WHERE n2.packageName = notifications.packageName AND n2.postedAtMillis >= :sinceMillis)
         )
       GROUP BY packageName
       HAVING COUNT(*) >= :threshold
       ORDER BY COUNT(*) DESC, packageName ASC
   """)
   suspend fun countHighVolumeAppsSince(sinceMillis: Long, threshold: Int): List<HighVolumeAppRow>
   ```
   `HighVolumeAppRow(packageName, appName, count)` POJO 는 DAO file 옆 internal data class. 정확한 SQL 은 구현 시 `assembleDebug` + Robolectric 으로 검증 — 위는 starting point. PRIORITY-only 가드는 SQL 안 (서브쿼리) 또는 Kotlin 측 (별도 PRIORITY-count 쿼리 후 in-memory 필터) 둘 중 simplicity 이긴 쪽을 택한다.
3. `HighVolumeAppDetector(dao: NotificationDao, clock: () -> Long = System::currentTimeMillis)`:
   - `suspend fun detect(threshold: Int = 10 * 7 /* 7-day total ≥ 70 → avg 10/day */, windowDays: Int = 7): List<HighVolumeAppCandidate>` — `sinceMillis = clock() - windowDays * 24L * 60 * 60 * 1000`. DAO 호출 → `count` 필드를 `avgPerDay` 로 환산한 candidate list 반환.
   - **NB:** threshold semantics 는 Task 1 의 fixture 와 일치해야 한다 — 7day total = 70 = avg 10/day. 단위 (per-day vs total-window) 는 Detector 호출자가 명확히 한다. `threshold = 10` 호출이 "7day total 10건" 으로 해석되면 R3CY2058DLJ 의 ~10/day 앱이 모두 미달로 빠짐. 그래서 `detect(threshold = 70, windowDays = 7)` 또는 detect signature 자체를 `detect(avgPerDayThreshold: Int, windowDays: Int)` 로 바꾸는 것이 명확. Task 2 에서 명시 결정.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.local.HighVolumeAppDetectorTest"` GREEN.

## Task 3: `HighVolumeAppSuggestionPolicy` (pure) + tests

**Objective:** Detector 결과 + Settings + clock → `HighVolumeAppCandidate?` (top-1) 결정 로직을 pure helper 로 분리해 viewmodel 의존성 없이 테스트 가능하게.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/HighVolumeAppSuggestionPolicy.kt`
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/HighVolumeAppSuggestionPolicyTest.kt`

**Steps:**
1. `object HighVolumeAppSuggestionPolicy { fun pickTop(candidates: List<HighVolumeAppCandidate>, snoozedUntil: Map<String, Long>, dismissed: Set<String>, suppressed: Set<String>, excluded: Set<String>, nowMillis: Long): HighVolumeAppCandidate? }`. 결정 순서:
   1. `dismissed` / `suppressed` / `excluded` 는 detector 가 이미 필터했지만 방어적 필터 한 번 더 (race condition 가드).
   2. `snoozedUntil[packageName] != null && snoozedUntil[packageName]!! > nowMillis` 면 제외.
   3. `candidates.firstOrNull { 제외 안 된 것 }` 반환.
2. Tests (5 case): empty, 모두 snoozed, 모두 dismissed, 첫 후보가 snoozed 인 경우 두 번째 반환, 첫 후보의 snooze 가 expired 인 경우 첫 후보 반환.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.HighVolumeAppSuggestionPolicyTest"` GREEN.

## Task 4: Extend `TrayOrphanCleanupRunner` with scoped overload (depends on #524)

**Objective:** `[예]` 가 즉시 단일 packageName 의 트레이 잔존 알림을 정돈할 수 있도록 #524 의 runner 에 scoped overload 추가. 전체 cleanup 과 동일 알고리즘이지만 `targetPackages` set 으로 후보를 prefilter.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/data/local/TrayOrphanCleanupRunner.kt` (#524 가 ship 된 후)
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/data/local/TrayOrphanCleanupRunnerTest.kt` (scoped overload fixture 추가)

**Steps:**
1. **#524 가 머지되어 main 에 들어가 있어야 함.** Plan dependency check: `gh pr list --state merged --search "fixes 524"` 또는 `git log --oneline -- app/src/main/java/com/smartnoti/app/data/local/TrayOrphanCleanupRunner.kt`. 머지 전이면 본 task 는 block — 우선순위 (#524 → #525) 직렬.
2. Runner 에 `suspend fun cleanup(targetPackages: Set<String>): CleanupResult` overload 추가. 기본 `cleanup()` 은 그대로 — overload 안에서 동일 식별 후 `entries.filter { it.packageName in targetPackages }` 추가 prefilter 만.
3. Test fixture 추가: `scoped_cleanup_only_cancels_target_packages` — 5 source orphan (네이버 / 카카오 / 쿠팡 / 제네시스 / 삼성헬스) 중 `cleanup(setOf("com.nhn.android.search"))` 호출 → cancelledCount == 1, gateway.cancelled = ["com.nhn.android.search:..."] 만.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.local.TrayOrphanCleanupRunnerTest"` GREEN.

## Task 5: Settings model + repository wiring

**Objective:** 두 신규 DataStore 필드 영속화 + atomic edit path 두 개 노출.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt` — `suggestedSuppressionDismissed: Set<String> = emptySet()` + `suggestedSuppressionSnoozeUntil: Map<String, Long> = emptyMap()` 두 필드.
- 수정: `app/src/main/java/com/smartnoti/app/data/settings/SettingsSuppressionRepository.kt` — `setSuggestedSuppressionDismissed(packageName, dismissed)` + `setSuggestedSuppressionSnoozeUntil(packageName, untilMillis: Long?)` 두 method (sticky-exclude pattern mirror, atomic `edit { }`).
- 수정: `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` — facade 두 method 노출.
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/data/settings/SettingsSuppressionRepositoryTest.kt` — 두 path 의 happy path + atomic + clear (`null` snooze 가 entry 제거) 케이스.

**Steps:**
1. DataStore Preferences key 명: `suggested_suppression_dismissed_v1` (Set<String>), `suggested_suppression_snooze_until_v1` (String — JSON map, `Map<String, Long>` 직렬화). 기존 패턴 (e.g. `suppressed_source_apps_excluded_v1`) 와 일치하는 직렬화 규칙 사용. Map 의 경우 codebase 에 기존 직렬화 헬퍼가 있는지 먼저 확인 — 없으면 simple `org.json.JSONObject` 또는 `key1=val1,key2=val2` 텍스트 포맷으로 시작 (DataStore Proto 까지 가지 않음, scope 절약).
2. `applyPendingMigrations` 에서 default 가 빈 collection 이므로 noop. 별도 migration key 도입 X.
3. Settings flow 가 자동으로 두 신규 필드를 노출 — `observeSettings()` 의 mapper 수정.
4. Test 5 case 모두 GREEN.

## Task 6: `InboxSuggestionCard` Composable + spec contract test

**Objective:** Card 의 시각 / 카피 contract 를 단일 source of truth (object) 로 응축 + headless contract test.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxSuggestionCard.kt`
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxSuggestionCardSpec.kt`
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/inbox/InboxSuggestionCardSpecTest.kt`

**Steps:**
1. `object InboxSuggestionCardSpec`:
   - `fun titleFor(candidate: HighVolumeAppCandidate): String` → `"💡 제안"` (eyebrow) — 정적.
   - `fun bodyFor(candidate: HighVolumeAppCandidate): String` → `"${candidate.appName}에서 최근 7일간 평균 ${candidate.avgPerDay.roundToInt()}건/일이 와있어요. 자동 묶음 처리 (DIGEST) 로 변경할까요?"`. 카피 한국어.
   - `const val LABEL_ACCEPT = "예, 묶을게요"`
   - `const val LABEL_SNOOZE = "나중에"`
   - `const val LABEL_DISMISS = "무시"`
   - `const val SNOOZE_DURATION_MILLIS = 24L * 60 * 60 * 1000` (24h)
2. `InboxSuggestionCard(candidate, onAccept, onSnooze, onDismiss, modifier)` Composable:
   - Outer Surface 는 `InboxCardLanguage.Primary` 토큰 (corner 16dp / border 1dp / padding 16dp) 사용 — 정리함의 다른 카드와 시각 언어 통일.
   - Row { largeIcon (`AppIconResolver.resolve(candidate.packageName)`, null 이면 placeholder Icon), Column { eyebrow + body }, } — eyebrow `"💡 제안"` (`labelMedium`, primary tint), body (`bodySmall`, onSurface).
   - Spacer 8dp.
   - Row { OutlinedButton(onAccept, LABEL_ACCEPT, weight 1f), OutlinedButton(onSnooze, LABEL_SNOOZE, weight 1f), OutlinedButton(onDismiss, LABEL_DISMISS, weight 1f) } — `Hidden` 의 bulk action row pattern mirror.
3. Spec test (4 case): bodyFor 의 평균 round (`24.0` → `"24건/일"`, `10.5` → `"11건/일"`), 세 버튼 라벨이 distinct, snooze duration > 0.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.inbox.InboxSuggestionCardSpecTest"` GREEN.

## Task 7: Wire into `InboxScreen`

**Objective:** Detector + policy + card + settings 를 InboxScreen 에 연결. card 는 `InboxTabRow` 아래 + 첫 sub-tab body 위에 single LazyColumn item 으로 렌더.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/ui/screens/inbox/InboxScreen.kt`
- 수정 (또는 신규 viewmodel): InboxScreen 의 host-level state 가 `HighVolumeAppDetector` + `HighVolumeAppSuggestionPolicy` + `SettingsRepository.observeSettings()` 를 collect 하는 경로 추가. 기존 viewmodel 패턴 (`DigestViewModel` 등) 와 일관.
- 수정: `app/src/main/java/com/smartnoti/app/di/AppModule.kt` (또는 해당 Hilt module) — `HighVolumeAppDetector` provide. `TrayOrphanCleanupRunner` 는 #524 가 이미 wire.

**Steps:**
1. InboxScreen 에 host-level `suggestion: HighVolumeAppCandidate?` state 추가. mounting 시 1회 detector 호출 (hot path 가 아니므로 LaunchedEffect 안에서 background dispatcher 로 호출 → main 으로 emit).
2. LazyColumn 의 `items` 블록 위에 `item(key = "suggestion-${suggestion.packageName}")` 으로 card 노출 — Digest 서브탭일 때만 (다른 두 서브탭 — 보관 중 / 처리됨 — 은 카드 없음, 사용자 의지가 모인 행동지점은 Digest). suggestion == null 이면 item 자체를 emit 하지 않아 LazyColumn 이 첫 group card 를 직접 렌더.
3. 세 콜백:
   - `onAccept`: `viewModel.acceptSuggestion(suggestion.packageName)` → `settingsRepository.setSuppressedSourceApps(current + pkg)` + `trayOrphanCleanupRunner.cleanup(setOf(pkg))` (background dispatcher) → suggestion state 를 null 로 clear (next-tick re-detect 가 다음 ranked 후보를 fill).
   - `onSnooze`: `viewModel.snoozeSuggestion(suggestion.packageName)` → `settingsRepository.setSuggestedSuppressionSnoozeUntil(pkg, now + SNOOZE_DURATION_MILLIS)` → state clear.
   - `onDismiss`: `viewModel.dismissSuggestion(suggestion.packageName)` → `settingsRepository.setSuggestedSuppressionDismissed(pkg, true)` → state clear.
4. `[예]` 후 `TrayOrphanCleanupRunner` 가 `notBound = true` 를 반환하면 Snackbar 한 줄: `"트레이 정리는 알림 권한이 활성일 때만 가능해요"`. suppressedSourceApps 추가는 listener bind 와 무관하게 이미 적용 — 사용자가 다음 알림이 올 때 #511 의 forward-only fix 가 자동으로 cancel.
5. Scroll position preservation: card 가 dismiss 된 후 LazyColumn 이 첫 item 의 stable key 로 scroll 을 유지하도록 `LazyListState.firstVisibleItemIndex / firstVisibleItemScrollOffset` 보존 — `rememberLazyListState()` 의 default 동작이면 충분하지만 검증 필요 (item key 는 위에서 명시).

## Task 8: Journey docs sync

**Files:**
- `docs/journeys/inbox-unified.md` — Observable steps 에 "Digest 서브탭 진입 시 LazyColumn 첫 item 위에 (suggestion 이 있을 때만) `InboxSuggestionCard` 노출" 한 줄 추가, Code pointers 에 `InboxSuggestionCard` + `InboxSuggestionCardSpec` 추가, Tests 에 contract test 추가, Change log 신규 line.
- `docs/journeys/digest-suppression.md` — Observable steps 1 ("auto-expansion") 옆에 별도 step 으로 "사용자 명시적 [예] 경로" 추가, Code pointers 에 `HighVolumeAppDetector` / `HighVolumeAppSuggestionPolicy` 추가, Change log 신규 line.
- `docs/journeys/categories-management.md` — 무관 (suggestion 은 Inbox 에만 노출되고 Categories 화면은 영향 없음). 단, `[예]` 후 그 packageName 이 Settings → 숨길 앱 리스트 에서 selected 로 보이는지 확인하는 cross-cut 한 줄을 Known gaps 의 "정리함 진입 후 후속 발견 경로" bullet 로 추가.

**Steps:**
1. 위 세 파일 갱신.
2. `last-verified` 는 verification recipe 를 다시 돌리지 않으므로 갱신 X — Change log 만.

## Task 9: ADB e2e on R3CY2058DLJ + plan flip

**Objective:** 사용자 디바이스 (R3CY2058DLJ) 의 현재 트레이 상태에서 plan 의 happy path 가 실제로 작동하는지 검증.

**Steps:**
```bash
# 0. 현재 상태 capture
adb -s R3CY2058DLJ shell dumpsys notification --noredact \
  | grep -oE "pkg=[a-z.]+" | sort | uniq -c | sort -rn | head -20
# 기대: 네이버 24 / 카카오 20 / 쿠팡 11 / ... 그대로

# 1. 신규 빌드 install
./gradlew :app:assembleDebug
adb -s R3CY2058DLJ install -r app/build/outputs/apk/debug/app-debug.apk

# 2. 정리함 진입 → suggestion card 노출 확인
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
adb -s R3CY2058DLJ shell input tap 407 2274  # BottomNav 정리함
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "제안|네이버|예, 묶을게요"
# 기대: card body "네이버에서 최근 7일간 평균 24건/일" + 3 버튼 노출

# 3. [예] tap → DataStore + tray cleanup 검증
adb -s R3CY2058DLJ shell input tap <accept_button_x> <accept_button_y>
sleep 2
adb -s R3CY2058DLJ shell dumpsys notification --noredact \
  | grep "pkg=com.nhn.android.search" | wc -l
# 기대: 0 (cleanup), 또는 PERSISTENT_PROTECTED 만 잔존

# 4. 다시 정리함 → 다음 ranked 후보 (카카오톡) 가 새 카드로 노출
adb -s R3CY2058DLJ shell am force-stop com.smartnoti.app
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
adb -s R3CY2058DLJ shell input tap 407 2274
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "카카오톡"
# 기대: 카카오톡 card 노출

# 5. [무시] tap → 다음 진입에서도 카카오톡 카드 부재
adb -s R3CY2058DLJ shell input tap <dismiss_button_x> <dismiss_button_y>
adb -s R3CY2058DLJ shell am force-stop com.smartnoti.app
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
adb -s R3CY2058DLJ shell input tap 407 2274
adb -s R3CY2058DLJ shell uiautomator dump /sdcard/ui.xml
adb -s R3CY2058DLJ shell cat /sdcard/ui.xml | grep -iE "카카오톡"
# 기대: 출력 없음 (다음 ranked 후보로 promotion)
```

Plan frontmatter 를 `status: shipped` + `superseded-by:` 로 flip. inbox-unified / digest-suppression journey 의 `last-verified` 는 재검증 시점에 별도 PR 로 갱신.

**Status (2026-04-28): plan flipped to `status: shipped`.** Tasks 1-7 shipped via PR #532 (commit `1b8927c`); Task 8 (journey docs sync) shipped via this PR. **Task 4 (scoped `cleanup(targetPackages)` overload of `TrayOrphanCleanupRunner`)** is **NOT** shipped — `InboxScreenSuggestionCallbacks.cleanupTrayOrphans` calls the unscoped `cleanup()` as a v1 fallback (documented in code as `Task 4 pending`). The user's intent ("stop bundling this app") still commits via the suppress-list write so the next-emission `#511` forward-only fix takes effect; the tray sweep is just broader than the plan envisaged. Tracking as a Known gap on `docs/journeys/inbox-unified.md`. **Task 9 (ADB e2e on R3CY2058DLJ)** is **NOT** executed in this PR — the loop emulator (emulator-5554) does not naturally carry an R3CY2058DLJ-class tray fixture (네이버 24 / 카카오톡 20 / 쿠팡이츠 11 / 마이제네시스 11 / 삼성헬스 10 / 삼성캘린더 10), and fabricating ADB output would violate `.claude/rules/clock-discipline.md` analogue for verification artefacts. The recipe above is preserved verbatim for whoever next has access to a real heavy-tray device; tracking as a second Known gap on `docs/journeys/inbox-unified.md`. Contract regression vectors are guarded by 26 new JVM unit cases (`HighVolumeAppDetectorTest` 6 + `InboxSuggestionCardSpecTest` 5 + `InboxSuggestionAcceptIntegrationTest` 4 + `SettingsRepositorySuggestedSuppressionTest` 11).

---

## Scope

**In:**
- 단일 신규 detector + 단일 신규 policy + 단일 card composable + 두 신규 DataStore 필드 + #524 runner overload + InboxScreen wiring 1점 + 카드 dismiss 의 세 분기 (예 / 나중에 / 무시).

**Out:**
- Settings 의 threshold preset (5/10/20) UI — v2 plan. 본 plan 은 hard-coded 10/day 로 ship.
- Settings 의 "무시한 제안 다시 보기" 화면 — v2 plan. dismiss 후 사용자가 의지를 번복하려면 Settings 의 숨길 앱 리스트에서 직접 추가.
- N개 후보 stack (multi-card carousel) — top-1 only 로 시작.
- Categories / Rules 자동 생성 — 본 plan 은 단순 `suppressedSourceApps` 추가만. 사용자가 더 정교한 rule 을 원하면 기존 onboarding 의 PROMO_QUIETING preset / Rules editor 경유.
- 보관 중 / 처리됨 서브탭에도 카드 노출 — Digest 서브탭만. 사용자 의지가 모인 행동지점은 Digest.
- Daily threshold 의 사용자별 자동 calibration — v2 plan. R3CY2058DLJ 같은 헤비 사용자에게는 10/day 가 너무 낮을 수도 있고, 라이트 사용자에게는 너무 높을 수도. v1 은 단일 default.

---

## Risks / open questions

- **Threshold default 가 10/day average 가 적절한가?** R3CY2058DLJ 의 데이터에서는 6 개 앱이 후보가 되고 자연스럽게 surface. 라이트 사용자 (하루 총 알림 < 30) 는 후보가 거의 없을 수도 있음. **사용자 (Open Question): v1 에서 default 10/day 를 hard-code 하고 v2 plan 으로 Settings preset (5/10/20) 분리하는 것에 동의하는가?** Plan 의 default 결정 — 동의로 가정. 반대 시 Task 5 + Settings UI 작업이 v1 에 추가됨 (~+1 task).
- **Mixed-status packages (PRIORITY + DIGEST 섞인 앱) 의 처리.** Task 1 Fixture D 의 `com.naver.shopping` 시나리오. 본 plan 은 "PRIORITY-only 앱만 제외, mixed 는 후보에 포함" 으로 정의 (DIGEST/SILENT row 도 시끄러움 신호). 다만 사용자가 이미 PRIORITY 로 분류한 앱에 대해 "묶을까요?" 라고 묻는 것은 mental model 충돌 가능성. **추후 사용자 신고 시 v2 plan 으로 정교화** — 예: PRIORITY ratio > 50% 면 후보 제외, 또는 PRIORITY rule 의 keyword 가 candidate 와 매치되지 않으면 후보 제외 등.
- **#524 의 scoped overload (Task 4) dependency.** #524 의 `TrayOrphanCleanupRunner` 가 main 에 머지되어 있어야 본 plan 의 Task 4 가 진행 가능. #524 가 block 되면 본 plan 의 Task 7 의 `[예]` 분기는 cleanup 없이 suppressedSourceApps 추가만 수행 (degraded but functional — #511 forward-only fix 가 다음 알림부터는 cancel). Plan implementer 는 dependency 명시적으로 check.
- **DataStore Map 직렬화.** Preferences DataStore 가 native Map<String, Long> 을 지원하지 않으므로 JSON 또는 string-encoded 직렬화 필요. Codebase 에 기존 helper 가 없으면 Task 5 가 ~+30 분 추가. Proto DataStore 까지 가지는 않음 — scope 절약.
- **카드 노출 유일성.** 사용자가 빠르게 정리함 진입 → 나가기 → 재진입 시 같은 카드가 또 보일 수 있음 (snooze/dismiss 전). UX 상 자연스럽지만, 만약 "한 세션에 한 번만" 이 더 직관적이면 in-memory `Set<String>` 으로 한 번 본 카드는 재진입에서 제외. v1 은 단순 — DataStore 영속화 두 set 만 의지. 사용자 신고 시 v2.
- **Top-1 만 노출하므로 "아 사실 카카오톡도 묶고 싶어" 가 카드 형태로는 보이지 않음.** v1 은 의도된 제한 — Settings 의 숨길 앱 리스트가 N 개 동시 toggle 의 표준 경로. 카드는 onboarding-after-the-fact 의 nudge.
- **24h snooze 가 시계 변경 / 시간대 변경에 취약.** `nowMillis` 비교가 wall clock 의존 — 사용자가 디바이스 시간을 수동으로 24h 뒤로 돌리면 snooze 가 즉시 expire. Production scenario 에서는 거의 없으므로 v1 무시.

---

## Related journey

- [`inbox-unified`](../journeys/inbox-unified.md) — Suggestion card 의 호스트 화면. shipped 후 Observable steps + Code pointers + Change log 갱신.
- [`digest-suppression`](../journeys/digest-suppression.md) — `[예]` 가 호출하는 path (`suppressedSourceApps` 추가 + cleanup) 의 contract. shipped 후 Observable steps 1 옆에 명시적 사용자 경로 추가, Change log 갱신.
- [`categories-management`](../journeys/categories-management.md) — 무관 (suggestion 은 Categories 화면 우회). 단 cross-cut Known gap 한 줄 추가.

Source issue: https://github.com/WooilKim/SmartNoti/issues/525
