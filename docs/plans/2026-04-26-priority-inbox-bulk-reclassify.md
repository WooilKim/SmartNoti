---
status: planned
---

# Priority Inbox — Bulk Reclassify (모두 Digest / 모두 조용히)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 검토 대기 알림이 수십 건 (최근 verification 에서 34건 관측) 쌓인 사용자가 카드별로 N 회 탭하지 않고 **한 번에 여러 PRIORITY row 를 Digest 또는 조용히 로 재분류** 할 수 있다. PriorityScreen 에서 long-press 로 multi-select 모드 진입 → 선택 카운트 ActionBar → "→ Digest" / "→ 조용히" bulk CTA → 단일 snackbar (`"알림 N건을 Digest 로 옮겼어요"` / `"알림 N건을 조용히로 옮겼어요"`). 선택된 모든 row 의 `status` + `reasonTags` 가 `NotificationFeedbackPolicy` 경로로 일괄 갱신되어 Home `검토 대기 N` count 가 즉시 감소하고, 시스템 tray 의 원본 알림은 PRIORITY 의 `cancelSourceNotification=false` 불변조건과 무관하게 변하지 않는다 (재분류는 DB row + 후속 routing 만 영향, 이미 게시된 원본 tray entry 는 유지 — 기존 단일 row 인라인 액션과 동일 정책).

**Architecture:** 기존 `PassthroughReviewReclassifyDispatcher.reclassify(notification, action, createRule=false)` 를 N 건 ordered loop 로 호출하는 신규 `BulkPassthroughReviewReclassifyDispatcher` 를 도메인 layer 에 추가. UI 는 `PriorityScreen` 의 `NotificationCard` 행에 long-press → multi-select 진입을 추가하고, `ScreenHeader` 와 `SmartSurfaceCard` 사이 영역에 `PriorityMultiSelectActionBar` (선택 카운트 + "→ Digest" + "→ 조용히" + 취소) 를 띄운다. 단일 row 인라인 액션 (`PassthroughReclassifyActions` "→ Digest / → 조용히 / → 규칙 만들기") 은 multi-select 가 활성일 때만 hidden 처리 — non-active 모드에서는 기존 카드별 흐름이 그대로 유지된다. 카드 본문 탭은 multi-select 활성 시 toggle, 비활성 시 Detail navigation 으로 분기. "→ 규칙 만들기" bulk 경로는 본 plan out — bulk rule 생성은 selection 의 모든 row 가 같은 sender/app 일 때만 의미가 있어 별도 plan 후속.

**Tech Stack:** Kotlin, Jetpack Compose Material3, DataStore (`smartnoti_notifications` 간접 — `NotificationRepository.updateNotification`), Gradle JVM unit tests.

---

## Product intent / assumptions

- 현 흐름은 PRIORITY 검토 N 건 처리에 N 회의 카드별 탭을 강요한다. Home `HomePassthroughReviewCard` 가 "검토 대기 34" 같은 두 자릿수를 정상적으로 표시하는 단계가 되면 (실제 verification log 2026-04-26: `검토 대기 34건`), 사용자가 "한꺼번에 Digest 로 보내기" 가 가능하지 않다는 점이 분명한 UX 후퇴로 느껴진다 (priority-inbox Known gap 첫 bullet — "검토 화면 자체에 일괄 처리(모두 읽음/모두 재분류) 액션 없음 — 여전히 카드별 처리").
- 다음 결정은 implementer 에게 맡기지 않고 plan 단계에서 고정한다:
  - **Multi-select scope**: PriorityScreen 한 화면 안의 모든 PRIORITY row 가 동일 bucket — sub-section 분리는 없다. (rules-management 의 작업 필요/보류 와 다른 점.)
  - **Bulk action 의 결과**: 선택된 row 별로 `PassthroughReviewReclassifyDispatcher.reclassify(notification, action, createRule=false)` 를 호출. 이미 target status 인 row 는 dispatcher 의 `NOOP` 로 반환되므로 카운트에서 제외 (snackbar 문구는 실제 persisted 건수 사용). Rule 생성은 하지 않음 — 단일 row 경로의 "→ Digest" / "→ 조용히" 버튼과 동일.
  - **Snackbar 카피 단일화**: `"→ Digest"` 경로 `"알림 ${N}건을 Digest 로 옮겼어요"`, `"→ 조용히"` 경로 `"알림 ${N}건을 조용히로 옮겼어요"`. N=0 (모두 NOOP) 인 경우 snackbar 미등장 + selection 그대로 유지 (사용자가 다시 시도 가능).
  - **Cancel 정책**: 사용자가 명시적으로 "취소" 탭한 경우만 selection clear. Bulk action 성공 후에는 selection clear + ActionBar 사라짐.
  - **Empty-state UX**: bulk action 으로 모든 PRIORITY row 가 사라지면 PriorityScreen 의 기존 empty-state 분기가 자동으로 재발화 (`if (notifications.isEmpty()) { ... }`) — 별도 처리 없음.
- **남는 open question (user 결정 필요)**: 카드 본문 탭이 multi-select 활성 시 toggle 로 분기되면, 사용자가 "Detail 보기" 를 의도한 탭이 toggle 로 흡수되어 헷갈릴 수 있다. 두 후보:
  1. **카드 본문 탭 = toggle 전용** (Gmail 식 multi-select; 단순함. Detail 보기는 multi-select 종료 후로 미룬다.)
  2. **카드 본문 탭 = Detail navigation 유지, toggle 은 별도 leading checkbox 에서만** (Apple 메일 식; 시각적으로 더 안전하지만 작은 hit-target 추가 비용).
  본 plan 은 후보 1 을 가구현 (Compose 의 selection mode 표준 패턴이며 long-press 진입과 자연스럽게 연결). PR 본문에서 사용자에게 확정 요청.

---

## Task 1: Add failing tests for `BulkPassthroughReviewReclassifyDispatcher` [IN PROGRESS via PR #387]

**Objective:** 새 dispatcher 의 계약을 unit test 로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/domain/usecase/BulkPassthroughReviewReclassifyDispatcherTest.kt`

**Steps:**
1. Fake `updateNotification` (호출 record) + Fake `upsertRule` (호출 record — bulk path 에서 한 번도 호출되지 않아야 함) 작성. 단일 dispatcher 의 기존 테스트 패턴이 있다면 fake 구조 재사용; 없으면 in-memory `mutableListOf<NotificationUiModel>()` + `mutableListOf<RuleUiModel>()` 로 충분.
2. 테스트 케이스:
   - **happy path (Digest)**: `bulk(notifications=[n1, n2, n3], action=DIGEST)` → 3개 row 모두 PRIORITY 였다가 DIGEST 로 persist. 반환값 `BulkReclassifyResult(persistedCount = 3, noopCount = 0)`. `upsertRule` 호출 record 0 건.
   - **mixed status**: notifications 중 1건이 이미 DIGEST 인 상태로 들어오면 dispatcher 가 그 row 만 NOOP 로 분류 → `BulkReclassifyResult(persistedCount = 2, noopCount = 1)`. updateNotification 호출 record 2건.
   - **empty list**: `bulk([], DIGEST)` → `BulkReclassifyResult(persistedCount = 0, noopCount = 0)`. updateNotification 호출 0건.
   - **CONTEXTUAL action 거부**: `bulk(notifications=[n1], CONTEXTUAL)` → `BulkReclassifyResult(persistedCount = 0, noopCount = 0, ignoredCount = 1)`. (defense-in-depth — UI 가 절대 호출하지 않지만 단일 dispatcher 의 `IGNORED` 분기와 정합.)
   - **partial failure mid-loop**: 두 번째 notification 의 updateNotification 이 throw 하면 dispatcher 가 throw 를 propagate, 첫 번째 notification 은 이미 persist 된 상태로 유지. assertion: 첫 row 의 persist record 존재, 두 번째 row 의 persist 시도되고 throw, 세 번째 row 는 호출되지 않음.
3. `./gradlew :app:testDebugUnitTest --tests "*.BulkPassthroughReviewReclassifyDispatcherTest"` → RED 확인.

## Task 2: Implement `BulkPassthroughReviewReclassifyDispatcher` [IN PROGRESS via PR #387]

**Objective:** Task 1 의 테스트를 GREEN 으로.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/domain/usecase/BulkPassthroughReviewReclassifyDispatcher.kt`

**Steps:**
1. 단일 `PassthroughReviewReclassifyDispatcher` 를 생성자로 받는 thin wrapper (use case → use case composition). 단일 경로의 NOOP/IGNORED/UPDATED 정책을 그대로 위임 → 회귀 위험 최소.
2. `suspend fun bulk(notifications: List<NotificationUiModel>, action: RuleActionUi): BulkReclassifyResult`:
   - `if (notifications.isEmpty()) return BulkReclassifyResult.EMPTY`.
   - 누적 카운터 (persisted/noop/ignored) 를 들고 ordered `forEach` — 각 notification 에 대해 `single.reclassify(notification, action, createRule = false)` 호출, 반환 `ReclassifyOutcome` 에 따라 카운터 증가.
   - throw 는 catch 하지 않고 위로 (transaction 책임 없음 — 단일 경로와 동일 정책).
3. `data class BulkReclassifyResult(val persistedCount: Int, val noopCount: Int, val ignoredCount: Int)` + `companion object { val EMPTY = BulkReclassifyResult(0, 0, 0) }`.
4. `./gradlew :app:testDebugUnitTest --tests "*.BulkPassthroughReviewReclassifyDispatcherTest"` → GREEN.

## Task 3: Add failing tests for `PriorityScreenMultiSelectState`

**Objective:** Compose 외부의 pure state 로 multi-select 의사결정을 분리해 회귀 비용을 낮춘다.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/priority/PriorityScreenMultiSelectStateTest.kt`

**Steps:**
1. `PriorityScreenMultiSelectState` 의 의도된 모델: `data class State(val isActive: Boolean, val selectedNotificationIds: Set<String>)`. 초기값 `State(isActive = false, selectedNotificationIds = emptySet())`.
2. 케이스:
   - `enterSelection(seedNotificationId)` → isActive = true, selectedNotificationIds = setOf(seedNotificationId).
   - `toggle(notificationId)` → active 일 때 add/remove. 마지막 한 건 remove 시 자동 cancel (state 가 초기값으로 reset).
   - `toggle` 가 inactive 상태에서 호출되면 무시 (state 불변).
   - `cancel()` 은 항상 초기값으로 reset.
   - `clear()` (bulk action 성공 후 호출) 은 `cancel()` 과 alias — 의미 분리는 호출부에서.
3. `./gradlew :app:testDebugUnitTest --tests "*.PriorityScreenMultiSelectStateTest"` → RED.

## Task 4: Implement `PriorityScreenMultiSelectState`

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/priority/PriorityScreenMultiSelectState.kt`

**Steps:**
1. Pure Kotlin data class + 메서드 (Compose 의존 없음). `copy(...)` 기반의 fluent 메서드.
2. `./gradlew :app:testDebugUnitTest --tests "*.PriorityScreenMultiSelectStateTest"` → GREEN.

## Task 5: Wire multi-select gestures + selection chrome into PriorityScreen

**Objective:** PriorityScreen 의 `NotificationCard` row 에 long-press → multi-select 진입, 활성 시 본문 탭으로 toggle, ActionBar mount.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/priority/PriorityScreen.kt`
- 필요 시 신규 `app/src/main/java/com/smartnoti/app/ui/screens/priority/PriorityMultiSelectActionBar.kt`
- `app/src/main/java/com/smartnoti/app/ui/components/NotificationCard.kt` — `onLongClick` + `isSelected` 옵셔널 파라미터 추가 (단일 호출 사이트 외 다른 화면 사용 여부 grep 확인 후 default 값으로 호환)

**Steps:**
1. `PriorityScreen` 에 `var multiSelectState by remember { mutableStateOf(PriorityScreenMultiSelectState()) }` 추가.
2. `NotificationCard` 호출부에 새 콜백:
   - `onLongClick: () -> Unit` — `multiSelectState.isActive == false` 일 때만 `enterSelection(notification.id)`.
   - 본문 `onClick` 분기: `if (multiSelectState.isActive) multiSelectState = multiSelectState.toggle(notification.id) else onNotificationClick(notification.id)`.
3. `multiSelectState.isActive` 일 때:
   - `PassthroughReclassifyActions` (단일 row 인라인 카드) 는 hidden — 시각 노이즈 제거.
   - `NotificationCard` 의 `isSelected` 가 true 인 row 는 border accent + leading checkbox/check icon 으로 시각 구분.
   - `ScreenHeader` 와 `SmartSurfaceCard` 사이에 `PriorityMultiSelectActionBar` mount: `"${selectedNotificationIds.size}개 선택됨"` + `OutlinedButton("→ Digest")` + `OutlinedButton("→ 조용히")` + `TextButton("취소")`. "취소" 탭 → `multiSelectState = PriorityScreenMultiSelectState()`.
4. `NotificationCard` 의 시그니처 변경 (`onLongClick`, `isSelected`) 은 다른 호출부 (`HomeRecentNotificationCarousel` 등 grep 으로 확인) 에 default 값 (`onLongClick = {}`, `isSelected = false`) 으로 전파 — 회귀 없음 보장. 시그니처 변경 영향 범위가 5건을 넘으면 implementer 가 PR 분리 보고.
5. SmartSurfaceCard "검토 대기 N건" 카피는 multi-select 활성 시에도 그대로 노출 — 사용자가 전체 N 중 몇 건을 선택했는지 한눈에 확인 가능.

## Task 6: Wire `BulkPassthroughReviewReclassifyDispatcher` + snackbar at the ActionBar

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/priority/PriorityScreen.kt`

**Steps:**
1. `PriorityScreen` 에 `bulkDispatcher = remember(dispatcher) { BulkPassthroughReviewReclassifyDispatcher(dispatcher) }` 추가.
2. 필요한 selected NotificationUiModel 리스트 lookup: `val selectedModels = remember(notifications, multiSelectState.selectedNotificationIds) { notifications.filter { it.id in multiSelectState.selectedNotificationIds } }`.
3. `PriorityMultiSelectActionBar` 의 `onSendToDigest` / `onSilence` 콜백:
   - `scope.launch { val result = bulkDispatcher.bulk(selectedModels, RuleActionUi.DIGEST or SILENT); snackbarHostState.showSnackbar("알림 ${result.persistedCount}건을 ${labelFor(action)}로 옮겼어요") }` (persistedCount > 0 인 경우만).
   - persistedCount == 0 이면 snackbar 미등장 + multiSelectState 유지 (사용자가 다시 시도 가능 — 모든 row 가 이미 target status 인 정상 상태일 때).
4. Bulk action 성공 후 state 정리: `multiSelectState = PriorityScreenMultiSelectState()`. 다음 recomposition 에 selected row highlight + ActionBar 사라짐, persisted row 는 `observePriorityFiltered` flow 가 자동으로 list 에서 제거.
5. `SnackbarHost` 를 PriorityScreen scaffold 에 추가 (현재는 없음 — Material3 `SnackbarHost(snackbarHostState)` 를 LazyColumn 위 또는 `Scaffold` 와 같은 hierarchy 로 mount). 단일 row 경로는 현재 snackbar 없이 silently persist 하는데, 본 plan 은 bulk N 건만 명시 — 단일 row 경로 snackbar 추가는 out-of-scope.

## Task 7: Update journey doc

**Files:**
- `docs/journeys/priority-inbox.md`

**Steps:**
1. Observable steps 의 step 5 ("PassthroughReclassifyActions" 영역) 뒤에 step 5a 추가 — long-press 진입 → ActionBar → "→ Digest" / "→ 조용히" → snackbar `"알림 N건을 Digest 로 옮겼어요"` / `"알림 N건을 조용히로 옮겼어요"` → list 에서 동시 사라짐.
2. Known gaps 의 첫 bullet (`검토 화면 자체에 일괄 처리(모두 읽음/모두 재분류) 액션 없음 — 여전히 카드별 처리.`) 를 resolved 마커 + plan 링크로 교체 (gap 본문은 보존, 앞에 `(resolved YYYY-MM-DD, plan 2026-04-26-priority-inbox-bulk-reclassify) ` 마커 prefix + 끝에 `→ plan: ` 링크).
3. Code pointers 에 `domain/usecase/BulkPassthroughReviewReclassifyDispatcher` + `ui/screens/priority/PriorityMultiSelectActionBar` + `ui/screens/priority/PriorityScreenMultiSelectState` 추가.
4. Change log 에 새 row 1줄 (구현 PR 머지 시 commit 해시 채움).

## Task 8: ADB end-to-end verification

**Objective:** 실제 emulator 에서 두 경로 모두 동작 확인.

**Steps:**
```bash
# 사전: emulator-5554 가 실행 중, SmartNoti debug APK 설치, listener 권한 grant.
# 본 recipe 는 priority-inbox 의 기존 verification recipe (debug-inject hook) 를 재사용.

# 1. seed 4 PRIORITY rows via debug inject:
for i in 1 2 3 4; do
  adb -s emulator-5554 shell am broadcast \
    -n com.smartnoti.app/.debug.DebugInjectNotificationReceiver \
    --es title "BulkPriTest_$i" \
    --es body "bulk reclassify seed $i" \
    --es force_status PRIORITY
done

# 2. 앱 진입 → Home `검토 대기 N` 카드 탭 → PriorityScreen 진입.
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity

# 3. 첫 번째 BulkPriTest_1 row long-press → multi-select 진입 + ActionBar
#    "1개 선택됨 / → Digest / → 조용히 / 취소" 노출 확인.
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s emulator-5554 exec-out cat /sdcard/ui.xml | grep -E '선택됨|→ Digest|→ 조용히|취소'

# 4. 나머지 BulkPriTest_2/3/4 row 본문 탭 toggle → "4개 선택됨" 갱신 확인.

# 5. ActionBar "→ Digest" 탭 → 기대값:
#    - snackbar "알림 4건을 Digest 로 옮겼어요"
#    - 4개 row 모두 PriorityScreen 에서 사라짐
#    - SmartSurfaceCard "검토 대기 ${N-4}건" 갱신
#    - Home `검토 대기 ${N-4}` count 동기 갱신
#    - 정리함 Digest 서브탭 진입 → 4개 row 가 DIGEST 로 라우팅된 상태 확인
adb -s emulator-5554 exec-out run-as com.smartnoti.app sqlite3 \
  databases/smartnoti.db "SELECT id, status FROM notifications WHERE title LIKE 'BulkPriTest_%';"

# 6. "→ 조용히" 경로 검증 — BulkPriTest_5..8 다시 seed → multi-select → "→ 조용히"
#    → snackbar "알림 4건을 조용히로 옮겼어요" + Hidden 보관 중 탭에서 4건 확인.

# 7. NOOP 경로 — BulkPriTest_9 single seed → multi-select 1건 → "→ Digest"
#    → 정상 1건 persist → 즉시 다시 long-press 가능한 상태인지 확인 (selection
#    cleared, 새 long-press 로 mode 재진입).

# 8. baseline 복원 — sqlite3 DELETE 또는 디버그 reset 으로 검증용 row 삭제.
```

---

## Scope

**In:**
- 신규 `BulkPassthroughReviewReclassifyDispatcher` + 단위 테스트 5 케이스.
- 신규 `PriorityScreenMultiSelectState` (pure) + 단위 테스트 5 케이스.
- `PriorityScreen` 에 multi-select 진입 (long-press) + ActionBar + 선택 highlight + bulk action wiring + snackbar.
- `NotificationCard` 시그니처에 `onLongClick` + `isSelected` (default 호환) 추가.
- `docs/journeys/priority-inbox.md` 의 Observable step 5a 추가 + Known gap resolved 마커 + Code pointers + Change log row.

**Out:**
- "→ 규칙 만들기" bulk 경로 (selection 의 모든 row 가 같은 sender/app 일 때만 의미 있음 — 별도 plan 후속).
- "모두 읽음" / "모두 무시 (IGNORE)" bulk 액션 (Known gap 본문은 "모두 읽음" 도 언급하지만 PRIORITY 에는 read state 자체가 없으므로 본 plan 에서는 reclassify 두 경로만).
- bulk action 의 undo snackbar (단일 row 경로도 미지원).
- 시스템 tray 의 PRIORITY 원본 알림 cancel — `SourceNotificationRoutingPolicy.PRIORITY` 분기의 `cancelSourceNotification=false` 불변조건과 의도적으로 맞춤. Bulk reclassify 후 DIGEST/SILENT 가 된 row 의 routing 은 다음 tick 에 새 알림이 와야만 적용.
- PriorityScreen Compose UI 테스트 (Known gap 두 번째 bullet — 별도 plan 으로 추적, 본 plan 은 pure state + dispatcher unit test 로 한정).

---

## Risks / open questions

- **카드 본문 탭 vs Detail navigation 충돌** — 위 Product intent 절의 두 후보 (toggle 전용 vs leading checkbox 분리) 중 선택을 PR 본문에서 사용자에게 묻는다. 결정 전까지 implementer 는 후보 1 (toggle 전용) 로 가구현.
- **`NotificationCard` 시그니처 변경 영향 범위** — `onLongClick` + `isSelected` 추가가 다른 화면 (Home 의 `HomeRecentNotificationCarousel`, Inbox 의 Digest/Hidden bundle preview, NotificationDetailScreen 의 관련 카드 등) 에 default 값으로 전파될 때 회귀 없음을 grep + build 로 보장. 5건 초과 시 implementer 가 PR 분리 보고 후 멈춤.
- **ActionBar mount 위치** — `ScreenHeader` 와 `SmartSurfaceCard` 사이에 inline 영역으로 둘지, sticky/floating 으로 띄울지는 ui-improvement.md "ScreenHeader / SectionLabel 일관성" 측면에서 inline 권장. 단, scroll 이 길어진 상태에서 ActionBar 가 시야에서 벗어나면 multi-select 종료 어려움 — implementer 가 emulator 에서 long list (10+ rows) 로 scroll behavior 미세 조정.
- **NOOP 케이스 UX** — 모든 selected row 가 이미 target status 인 경우 (`persistedCount=0`) snackbar 미등장 + selection 유지. 사용자에게 "왜 아무것도 안 됐는지" 피드백이 없다는 약점 — Product intent 절에서 "정상 상태" 로 정의했지만 UX 후속 검토 가능.
- **재분류 후 system tray 원본 알림** — PRIORITY 의 `cancelSourceNotification=false` 정책상 bulk reclassify 후에도 시스템 tray 의 원본 알림은 그대로 남는다. 사용자가 "Digest 로 보냈는데 알림센터에 그대로 있어요" 로 혼동 가능. 단일 row 경로의 동작과 동일 — 본 plan 에서 별도 처리하지 않고 priority-inbox journey 의 "시스템 tray 처리" 섹션 (`SourceNotificationRoutingPolicy` PRIORITY 불변조건) 에 의존.
- **SnackbarHost mount** — PriorityScreen 은 현재 SnackbarHost 를 직접 갖지 않고 상위 NavHost level 의 host 에 의존하는지, 직접 mount 가 필요한지 implementer 가 grep 으로 확인. 직접 mount 시 Scaffold 도입 필요 여부 추가 검토.

---

## Related journey

- [`docs/journeys/priority-inbox.md`](../journeys/priority-inbox.md) — 본 plan 이 해소하는 Known gap: 첫 bullet `검토 화면 자체에 일괄 처리(모두 읽음/모두 재분류) 액션 없음 — 여전히 카드별 처리.`
