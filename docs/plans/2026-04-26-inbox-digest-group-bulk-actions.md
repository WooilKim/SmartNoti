---
status: shipped
shipped: 2026-04-26
superseded-by: docs/journeys/inbox-unified.md
---

# Inbox Digest sub-tab — Group bulk actions (모두 중요로 변경 / 모두 지우기)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 정리함의 `Digest` 서브탭에서 앱별 그룹 카드를 펼친 사용자가, 그 그룹 안의 모든 DIGEST 알림을 **한 번에** 다른 분류로 옮기거나 한꺼번에 지울 수 있다. 구체적으로 `DigestGroupCard` (Digest 서브탭) 의 expand 영역 끝에 `보관 중` / `처리됨` 탭에서 이미 동작하는 것과 동일한 패턴의 `bulkActions` 행이 노출된다 — 두 OutlinedButton: **`모두 중요로 변경`** (그룹 안 모든 row 의 status 를 PRIORITY 로 일괄 갱신, `reasonTags` 에 `사용자 분류` dedup append) + **`모두 지우기`** (그룹 안 모든 DIGEST row 를 DB 에서 삭제). 두 액션 모두 그룹 단위 단일 op (개별 row N 회 호출이 아니라 신규 repository 단일 query). 액션 후 `observeDigestGroupsFiltered` flow 가 자동으로 해당 그룹을 list 에서 제거 (`모두 지우기`) 하거나, status 가 PRIORITY 로 바뀐 row 들이 Digest 서브탭에서 사라지고 Home `검토 대기` count 가 즉시 증가 (`모두 중요로 변경`). 시스템 tray 의 원본 알림 / replacement 알림 routing 은 본 plan 의 scope 밖 (DB row + 후속 routing 만 영향, `digest-suppression` 의 replacement contract 는 그대로).

**Architecture:** (a) `NotificationRepository` 에 두 신규 suspend 메서드 추가 — `restoreDigestToPriorityByPackage(packageName: String): Int` 와 `deleteDigestByPackage(packageName: String): Int` — 기존 `restoreSilentToPriorityByPackage` / `deleteSilentByPackage` 의 SILENT 분기를 DIGEST 분기로 mirror. DAO 에는 대응 `@Query("UPDATE notifications SET status = :priority, reasonTags = ... WHERE packageName = :pkg AND status = 'DIGEST'")` + `@Query("DELETE FROM notifications WHERE packageName = :pkg AND status = 'DIGEST'")` 메서드 추가. reasonTags 의 `사용자 분류` dedup append 는 SQL 단의 `CASE` 식 대신 in-Kotlin transaction (load → mutate → upsert) 으로 처리해 dedup 로직을 단순화한다 (Detail 단일 row 경로의 `ApplyCategoryActionToNotificationUseCase` 와 동일한 dedup 의미). (b) `DigestScreen` 의 `items(groups, ...)` 호출부에서 `DigestGroupCard(model = group, onNotificationClick = ..., bulkActions = { ... })` 로 `bulkActions` slot 을 채우고, `Hidden` 의 동일 패턴을 그대로 베껴 두 OutlinedButton 을 `Row(weight 1f / 1f)` 로 배치. 두 onClick 콜백은 `scope.launch { repository.<method>(group.items.first().packageName) }` 로 위임 — `DigestGroupUiModel` 도 `HiddenGroupUiModel` 과 동일하게 packageName 키 기반 그룹이므로 `group.items.first().packageName` 가 그룹 키와 동치. (c) Inbox-unified Digest 서브탭의 진입 경로는 `InboxScreen` 이 `DigestScreen` 본체를 그대로 위임하므로 별도 wiring 불필요 — DigestScreen 한 곳만 고치면 inbox-unified 와 legacy `Routes.Digest` deeplink 두 진입 경로 모두에서 동작. (d) DigestGroupCard 의 `collapsible=false` (Digest 서브탭의 default) 모드에서도 `bulkActions` 가 렌더되도록 컴포넌트의 기존 분기 (`if (bulkActions != null) { bulkActions() }`) 가 이미 collapsible 와 무관하게 작동함을 grep + 단위 테스트로 확인.

**Tech Stack:** Kotlin, Room (DAO + Repository), Jetpack Compose Material3, Gradle JVM unit tests + Robolectric (DAO).

---

## Product intent / assumptions

- 본 plan 은 다음 세 가지를 plan 단계에서 고정한다 (implementer 추측 금지):
  - **그룹 단위 단일 op**: bulk 액션은 row N 회 update/delete 가 아니라 **packageName 기준 단일 SQL query**. 부분 실패 시 transaction 자체가 throw — 재시도는 사용자가 다시 탭. 단일 row 경로의 `ApplyCategoryActionToNotificationUseCase` 가 row 한 건의 status flip 만 담당하는 것과 의도적으로 분리된 layer (`PassthroughReviewReclassifyDispatcher` 가 단일 row 경로를 담당하는 것과 동일 패턴).
  - **`모두 중요로 변경` 의 의미**: 선택된 그룹의 모든 DIGEST row 의 `status` → `PRIORITY`, `reasonTags` 에 `사용자 분류` 라벨을 dedup append. **Rule / Category 는 만들지 않음** — 즉, "다음에 같은 앱이 알림을 보내면" 자동으로 PRIORITY 로 가지는 않는다. 이는 priority-inbox 의 bulk reclassify 와 동일 정책 (`createRule = false` 와 의미 일치). 사용자가 영구 룰을 원하면 Detail 의 `분류 변경` 시트 경로 (rules-feedback-loop) 를 사용한다. 카피가 "모두 중요로 변경" 이지 "모두 중요로 분류" 가 아닌 이유.
  - **`모두 지우기` 의 의미**: 선택된 그룹의 모든 DIGEST row 를 **DB 에서 물리 삭제**. SILENT 의 `deleteSilentByPackage` 와 동일 의미. tray 원본 / replacement 알림은 별도 — 본 plan 은 row 만 다룬다 (`digest-suppression` 의 replacement notification 은 그 자체로 lifecycle 을 가지므로 row 삭제와 독립). 사용자가 "지우기" 라고 부르는 직관에 맞춰 soft-delete (status=IGNORE 전환) 가 아닌 hard delete 를 선택 — Hidden 의 동일 카피와 일관성 유지.
- **남는 open question (user 결정 필요, 본 plan 은 default 안 채택 후 PR 본문에서 confirm)**:
  - **확인 다이얼로그 유무**: `모두 지우기` 는 N 건을 한 번에 hard-delete 하므로 destructive. Hidden 측은 그룹별 `모두 지우기` 에 confirmation 없이 즉시 삭제 (헤더의 `전체 숨긴 알림 모두 지우기` 만 `pendingClearAll` AlertDialog 로 가드). Digest 측도 **그룹별 단위는 confirmation 없음** 으로 시작 — Hidden 과의 UX 일관성 우선. 사용자가 PR 리뷰에서 "그룹별도 확인 다이얼로그가 필요하다" 고 결정하면 즉시 추가 가능 (작은 변경).
  - **카피 단어 선택**: `모두 중요로 변경` vs `모두 중요로 보내기` vs `모두 중요로 옮기기`. 본 plan 은 `모두 중요로 변경` 을 default 로 채택 (Hidden 의 `모두 중요로 복구` 와 비슷한 동사형 + Detail 단일 경로의 "분류 변경" 카피와 톤 일치). PR 본문에서 confirm.
  - **`모두 무시` 액션 추가 여부**: `digest-inbox.md` 의 원래 Known gap 카피는 `모두 무시` 도 함께 언급하지만 IGNORE 는 destructive (row 자체가 기본 뷰에서 영구 사라짐 + Settings 의 `showIgnoredArchive` 토글이 켜진 사용자만 archive 에서 볼 수 있음) 이고, 현재 Hidden 측에도 `모두 무시` bulk 는 없다. 본 plan 은 `모두 중요로 변경` + `모두 지우기` 두 개로 한정 — `모두 무시` 는 별도 plan 후속 (IGNORE Category 자동 매핑 또는 reasonTags 보강 등 추가 결정이 필요). 단순한 3 버튼 행 (`모두 중요로 / 모두 지우기 / 모두 무시`) 도 가능하지만 row 너비에 무리.

---

## Task 1: Add failing tests for `restoreDigestToPriorityByPackage` + `deleteDigestByPackage` [IN PROGRESS via PR #392]

**Objective:** 두 신규 repository 메서드의 계약을 unit + DAO 테스트로 고정.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/data/local/NotificationRepositoryDigestBulkActionsTest.kt`
- 보강: 기존 DAO 단위 테스트 (`NotificationDaoTest.kt` 류) 가 있다면 같은 파일에 케이스 추가

**Steps:**
1. 기존 `restoreSilentToPriorityByPackage` / `deleteSilentByPackage` 의 테스트가 어디에 있는지 grep — 동일 파일에 mirror 케이스 추가가 가장 깔끔.
2. 케이스:
   - **happy path (restore)**: DIGEST 3건 + SILENT 1건 (같은 packageName) seed → `restoreDigestToPriorityByPackage(pkg)` → 반환값 3, DIGEST 3건 모두 status=PRIORITY 로 갱신, reasonTags 에 `사용자 분류` 1회 append (이미 있던 row 의 reasonTags 는 중복 append 되지 않음 — `사용자 분류` 가 이미 있으면 그대로). SILENT 1건은 불변.
   - **happy path (delete)**: DIGEST 3건 + SILENT 1건 (같은 packageName) seed → `deleteDigestByPackage(pkg)` → 반환값 3, DIGEST 3건이 DB 에서 사라짐, SILENT 1건은 보존.
   - **다른 packageName 격리**: 같은 status 의 다른 packageName row 는 어느 메서드에서도 영향 받지 않음.
   - **빈 그룹**: 해당 packageName + DIGEST row 가 0건이면 두 메서드 모두 반환값 0, 다른 row 변경 없음.
   - **`사용자 분류` dedup**: restore 시 reasonTags 에 `사용자 분류` 가 이미 있던 row 는 한 번만 가지고, 없던 row 는 새로 append 받는다 (이중 dedup 검증).
3. `./gradlew :app:testDebugUnitTest --tests "*.NotificationRepositoryDigestBulkActionsTest"` → RED 확인.

## Task 2: Implement `restoreDigestToPriorityByPackage` + `deleteDigestByPackage` [IN PROGRESS via PR #392]

**Objective:** Task 1 의 테스트를 GREEN 으로.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/local/NotificationRepository.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/data/local/NotificationDao.kt`

**Steps:**
1. `restoreDigestToPriorityByPackage(packageName: String): Int`:
   - 기존 `restoreSilentToPriorityByPackage` 의 구현을 참조 — DAO 에 `@Query("SELECT * FROM notifications WHERE packageName = :pkg AND status = 'DIGEST'")` 가 없다면 `loadSilentByPackage` 류와 mirror 한 `loadDigestByPackage` 추가 (혹은 기존 `loadByPackageAndStatus(pkg, status)` 를 재사용).
   - 로드한 row 들의 `reasonTags` 에 `사용자 분류` 라벨이 없으면 append (단순 `if (!list.contains("사용자 분류")) list + "사용자 분류"`), `status` = `PRIORITY` 로 copy.
   - DAO 의 `upsertAll` 또는 `update` 로 일괄 persist. 반환값은 변경된 row 수.
2. `deleteDigestByPackage(packageName: String): Int`:
   - DAO 에 `@Query("DELETE FROM notifications WHERE packageName = :pkg AND status = 'DIGEST'")` 추가. 반환 Int (DELETE affected rows).
   - Repository 메서드는 단순 위임.
3. 두 메서드 모두 기존 `*ByPackage` 가 위치한 파일/줄과 인접하게 배치 (`SilentByPackage` 메서드 바로 아래) 해서 reviewer 가 mirror 임을 한눈에 알 수 있게.
4. `./gradlew :app:testDebugUnitTest --tests "*.NotificationRepositoryDigestBulkActionsTest"` → GREEN.
5. 회귀 가드: 기존 SILENT 테스트 (`restoreSilentToPriorityByPackage` / `deleteSilentByPackage` 테스트가 있다면) 가 여전히 GREEN 인지 `./gradlew :app:testDebugUnitTest --tests "*NotificationRepository*"` 로 확인.

## Task 3: Wire `bulkActions` slot into DigestScreen

**Objective:** Digest 서브탭의 `DigestGroupCard` 가 그룹 헤더 아래에 두 bulk OutlinedButton 을 노출.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/digest/DigestScreen.kt`

**Steps:**
1. `DigestScreen` 에 `val scope = rememberCoroutineScope()` 추가 (현재 없음).
2. `items(groups, key = { it.id }) { group -> DigestGroupCard(...) }` 호출부에 `bulkActions = { ... }` slot 채움. Hidden 의 `HiddenGroupCardWithBulkActions` 를 참조해 동일 컴포지션 (`Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp))` + 두 `OutlinedButton(modifier = Modifier.weight(1f))`) 사용.
3. 두 onClick:
   - `모두 중요로 변경` → `scope.launch { repository.restoreDigestToPriorityByPackage(group.items.first().packageName) }`
   - `모두 지우기` → `scope.launch { repository.deleteDigestByPackage(group.items.first().packageName) }`
4. `DigestGroupCard` 호출부에서 `collapsible = ?` 파라미터의 현재값 확인 — Digest 서브탭은 default (현재 `collapsible` default 가 false) 로 두며 bulkActions 만 추가. `bulkActions` 의 렌더는 `collapsible` 와 무관하게 항상 (`if (bulkActions != null)`) 동작함을 컴포넌트 코드로 재확인.
5. `./gradlew :app:assembleDebug` 빌드 통과 확인.

## Task 4: Add a UI-level guard test for bulkActions visibility

**Objective:** Digest 서브탭의 `bulkActions` slot 이 미래 PR 에서 실수로 다시 빠지지 않도록 컴포넌트 호출부 합성 검증을 단위 테스트로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/ui/screens/digest/DigestScreenBulkActionsWiringTest.kt` (Robolectric 또는 pure Compose UI test 가 가능한 환경에 따라)
- 또는 대안 (실용적): `app/src/test/java/com/smartnoti/app/ui/screens/digest/DigestGroupCardBulkActionsContractTest.kt` — `DigestGroupCard` 가 `bulkActions != null` 일 때 정확히 렌더되고 (`fillMaxWidth` 의 두 button 라벨이 발견되는지) `bulkActions == null` 일 때 렌더되지 않는지 두 케이스 검증.

**Steps:**
1. CI 가 Compose UI test (`createComposeRule()`) 를 지원한다면 `DigestGroupCardBulkActionsContractTest` 로 lambda 가 호출되는지 + 라벨이 보이는지 검증.
2. CI 가 그것을 지원하지 않으면 (이전 plan `2026-04-25-android-queries-package-visibility` 가 `connectedDebugAndroidTest` 부재로 Option B 채택했음을 참조), pure Kotlin 합성 추출 — 예: `DigestScreenBulkActionsAdapter` 라는 작은 helper 함수 (`fun buildDigestGroupBulkActions(group, onRestoreAll, onDeleteAll): DigestGroupBulkActions`) 를 분리하고 그 데이터 객체 (`packageName`, `restoreLabel`, `deleteLabel`) 를 단위 테스트로 검증.
3. `./gradlew :app:testDebugUnitTest --tests "*DigestScreenBulkActionsWiringTest*"` 또는 `*DigestGroupCardBulkActionsContractTest*` GREEN.

## Task 5: Update journey docs + Known gap markers

**Files:**
- `docs/journeys/inbox-unified.md`
- `docs/journeys/digest-inbox.md` (deprecated 이지만 여전히 참조 — Change log + Known gap 마킹)

**Steps:**
1. `inbox-unified.md` Observable steps 6 에 sub-step 추가: Digest 서브탭의 expanded preview 영역 끝에 `모두 중요로 변경` / `모두 지우기` 두 OutlinedButton 이 노출되며, 탭 시 그룹 안 모든 DIGEST row 가 PRIORITY 로 갱신되거나 DB 에서 삭제되어 list 에서 사라진다.
2. `inbox-unified.md` Out of scope 의 `통합 탭 내 bulk action 확장 ...` bullet 을 갱신 — Digest 서브탭의 그룹별 bulk 두 액션이 추가되었으므로, 본 bullet 의 "현재 그룹별로만" 부분을 "그룹별 (Digest / 보관 중 / 처리됨 모두)" 로 좁히고, 진정한 Out-of-scope (`전체 Digest 삭제`, `전체 SILENT 복구` 같은 cross-group bulk) 를 명시.
3. `inbox-unified.md` Code pointers 에 `data/local/NotificationRepository#restoreDigestToPriorityByPackage` + `data/local/NotificationRepository#deleteDigestByPackage` 추가.
4. `inbox-unified.md` Change log 에 새 row 1줄 (구현 PR 머지 시 commit 해시 채움).
5. `digest-inbox.md` (deprecated) 의 Known gaps 의 `그룹별 "모두 중요로 변경" / "모두 무시" 액션 부재.` bullet 앞에 resolved marker 추가: `(resolved YYYY-MM-DD, plan 2026-04-26-inbox-digest-group-bulk-actions; "모두 무시" 는 별도 plan 으로 위임)` — Bullet 본문은 보존, 끝에 `→ plan: docs/plans/2026-04-26-inbox-digest-group-bulk-actions.md` 링크 추가. Date 는 `date -u +%Y-%m-%d` 로 ship 시점에 stamp.
6. `digest-inbox.md` Change log 에도 row 1줄 추가 (deprecated 문서이지만 contract 의 일부 — Routes.Digest deeplink 가 동일 화면을 호스팅하므로 history 가 유효).

## Task 6: ADB end-to-end verification

**Objective:** 실기기 emulator-5554 에서 두 액션 모두 동작 확인 + 기존 Hidden 측 bulk action 회귀 없음 가드.

**Steps:**
```bash
# 사전: emulator-5554 가 실행 중, SmartNoti debug APK 설치, listener 권한 grant.

# 1. 같은 packageName (`com.android.shell`) 으로 DIGEST 분류될 알림 4건 seed
#    (현재 baseline 의 onboarding KEYWORD 룰 광고/프로모션/쿠폰/세일/특가/이벤트/혜택 → DIGEST 활용)
for i in 1 2 3 4; do
  adb -s emulator-5554 shell cmd notification post -S bigtext \
    -t "광고" "BulkDigTest_${i}" "${i}번째 광고 알림"
done

# 2. 다른 packageName 의 DIGEST 1건 seed (격리 검증용 — testnotifier)
#    (또는 com.android.shell 외 다른 sender 라벨로 구분 — 이번 sweep 은 단순화)

# 3. 앱 실행 → BottomNav `정리함` 탭 → Digest 서브탭 (default 선택)
adb -s emulator-5554 shell am force-stop com.smartnoti.app
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity

# 4. UI dump 로 그룹 카드 + 두 OutlinedButton 라벨 노출 확인
adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null
adb -s emulator-5554 exec-out cat /sdcard/ui.xml | grep -oE '모두 중요로 변경|모두 지우기'
# expected: 두 라벨 모두 hit

# 5. `모두 중요로 변경` 탭 → 기대값:
#    - Digest 서브탭에서 `광고 4건` 그룹이 사라짐
#    - Digest 서브탭 카운트 4 감소
#    - Home `검토 대기` 카드 / `즉시` StatPill 가 4 증가
#    - DB row 의 status 가 PRIORITY 로 갱신, reasonTags 에 `사용자 분류` 포함
adb -s emulator-5554 exec-out run-as com.smartnoti.app sqlite3 \
  databases/smartnoti.db \
  "SELECT id, status, reasonTags FROM notifications WHERE title LIKE 'BulkDigTest_%';"

# 6. baseline 복원 후 두 번째 seed → `모두 지우기` 탭 → 기대값:
#    - Digest 서브탭에서 `광고 N건` 그룹이 사라짐
#    - DB row 가 사라짐
adb -s emulator-5554 exec-out run-as com.smartnoti.app sqlite3 \
  databases/smartnoti.db \
  "SELECT count(*) FROM notifications WHERE title LIKE 'BulkDigTest_%';"
# expected: 0

# 7. 회귀 가드 — Hidden 보관 중 / 처리됨 탭의 기존 bulk action 이 여전히 동작하는지 1회 smoke
#    (별도 SILENT seed → `보관 중` 탭 → 그룹 헤더 expand → `모두 중요로 복구` 탭)

# 8. baseline 복원 — sqlite3 DELETE 또는 디버그 reset 으로 검증용 row 삭제.
```

---

## Scope

**In:**
- 신규 `NotificationRepository.restoreDigestToPriorityByPackage` + `deleteDigestByPackage` + 대응 DAO 쿼리.
- `NotificationRepositoryDigestBulkActionsTest` (5 케이스 — 두 happy + 격리 + 빈 그룹 + dedup).
- `DigestScreen` 의 `DigestGroupCard` 호출부에 `bulkActions` slot 채움 (`Row(weight 1f / 1f)` 두 OutlinedButton).
- 가드 테스트 (Compose UI test 또는 pure helper 분리).
- `inbox-unified.md` Observable step 6 갱신 + Out of scope bullet 갱신 + Code pointers + Change log row.
- `digest-inbox.md` (deprecated) Known gap resolved marker + Change log row.

**Out:**
- "모두 무시" (IGNORE) bulk 액션 — IGNORE Category 자동 매핑 / reasonTags 보강 등 추가 결정이 필요 (별도 plan 후속).
- Bulk action 의 undo snackbar — 단일 row 경로 (Detail "분류 변경") 도 미지원 + Hidden 의 동일 bulk 도 미지원, 일관성 우선.
- `전체 Digest 삭제` / `전체 SILENT 복구` 같은 cross-group bulk — Out-of-scope (별도 plan 가능).
- Digest 서브탭에서 long-press → multi-select (priority-inbox 의 패턴) — 그룹 단위 bulk 가 충분한 leverage 를 주므로 본 plan 은 그룹 단위에 한정. 사용자가 "여러 그룹의 일부 row 만 골라 한꺼번에 처리하고 싶다" 같은 use case 가 떠오르면 별도 plan.
- 시스템 tray 의 원본 알림 / replacement 알림 cancel — `digest-suppression` 의 replacement contract 는 본 plan 과 독립 (이미 게시된 replacement 알림은 user dismiss 또는 timeout 까지 그대로).
- `DigestGroupCard` 의 collapsible 모드 변경 (Digest 서브탭은 default 로 expanded 인지, collapsed 인지 별도 결정) — 본 plan 은 현재 default 를 그대로 사용.

---

## Risks / open questions

- **확인 다이얼로그 유무 (그룹별 `모두 지우기`)** — Hidden 측은 그룹별 bulk delete 에 confirmation 없이 즉시 삭제. Digest 측도 동일 정책으로 시작 — PR 본문에서 user 가 "그룹별도 confirmation 다이얼로그 필요" 로 결정하면 즉시 추가 가능. **본 plan 은 default = no confirmation 으로 가구현**. (Product intent / assumptions 절 참고.)
- **카피 단어 선택 (`모두 중요로 변경` vs `모두 중요로 보내기` vs `모두 중요로 옮기기`)** — 본 plan 은 `모두 중요로 변경` 을 default. PR 본문에서 confirm. (Hidden 의 `모두 중요로 복구` 와 의도적으로 다름 — Digest 는 "복구" 가 아니라 "재분류" 의미가 더 정확.)
- **`모두 무시` 액션 추가 여부** — 본 plan 에서 의도적으로 제외. IGNORE Category 매핑 + Settings 의 `showIgnoredArchive` 토글과의 상호작용을 더 명확히 결정한 후 별도 plan 으로 추가. PR 본문에서 user 가 "본 plan 에서 함께 ship" 으로 결정하면 Task 3 / Task 5 만 확장.
- **reasonTags `사용자 분류` dedup 의미** — `ApplyCategoryActionToNotificationUseCase` (Detail 단일 row 경로) 가 사용한 dedup append 와 의미적으로 동일하게 맞춤. Detail 측이 향후 다른 reasonTag 라벨로 바뀌면 (예: `벌크 재분류`) 본 plan 의 라벨도 동기 갱신이 필요 — 현 시점에서는 단일 라벨 (`사용자 분류`) 로 통일.
- **Replacement 알림 lifecycle** — DIGEST 분류 시 `digest-suppression` 의 replacement 알림 (channel `smartnoti_replacement_digest_*`) 이 tray 에 게시되어 있을 수 있음. `restoreDigestToPriorityByPackage` 후에도 replacement 알림은 그대로 남는다 (본 plan 은 row 만 다룸 — replacement 의 cancel 은 별도 lifecycle). 이 점이 사용자 혼란을 일으키지 않는지 ADB 검증에서 확인 — 만약 큰 회귀 신호가 있으면 implementer 가 PR 본문에서 별도 follow-up plan 으로 보고.
- **빌드 회귀 가드** — `NotificationCard` / `DigestGroupCard` 의 시그니처는 본 plan 에서 변경하지 않음 (slot 호출만 추가). 기존 호출부에 대한 영향 없음을 grep 으로 사전 확인.
- **DAO 의 string 매칭 (`status = 'DIGEST'`)** — 기존 `restoreSilentToPriorityByPackage` 의 DAO 쿼리가 enum string 매칭을 어떻게 처리하는지 (Room TypeConverter 또는 직접 `ordinal` 비교) 를 implementer 가 grep 으로 mirror — 새 쿼리도 동일 방식으로 작성. 불일치 시 `NotificationStatus` enum 의 storage 표현이 잘못 매핑되어 0건 affected 가 될 수 있다.

---

## Related journey

- [`docs/journeys/inbox-unified.md`](../journeys/inbox-unified.md) — 본 plan 의 Observable step 6 와 Code pointers 를 갱신.
- [`docs/journeys/digest-inbox.md`](../journeys/digest-inbox.md) (deprecated) — 본 plan 이 해소하는 Known gap: `그룹별 "모두 중요로 변경" / "모두 무시" 액션 부재.` (`모두 무시` 는 별도 plan 후속.) Routes.Digest deeplink 의 Standalone 진입 경로도 동일 컴포넌트를 공유하므로 본 plan 변경이 deprecated journey 의 contract 에도 영향.
