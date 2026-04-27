---
status: shipped
shipped: 2026-04-27
superseded-by: ../journeys/ignored-archive.md
---

# Ignored Archive: bulk "모두 지우기" + per-row 복구 어포던스

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `IgnoredArchiveScreen` 사용자가 (a) 한 번에 모든 IGNORE row 를 비우고 (header bulk action), (b) 개별 row 를 long-press 로 multi-select 후 한꺼번에 비우거나, (c) 한 row 를 즉시 PRIORITY 로 복구할 수 있게 한다. 현재는 plain list 만 노출되어 IGNORE row 가 무한히 쌓이는데 사용자가 "되돌리기" 또는 "정리" 를 원하면 row 별로 Detail → "분류 변경" → 다른 action 의 Category 선택 4-step 마찰을 거쳐야 한다 (Known gap `ignored-archive` line 92). 본 plan 은 [hidden-inbox](../journeys/hidden-inbox.md) / [digest-inbox](../journeys/digest-inbox.md) 가 이미 share 하는 그룹 bulk action 패턴 (`모두 중요로 복구` / `모두 지우기`) 과 동일 어휘를 IGNORE 단일 plain-list 에 일관되게 적용한다.

**Architecture:** `IgnoredArchiveScreen` 의 `SmartSurfaceCard` 헤더 우측에 두 OutlinedButton (`모두 PRIORITY 로 복구` / `모두 지우기`) 을 추가하고, "모두 지우기" 는 [`ConfirmDialog`](../../app/src/main/java/com/smartnoti/app/ui/components/ConfirmDialog.kt) 또는 동등한 확인 다이얼로그로 우발 발화 차단. 두 액션은 신규 `NotificationRepository.restoreAllIgnoredToPriority()` / `NotificationRepository.deleteAllIgnored()` 한 query 로 위임 (이미 존재하는 `restoreDigestToPriorityByPackage` / `deleteDigestByPackage` 패턴 — `app/src/main/java/com/smartnoti/app/data/local/NotificationRepository.kt` 참고). multi-select 는 [PriorityScreen 의 `PriorityScreenMultiSelectState`](../../app/src/main/java/com/smartnoti/app/ui/screens/priority/) 의 pure state 패턴 + `PriorityMultiSelectActionBar` UI 패턴을 mirror — long-press 로 진입, 카드 본문 탭은 multi-select 활성 시 toggle / 비활성 시 Detail navigation. 단일 row 복구 어포던스 (per-row 우하단 `TextButton "이 알림 PRIORITY 로 복구"`) 는 신규 `RestoreSingleIgnoredToPriorityUseCase` (이름 잠정) 가 `NotificationRepository.updateNotification(id, status=PRIORITY, reasonTags += "사용자 분류")` 단일 write 로 처리 — Detail 의 `ApplyCategoryActionToNotificationUseCase` 와 동일 의미론. snackbar 로 confirm + 자동 dismiss.

**Tech Stack:** Kotlin, Jetpack Compose, Room (`NotificationDao`), DataStore, Gradle JVM unit tests, ADB end-to-end on emulator-5554.

---

## Product intent / assumptions

- **복구 대상 status 는 PRIORITY 로 고정한다 (사용자 결정 필요).** 기존 Hidden 의 "모두 중요로 복구" 가 SILENT → PRIORITY 인 것과 일관. IGNORE 는 본질적으로 "삭제 의도" 였으므로 사용자가 복구를 결심했다면 가장 visible 한 PRIORITY 가 의도에 부합한다고 가정. (대안: SILENT 혹은 사용자 선택형 chooser. 사용자 선호가 다르면 Risks 섹션의 open question 으로 결정.)
- **"모두 지우기" 는 hard-delete (Room DELETE)** — IGNORE row 는 본디 사용자가 즉시 삭제를 원했던 알림이므로 retention 수명을 사용자가 단축하는 것 자체가 의도. Hidden 의 그룹 `모두 지우기` 와 동일 시맨틱.
- **rule/Category mutation 은 일어나지 않는다.** 본 액션들은 row 의 status 만 건드림 — 동일 sender/keyword 의 후속 알림은 여전히 owning Category 가 IGNORE 이면 다시 IGNORE 로 라우팅된다. 사용자가 "더 이상 IGNORE 로 보내지 마" 를 원하면 Category editor 로 가야 함 (현 ignored-archive Out-of-scope 와 일관, doc 에 명시).
- **multi-select UX 패턴은 priority-inbox 의 `PriorityScreenMultiSelectState` 를 그대로 mirror** — long-press 진입 / AppBar 자리에 카운터 + 액션 / 카드 본문 탭은 toggle 분기. 이미 ship 된 패턴이라 사용자에게 학습 부담 없음.
- **per-row "복구" TextButton 은 multi-select 가 비활성일 때만 노출** (priority-inbox Apple-식 대신 Gmail-식 long-press 패턴 채택과 동일 결정). single-tap 우발 발화는 confirmation 없는 단일 row 복구 + snackbar 로 충분 (5초 내 되돌리기 — 별도 plan 후보로 위임 가능; Risks 참조).
- 본 plan 은 `Out of scope` 의 "개별 row 를 아카이브 안에서 재분류하거나 복구하는 UI" 와 "bulk action" 두 줄을 모두 해소한다. journey 의 Out-of-scope 줄도 함께 갱신해야 한다.

---

## Task 1: Failing tests for new repository ops [SHIPPED via PR #432]

**Objective:** 두 신규 repository 메서드의 contract 를 테스트로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/data/local/NotificationRepositoryIgnoredBulkTest.kt`

**Steps:**
1. Robolectric (혹은 in-memory Room) 으로 IGNORE row 3건 + DIGEST row 1건 + PRIORITY row 1건 을 seed.
2. `restoreAllIgnoredToPriority()` 호출 → 3 IGNORE row 가 모두 PRIORITY 로 status flip + `reasonTags` 에 `사용자 분류` dedup append. DIGEST/PRIORITY row unaffected.
3. `deleteAllIgnored()` 호출 → IGNORE row 0건, 다른 status row count 보존.
4. Empty IGNORE set 에서 두 메서드 호출 → 0 row affected, no exception.
5. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.local.NotificationRepositoryIgnoredBulkTest"` 로 RED 확인.

## Task 2: Implement repository bulk ops [SHIPPED via PR #432]

**Objective:** Task 1 의 테스트 GREEN.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/local/NotificationRepository.kt`
- `app/src/main/java/com/smartnoti/app/data/local/NotificationDao.kt` (필요 시 신규 query 2개)

**Steps:**
1. `restoreDigestToPriorityByPackage` 의 in-Kotlin transaction 패턴을 mirror — `observeIgnoredArchive().first()` 로 row 스냅샷 → 각 row 에 대해 `updateNotification(id, status=PRIORITY, reasonTags=appendIfMissing("사용자 분류"))`.
2. `deleteDigestByPackage` 의 단일 DAO DELETE 패턴 mirror — `@Query("DELETE FROM notifications WHERE status = 'IGNORE'")` 신규 추가, suspend wrapper 호출.
3. `reasonTags` dedup append helper 가 이미 있으면 재사용 (priority-inbox / digest-inbox change log 참조), 없으면 pure helper 로 추출.
4. Task 1 테스트 GREEN 확인.

## Task 3: Pure state for archive multi-select [SHIPPED via this PR]

**Objective:** Compose 외부에서 검증 가능한 multi-select 상태.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveMultiSelectState.kt`
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveMultiSelectStateTest.kt`

**Steps:**
1. `priority-inbox` 의 `PriorityScreenMultiSelectState` 시그니처 그대로 차용 — `enter(id)`, `toggle(id)`, `clear()`, `isActive: Boolean`, `selected: Set<String>`, `count: Int`.
2. 4 케이스 단위 테스트: enter → toggle 가산/감산 / clear / 빈 set 자동 비활성.

## Task 4: ConfirmDialog 추출 (필요 시) [SHIPPED via this PR — inline AlertDialog]

**Objective:** "모두 지우기" 의 확인 다이얼로그 재사용 컴포넌트가 이미 있으면 채택, 없으면 single-purpose 컴포넌트 추가.

**Files:**
- (확인) `app/src/main/java/com/smartnoti/app/ui/components/` 안의 기존 confirm dialog 패턴 (Hidden 의 "전체 모두 지우기" 가 사용하는 것).
- 없으면 신규 `IgnoredArchiveBulkDeleteConfirmDialog.kt` (단일 메시지 "무시된 알림 N건을 모두 지울까요? 되돌릴 수 없어요." + 취소/지우기 두 버튼).

**Steps:**
1. Hidden 의 헤더 "전체 모두 지우기" 가 사용하는 컴포넌트를 grep 으로 확인 — 동일 패턴 재사용을 우선.
2. 신규 도입 시 카피는 Hidden 과 정렬 (`되돌릴 수 없어요` 문구 통일).

## Task 5: IgnoredArchiveScreen UI wiring [SHIPPED via this PR]

**Objective:** 헤더 bulk action + multi-select bar + per-row 복구 TextButton 을 시각적으로 연결.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveScreen.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/ui/screens/ignored/IgnoredArchiveActionBar.kt` (신규, multi-select AppBar)

**Steps:**
1. `SmartSurfaceCard` 헤더 우측 (count 라벨 옆) 에 두 OutlinedButton 추가 — `모두 PRIORITY 로 복구` (icon: Restore) / `모두 지우기` (icon: Delete). 빈 list 시 둘 다 미노출.
2. "모두 지우기" 탭 → confirm dialog → confirm → `repository.deleteAllIgnored()` + snackbar `"무시된 알림 N건을 모두 지웠어요"`. 카운터 N 은 dialog open 시점 캡처값.
3. "모두 PRIORITY 로 복구" 탭 → 즉시 `repository.restoreAllIgnoredToPriority()` + snackbar `"무시된 알림 N건을 PRIORITY 로 복구했어요"`. (확인 다이얼로그 없음 — 복구는 비파괴.)
4. `LazyColumn` 의 `NotificationCard` 를 `combinedClickable` 로 감싸 long-press → `multiSelectState.enter(id)`. multi-select 활성 시 본문 탭 = toggle, 비활성 시 Detail navigation (기존 동작).
5. multi-select 활성 시 `IgnoredArchiveActionBar` (카운트 + `모두 지우기` + `취소`) 가 ScreenHeader 자리를 대체. confirm dialog 후 `repository.deleteIgnoredByIds(selected)` (Task 2 또는 Task 6 의 신규 메서드) + snackbar.
6. multi-select 비활성 시 각 `NotificationCard` 우하단에 `TextButton("PRIORITY 로 복구")` 노출 — 탭 시 `repository.updateNotification(id, status=PRIORITY, reasonTags += "사용자 분류")` 단일 write + snackbar `"이 알림을 PRIORITY 로 복구했어요"`.
7. 빈 set 으로 떨어지면 multi-select 자동 종료 (Task 3 state contract).

## Task 6: Per-id bulk delete (선택) [SHIPPED via this PR]

**Objective:** multi-select 의 N row 한꺼번에 지우기 query.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/local/NotificationRepository.kt`
- `app/src/main/java/com/smartnoti/app/data/local/NotificationDao.kt`

**Steps:**
1. `@Query("DELETE FROM notifications WHERE id IN (:ids)")` 신규 + suspend wrapper.
2. 단위 테스트 1건 (Task 1 파일에 추가): mixed-status 5 row 중 IGNORE 2 row id 만 전달 → 2 row 만 삭제.

## Task 7: Update journey doc [SHIPPED via this PR]

**Objective:** [`docs/journeys/ignored-archive.md`](../journeys/ignored-archive.md) 의 Observable steps + Out-of-scope + Known gaps + Change log 동기화.

**Files:**
- `docs/journeys/ignored-archive.md`

**Steps:**
1. Observable step 6 의 `LazyColumn 이 NotificationCard 로 개별 row 렌더 (그룹 collapse / bulk action 없음 — 의도적으로 plain list)` 줄을 갱신 — bulk action 헤더 + per-row 복구 TextButton + multi-select 진입 어휘 추가.
2. `Out of scope` 의 "개별 row 를 아카이브 안에서 재분류하거나 복구하는 UI" + "bulk action 미구현" 두 줄 제거 또는 좁힘 (e.g. "Detail 단일 분류 변경 외 개별 row 액션 — multi-select bulk + per-row PRIORITY 복구 두 액션 한정").
3. Known gaps 의 `아카이브 안에서 "이 row 되살리기" / "모두 삭제" / bulk 재분류 미구현` 줄을 `(resolved YYYY-MM-DD, plan ...) ...` 마킹. 잔여 (예: "다른 status 로 복구 chooser") 는 잔여 항목으로 남김.
4. Change log 에 한 항목 append (날짜는 시스템 `date -u +%Y-%m-%d` 결과 — clock-discipline.md 준수).

## Task 8: ADB end-to-end verification [PARTIAL via this PR — empty branch only, see journey Change log 2026-04-27]

**Objective:** emulator-5554 에서 4 시나리오 확증.

**Steps:**
1. `adb shell pm clear com.smartnoti.app` 후 권한 grant + onboarding 통과.
2. IGNORE Category 1개 생성 (예: keyword `bulkclrkw`) → `am broadcast … --es title BulkClearTest_<n> --es body bulkclrkw --es force_status IGNORE` 으로 IGNORE row 5건 seed (debug-inject hook).
3. Settings 토글 ON → "무시됨 아카이브 열기" → 헤더 `보관 중 5건` + 두 신규 OutlinedButton 노출 확인.
4. 시나리오 A — `모두 지우기` 탭 → confirm dialog → `지우기` → snackbar `"무시된 알림 5건을 모두 지웠어요"` + EmptyState 복귀.
5. 시나리오 B — IGNORE row 5건 재 seed → `모두 PRIORITY 로 복구` 탭 → snackbar `"무시된 알림 5건을 PRIORITY 로 복구했어요"` → EmptyState (현 화면) + Home `즉시 N+5` StatPill 갱신 (PRIORITY tier 합산 확인).
6. 시나리오 C — IGNORE row 3건 재 seed → 첫 카드 long-press → AppBar `1개 선택됨` + `모두 지우기` + `취소` 노출 → 두 번째 카드 탭 → `2개 선택됨` → `모두 지우기` → confirm → snackbar `"선택한 알림 2건을 지웠어요"` → 헤더 `보관 중 1건`.
7. 시나리오 D — 마지막 row 의 `PRIORITY 로 복구` TextButton 탭 → snackbar `"이 알림을 PRIORITY 로 복구했어요"` → EmptyState + Home `즉시 N+1`.
8. 회귀 가드 — `Out of scope` 명시 항목 (group 화 / 검색 / 카테고리 chooser) 부재 재확인.

---

## Scope

**In scope:**
- 헤더 두 bulk action (전체 복구 / 전체 삭제) + 확인 다이얼로그 (삭제 한정)
- multi-select bar (카운트 + 삭제 + 취소) + long-press 진입
- per-row "PRIORITY 로 복구" TextButton (multi-select 비활성 시)
- 신규 repository / DAO 메서드 3종 (`restoreAllIgnoredToPriority`, `deleteAllIgnored`, `deleteIgnoredByIds`) + 단위 테스트
- snackbar confirm 카피 4종

**Out of scope:**
- 그룹화 / 검색 / 필터 (현재 plain list 유지)
- 복구 status chooser (PRIORITY 로 hardcode — Risks 의 open question)
- IGNORE row retention / 자동 삭제 정책 (별도 plan)
- Detail "분류 변경" 경로 변경 (Detail 의 단일 CTA 그대로)
- 5초 undo snackbar — 본 plan 의 confirm 다이얼로그 + snackbar 만으로 충분 (사용자 피드백으로 추가 결정)
- 전체 row 가 아닌 per-row "다른 Category 로 보내기" inline (Detail 경유 유지)

---

## Risks / open questions

1. **복구 시 status 가 PRIORITY 가 맞나, 아니면 사용자 chooser 인가?** Hidden 의 "모두 중요로 복구" 패턴과 어휘 일관성 위해 PRIORITY 로 가정. 사용자가 SILENT/DIGEST 로 옮기고 싶으면 Detail 의 "분류 변경" 경로 사용. → 사용자 결정 필요 시 본 plan implementation 전 확인.
2. **per-row 복구 TextButton 의 우발 발화** — confirm 없이 즉시 status flip 하므로, snackbar 만으로 충분한지 vs 5초 undo snackbar 까지 필요한지. priority-inbox bulk reclassify 도 confirm 없이 snackbar 만 사용 (그러나 그 액션은 비파괴). IGNORE → PRIORITY 복구는 비파괴이므로 snackbar 만으로 적정으로 가정.
3. **`deleteIgnoredByIds` 의 cascade 영향** — 현재 `notifications` 테이블 row 삭제는 `categories` / `rules` / `settings` 영향 없음. cascade 없으므로 안전 (FK 정의 없음). 만약 향후 row → reasonTag 분리 정규화가 들어오면 함께 cascade 확인.
4. **multi-select 시 long-press 이벤트가 `combinedClickable` 외 패턴으로 등장 가능성** — priority-inbox 의 ADB 테스트 노트 ("Compose `combinedClickable` long-press 는 `adb input swipe` 로 트리거 불가") 와 동일 제약. Task 8 의 시나리오 C 는 emulator GUI long-press 또는 uiautomator2 사용 권장.
5. **헤더 buttons 가 `SmartSurfaceCard` 안 vs ScreenHeader 옆** — 본 plan 은 카드 안을 가정. 시각 검증 후 ScreenHeader 옆으로 이동이 더 자연스러우면 implementation 단계에서 결정.

---

## Related journey

- [`docs/journeys/ignored-archive.md`](../journeys/ignored-archive.md) — Known gap "아카이브 안에서 'row 되살리기' / '모두 삭제' / bulk 재분류 미구현" 이 본 plan ship 시 resolved 표기되며 Out-of-scope / Observable steps 도 함께 갱신.
- 관련 패턴 참고: [`docs/journeys/hidden-inbox.md`](../journeys/hidden-inbox.md) (그룹 bulk action), [`docs/journeys/digest-inbox.md`](../journeys/digest-inbox.md) (group bulk repository 패턴), [`docs/journeys/priority-inbox.md`](../journeys/priority-inbox.md) (long-press multi-select state).
