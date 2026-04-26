---
status: shipped
shipped: 2026-04-26
superseded-by: ../journeys/categories-management.md
---

# Category Detail "최근 이 분류로 분류된 알림" preview Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `CategoryDetailScreen` 에 "최근 이 분류로 분류된 알림" preview 섹션을 추가한다. 사용자가 분류 탭 → row 탭으로 Detail 에 진입하면, 기존의 요약 카드 + 소속 Rule 리스트 아래에 **최근 5건** 의 알림 (해당 Category 의 `ruleIds` 와 `notification.matchedRuleIds` 가 교집합인 row, `postedAtMillis` 내림차순) 이 압축된 카드 형태로 노출되어 "이 분류 규칙들이 실제로 어떤 알림에 적용되고 있는가" 를 즉시 감사할 수 있다. 0건이면 "아직 이 분류로 분류된 알림이 없어요." empty-state 카피만 노출. 기존 요약/Rule 리스트/편집·삭제 액션의 동작과 위치는 변경하지 않는다.

**Architecture:**
- 신규 pure 함수 `CategoryRecentNotificationsSelector` (object 또는 top-level 함수) 가 `(category: Category, notifications: List<NotificationUiModel>, limit: Int = 5)` 를 받아 "category.ruleIds 와 notification.matchedRuleIds 가 교집합인 row" 만 필터링하고 `postedAtMillis` 내림차순 + `take(limit)` 으로 잘라 반환한다. `matchedRuleIds` 는 이미 `NotificationEntityMapper` 가 `ruleHitIds` 컬럼 (콤마 구분) 으로 영속화하여 `NotificationUiModel.matchedRuleIds: List<String>` 로 노출되므로 추가 storage 변경은 불필요.
- `CategoryDetailScreen` 시그니처에 `recentNotifications: List<NotificationUiModel>` 파라미터를 추가하고 (기존 `category` / `rules` 와 동일한 위치에 props 로 주입), 기존 LazyColumn 의 마지막 섹션 ("소속 Rule 리스트") 다음에 새 `item { ... }` 블록을 삽입한다. 섹션 헤더는 `SectionLabel("최근 분류된 알림")`. 각 row 는 압축된 형태 (앱 라벨 / 제목 / 본문 1줄 truncate / 상대 시간 — 예: `3분 전`). 탭 시 기존 `Routes.NotificationDetail(notificationId)` 로 nav 위임 — Detail 화면이 추후 재분류·되돌리기 흐름을 책임진다.
- 호출 사이트 (`MainActivity` / `AppNavHost` 의 Categories navigation 블록) 는 이미 `NotificationRepository.observeAll()` 결과의 일부 stream 을 다른 화면에 흘리고 있으므로, Category Detail 진입 시 추가로 `notificationRepository.observeAllFiltered(hidePersistentNotifications)` 를 collect 해서 `CategoryRecentNotificationsSelector.select(category, notifications)` 의 결과를 props 로 내려주면 된다. 신규 stream 추가는 1곳뿐.
- 상대 시간 카피는 기존 `ui/components/RelativeTimeFormatter` (또는 동등한 헬퍼) 가 있으면 재사용. 없다면 `CategoryRecentNotificationItem` 안에서 단순한 pure helper (`formatRelative(nowMillis, postedAtMillis)`) 를 분리해 unit test 한다 — 시간 포맷팅을 Compose 안에 직접 박지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Kotlin Flow (`combine` 으로 categories + notifications stream merge), JUnit unit tests.

---

## Product intent / assumptions

- **사용자 confirmed (gap 원문)**: `categories-management` 의 Known gap 세 번째 bullet `"Detail 화면이 '최근 이 Category 로 분류된 알림 preview' 를 아직 표시하지 않음 — 현재는 Rule 리스트 + 액션 chip 까지만. 후속 plan 대상."` 이 본 plan 의 출처. 추가로 `docs/plans/2026-04-24-categories-condition-chips.md` 의 Out of scope 에 `"Detail 화면의 '최근 이 Category 로 분류된 알림 preview' — 별도 Known gap, 본 plan 은 손대지 않음."` 으로 deferred 명시되어 있어, 본 plan 이 그 deferred-task 를 처리한다.
- **결정 필요 (default 제안)**: preview 건수 limit — 본 plan 은 **5건** 채택. 이유: Home 의 `HomeRecentNotificationsTruncation` 이 동일하게 5건을 cap 으로 사용 (`docs/plans/2026-04-22-inbox-denest-and-home-recent-truncate.md`) → 사용자가 "5건 = 빠른 감사용 sample" 이라는 mental model 을 이미 갖고 있다. 6건 이상이 필요한 사용자는 후속 plan 으로 "전체 N건 보기 → 정리함 진입" footer 를 추가할 수 있지만 본 plan 은 그 footer 를 포함하지 않는다 (분류 → 정리함 nav 라우트 결정이 별도 product 의사결정).
- **결정 필요 (default 제안)**: preview 매칭 기준 — 본 plan 은 **`category.ruleIds ∩ notification.matchedRuleIds`** 채택. 이유: 사용자가 "이 분류의 Rule 들이 실제 적용된 결과" 를 보고 싶어한다는 가정 (gap 원문 "분류된 알림"). `CategoryConflictResolver` 의 winner-derivation 결과가 아닌 단순 교집합이라, 같은 알림이 여러 Category 에 동시 매치된 경우 양쪽 Detail 모두에 노출될 수 있다 — Open question 에 명시.
- **결정 필요 (default 제안)**: ordering — `postedAtMillis` **내림차순** (가장 최신이 위). status 별 정렬은 적용하지 않음 (사용자가 "최근에 본 알림" 을 떠올리는 직관적 정렬).
- **결정 필요 (default 제안)**: 0건일 때 — `EmptyState` 컴포넌트 재사용 대신 단순 한 줄 카피 (`"아직 이 분류로 분류된 알림이 없어요."`) + 소형 padding 만 노출. `EmptyState` 는 화면 중앙용이라 Detail 안의 sub-section 에는 과한 시각 무게.
- **결정 필요 (default 제안)**: 압축 row 시각 — 별도 풀 `NotificationCard` 가 아닌 압축 (앱 라벨 / 제목 / body 1줄 / 상대 시간) 으로 노출. 기존 `NotificationCard` 는 status badge + reasonTags chip 까지 풀 렌더하므로 Detail 안에서 정보 밀도가 과하다. 신규 `CategoryRecentNotificationItem` (Composable) 을 새로 만든다 — 다른 화면에서 재사용하지 않는 한 file 내부 private 으로 둬도 무방.
- **결정 필요 (default 제안)**: 탭 동작 — 압축 row 탭 시 기존 `Routes.NotificationDetail` 로 nav. 백 버튼은 Category Detail 로 복귀 (back-stack). 별도 modal 노출 없음.
- **결정 필요 (default 제안)**: 상태 필터 — IGNORE row 는 Settings 의 `showIgnoredArchive` 토글과 무관하게 본 preview 에 **포함**. 이유: Detail 은 "이 분류가 무엇을 잡았는가" 를 보여주는 감사 화면이므로 IGNORE 분류의 row 가 보이지 않으면 사용자가 "이 분류는 일을 하고 있는가" 를 알 수 없음. `hidePersistentNotifications` 만 적용 (`observeAllFiltered`).
- 본 plan 은 `NotificationRepository` / `CategoriesRepository` / `Category` / `NotificationUiModel` 의 storage 모델을 변경하지 않는다 — 모두 derive.
- 카피/접근성: `CategoryRecentNotificationItem` 의 row 는 `Modifier.clickable` 로 탭 가능하게 만들고 `contentDescription` 은 `"$title, $relativeTime, 탭하면 알림 상세로 이동"` 형태로 설정 (TalkBack 안내). 압축 row 의 body 는 `maxLines = 1` + `TextOverflow.Ellipsis`.

---

## Task 1: Add failing tests for `CategoryRecentNotificationsSelector` [IN PROGRESS via PR #333]

**Objective:** preview 매칭 / 정렬 / limit 계약을 unit test 로 고정한 뒤 구현. 테스트 표면이 가장 명확한 곳부터 짠다.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoryRecentNotificationsSelectorTest.kt`

**Steps:**
1. 다음 시나리오를 assertion 으로 작성:
   - 빈 notifications → 빈 리스트.
   - notifications 가 모두 `matchedRuleIds` 가 빈 (예: 룰-미스 SILENT) → 빈 리스트.
   - `category.ruleIds = ["r1","r2"]`, notifications 6건 중 3건이 r1/r2 와 교집합 → 정확히 그 3건만 (`postedAtMillis` 내림차순).
   - 동일 알림이 여러 ruleId 에 매치되어도 (예: `matchedRuleIds=["r1","r3"]`, `category.ruleIds=["r1"]`) 한 번만 포함.
   - `limit = 5` 가 default 이고, 매치 row 가 7건이면 최신 5건만 반환.
   - `limit = 5` 에서 매치 row 가 정확히 5건이면 모두 반환.
   - `limit = 0` → 빈 리스트 (defensive).
   - `category.ruleIds` 가 비어 있으면 (rule-less Category) → 빈 리스트.
2. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.CategoryRecentNotificationsSelectorTest"` → 모두 RED 확인.

## Task 2: Implement `CategoryRecentNotificationsSelector` [IN PROGRESS via PR #333]

**Objective:** Task 1 의 테스트가 모두 GREEN 이 되게.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryRecentNotificationsSelector.kt`

**Steps:**
1. 다음 형태로 작성:
   ```
   object CategoryRecentNotificationsSelector {
       fun select(
           category: Category,
           notifications: List<NotificationUiModel>,
           limit: Int = 5,
       ): List<NotificationUiModel> { ... }
   }
   ```
2. 로직:
   - `if (limit <= 0) return emptyList()`.
   - `if (category.ruleIds.isEmpty()) return emptyList()`.
   - `val ruleIdSet = category.ruleIds.toSet()`.
   - `notifications.asSequence().filter { it.matchedRuleIds.any(ruleIdSet::contains) }.sortedByDescending { it.postedAtMillis }.take(limit).toList()`.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.ui.screens.categories.CategoryRecentNotificationsSelectorTest"` → 모두 GREEN.

## Task 3: Add failing test + implement relative-time formatter helper (only if no reusable one exists) [IN PROGRESS via PR #333]

**Objective:** 압축 row 의 시간 카피 (`"3분 전"` 등) 를 pure helper 로 분리해 unit test. 기존 helper 가 있으면 본 task 는 skip 하고 Task 4 에서 재사용.

**Files:**
- (조건부 신규) `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryRecentNotificationsRelativeTime.kt`
- (조건부 신규) `app/src/test/java/com/smartnoti/app/ui/screens/categories/CategoryRecentNotificationsRelativeTimeTest.kt`

**Steps:**
1. 먼저 `Grep` 으로 기존 `RelativeTime`, `formatRelative`, `agoMillis`, `시간 전`, `분 전` 등이 `app/src/main/java` 안에 이미 있는지 확인. **있으면 Task 3 skip + Task 4 에서 그 helper 를 호출**. 없으면 아래 진행.
2. helper signature: `fun formatRelative(nowMillis: Long, eventMillis: Long): String`. 분기:
   - `delta < 60s` → `"방금"`
   - `delta < 60m` → `"${minutes}분 전"`
   - `delta < 24h` → `"${hours}시간 전"`
   - `delta < 7d` → `"${days}일 전"`
   - 그 이상 → `"${days}일 전"` (단순화 — 본 plan 에서는 절대 날짜로 fallback 하지 않음).
3. `./gradlew :app:testDebugUnitTest --tests "*CategoryRecentNotificationsRelativeTimeTest"` → RED → 구현 → GREEN.

## Task 4: Add `CategoryRecentNotificationItem` Composable + section into `CategoryDetailScreen` [IN PROGRESS via PR #333]

**Objective:** 시각 시각화. CategoryDetailScreen LazyColumn 의 "소속 규칙 list" 다음에 신규 섹션 추가.

**Files:**
- 수정 `app/src/main/java/com/smartnoti/app/ui/screens/categories/CategoryDetailScreen.kt`
- (옵션) 신규 `app/src/main/java/com/smartnoti/app/ui/screens/categories/components/CategoryRecentNotificationItem.kt` (별도 파일이 깔끔하면 분리, 그렇지 않으면 `CategoryDetailScreen.kt` 안에 private composable 로 유지)

**Steps:**
1. `CategoryDetailScreen` 시그니처 확장: `recentNotifications: List<NotificationUiModel>`, `onOpenNotification: (notificationId: String) -> Unit` 두 파라미터 추가.
2. KDoc 의 "Recent-notification preview … are deferred" 문구 수정/제거.
3. LazyColumn 의 마지막 섹션 (소속 Rule 리스트) 다음에 새 `item` 추가:
   - `SectionLabel("최근 분류된 알림")`
   - `if (recentNotifications.isEmpty()) Text("아직 이 분류로 분류된 알림이 없어요.")` (`MaterialTheme.typography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`)
   - else `Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { recentNotifications.forEach { CategoryRecentNotificationItem(it, onClick = { onOpenNotification(it.id) }) } }`
4. `CategoryRecentNotificationItem` 은 `SmartSurfaceCard` (또는 `Surface` + small radius) 안에 `Column { Text(appLabel ?: packageName, style=labelSmall); Text(title, style=bodyMedium, fontWeight=FontWeight.SemiBold, maxLines=1, overflow=Ellipsis); Text(body, style=bodySmall, maxLines=1, overflow=Ellipsis); Text(formatRelative(now, postedAtMillis), style=labelSmall) }`. 카드는 `Modifier.clickable { onClick() }` + `contentDescription = "${title}, ${relative}, 탭하면 알림 상세로 이동"`.
5. ADB / preview 둘 다 깨지지 않도록 `@Preview` (있으면) + `MainActivity` / `AppNavHost` 의 Category Detail nav block 만 컴파일 깨지면 fix.

## Task 5: Wire repositories at the call site (`MainActivity` or `AppNavHost`) [IN PROGRESS via PR #333]

**Objective:** Category Detail nav 블록에서 `recentNotifications` 와 `onOpenNotification` 콜백을 props 로 주입.

**Files:**
- 수정 `app/src/main/java/com/smartnoti/app/MainActivity.kt` (또는 `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt` — Category Detail composable 호출 사이트)

**Steps:**
1. Category Detail nav 블록에서 기존 `categories` / `rules` collection 옆에 `notifications` collection 추가:
   ```
   val notifications by notificationRepository
       .observeAllFiltered(hidePersistentNotifications)
       .collectAsState(initial = emptyList())
   ```
   (`hidePersistentNotifications` 는 인접 `settings` state 에서 derive — 같은 nav 블록의 다른 화면이 이미 사용 중이면 그 패턴 재사용.)
2. `val recent = remember(categoryId, notifications, categories) { ... }` 안에서 `CategoryRecentNotificationsSelector.select(currentCategory, notifications)` 호출.
3. `CategoryDetailScreen(..., recentNotifications = recent, onOpenNotification = { id -> navController.navigate(Routes.NotificationDetail.create(id)) })`.
4. `Routes.NotificationDetail.create(...)` 의 정확한 시그니처는 `Grep` 으로 호출 사이트 (Inbox / Home recent / Hidden) 에서 확인 후 동일하게 사용.

## Task 6: ADB end-to-end verification + journey doc update [IN PROGRESS via PR #333]

**Objective:** emulator-5554 에서 Recipe 로 동작 확인 + `docs/journeys/categories-management.md` 의 Observable steps / Code pointers / Tests / Known gaps / Change log 갱신.

**Files:**
- 수정 `docs/journeys/categories-management.md`
- (해당 plan frontmatter) 수정 `docs/plans/2026-04-26-category-detail-recent-notifications-preview.md` → `status: shipped` + `superseded-by: docs/journeys/categories-management.md` (이 단계는 plan-implementer 가 PR merge 직전에 수행).

**Steps:**
1. APK rebuild + install. emulator 에 최소 1개 Category (예: 온보딩 quick-start `중요 알림` PRIORITY) 가 존재하는 상태로 시작.
2. `cmd notification post -t 'OtpTest' RecentPreview_T1 '인증번호 123456'` 와 같이 Category 의 Rule 매처와 일치하는 알림 3건을 시간차로 게시. 각 build 마다 `matchedRuleIds` 가 비어있지 않은지 `run-as com.smartnoti.app sqlite3 databases/smartnoti.db "SELECT id,ruleHitIds FROM notifications ORDER BY postedAtMillis DESC LIMIT 5;"` 로 확인.
3. 분류 탭 → 해당 Category row 탭 → Detail 진입. LazyColumn 마지막에 `최근 분류된 알림` 섹션 + 3건의 row (최신 위) 노출 확인.
4. row 1건 탭 → `NotificationDetailScreen` 진입 + 뒤로가기 → Category Detail 복귀 확인.
5. 매치 알림이 0건인 다른 Category (예: 비어있는 신규 Category) Detail → empty 카피 노출 확인.
6. journey 문서 갱신:
   - Observable step 4 ("Row 탭 → CategoryDetailScreen 진입") 의 sub-bullet 에 "최근 분류된 알림 — `category.ruleIds ∩ matchedRuleIds` 5건 cap, 최신순" 추가.
   - Code pointers 에 `ui/screens/categories/CategoryRecentNotificationsSelector` + `ui/screens/categories/components/CategoryRecentNotificationItem` (또는 분리하지 않은 경우 omit) 추가.
   - Tests 에 `CategoryRecentNotificationsSelectorTest` 추가.
   - Known gaps 의 "Detail 화면이 '최근 이 Category 로 분류된 알림 preview' 를 아직 표시하지 않음" bullet 을 Change log 한 줄로 옮기고 `(resolved YYYY-MM-DD, plan ...)` 마커 + `→ plan: docs/plans/2026-04-26-category-detail-recent-notifications-preview.md` 링크 추가.
   - Change log 신규 row: `YYYY-MM-DD: Category Detail 에 "최근 분류된 알림" preview 5건 cap 추가 — plan ...`.
   - `last-verified` 는 ADB 검증 일자로 bump.
7. 본 plan 의 frontmatter 를 `status: shipped` + `superseded-by: docs/journeys/categories-management.md` 로 갱신 (PR merge 직전).

---

## Scope

**In:**
- 신규 pure selector + (조건부) relative-time helper + 압축 Composable item.
- `CategoryDetailScreen` LazyColumn 에 신규 "최근 분류된 알림" 섹션 1개 추가.
- 호출 사이트 (`MainActivity` / `AppNavHost`) 에 notifications stream wiring 1곳.
- 단위 테스트 + ADB 검증 + journey 문서 갱신.

**Out:**
- "전체 N건 보기 →" footer / 정리함 nav 라우트.
- Category Detail 안에서 직접 재분류 (이는 NotificationDetail 의 책임).
- preview 정렬 옵션 / 필터 chip / 시간 범위 선택.
- IGNORE 토글 연동 (본 plan 은 항상 표시).
- Notification storage 모델 변경 (`matchedRuleIds` 는 이미 영속화됨).
- Home `HomeRecentNotificationsTruncation` 이나 Inbox 등 다른 화면의 preview 정책 변경.

## Risks / open questions

- **(open) 같은 알림이 여러 Category 에 매치되는 경우** — 본 plan 은 단순 교집합으로 양쪽 Detail 모두에 노출. `CategoryConflictResolver` winner 만 표시할지는 후속 product 의사결정. 현재 default 가 더 transparent 하지만, 사용자가 "이 알림은 결국 어느 분류로 갔는가" 를 헷갈릴 수 있다. plan-implementer 는 단순 교집합으로 ship 하고, 이 라인을 plan / journey Known gap 양쪽에 재기록.
- **(open) IGNORE 분류의 preview** — IGNORE 알림은 기본 뷰에서 숨겨지지만 본 preview 에서는 Settings 토글과 무관하게 노출. "Detail 은 감사 화면" 가설이 옳은지 사용자 리뷰. 다른 정책을 원하면 `CategoryRecentNotificationsSelector.select` 에 `includeIgnored: Boolean` 파라미터 추가 + Settings 연동 1줄.
- **(open) 5건 cap 의 적절성** — Home truncation 과의 일관성을 우선했지만 Category 가 매우 활발한 사용자 (예: 광고 분류) 는 5건이 너무 짧다고 느낄 수 있음. 후속에 "전체 N건 보기" footer 로 정리함 진입 가능. 본 plan 은 footer 추가하지 않음.
- **(open) 압축 row 시각** — `NotificationCard` 와 시각 차이가 너무 크면 사용자가 "이게 알림 row 인 줄 모를 수 있음." plan-implementer 는 Detail 의 다른 카드 톤 (`SmartSurfaceCard`) 과 visual 정렬을 우선. ui-ux-inspector 가 후속 sweep 에서 카드 톤을 평가.
- **(open) 상대 시간 helper 의 기존 존재 여부** — Task 3 가 조건부. plan-implementer 가 grep 으로 먼저 확인하고 결정. 기존 helper 가 있으면 본 plan 의 unit test 부담이 줄어든다 (Task 3 skip).
- **(non-blocker) Recipe 의 매치 보장** — `cmd notification post` 가 `com.android.shell` 로 들어오므로, Category 가 APP=`com.android.shell` rule 을 갖고 있어야 매치. 현재 온보딩 seed 분류는 KEYWORD 기반이므로 Task 6 의 ADB 검증은 키워드 인증번호 / 결제 / 배송 / 출발 중 하나를 body 에 포함시켜야 한다. 이미 reasonTags 와 `ruleHitIds` 에 keyword:* 가 채워지는지 live DB 로 확인.

## Related journey

- [`docs/journeys/categories-management.md`](../journeys/categories-management.md) — Known gap "Detail 화면이 '최근 이 Category 로 분류된 알림 preview' 를 아직 표시하지 않음 — 현재는 Rule 리스트 + 액션 chip 까지만. 후속 plan 대상." 이 본 plan 으로 해소된다 (Task 6 에서 Known gap → Change log 이동).

---

## Change log

- 2026-04-26: Backfilled status:shipped post-merge — STALE_FRONTMATTER incident, original PR #333 (merged 2026-04-26T01:25:03Z).
