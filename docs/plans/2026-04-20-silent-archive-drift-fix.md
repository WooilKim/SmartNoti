# Silent ARCHIVED 계약 드리프트 수정 — capture 배선 + Detail tray cancel

> **For Hermes:** Use the subagent-driven-development flow to implement this plan task-by-task (tests first).

**Goal:** 신규 SILENT 알림이 기본값으로 `SilentMode.ARCHIVED` 로 저장되어 Hidden 화면 "보관 중" 탭에 즉시 쌓이고, Detail 에서 "처리 완료로 표시" 를 누르면 DB 전이와 **동시에** 원본 tray 알림이 cancel 되어 사용자가 체감하는 "처리 = 알림창에서 사라짐" 기대와 일치하게 한다. 이로써 post-#115 `silent-auto-hide` journey 의 Observable steps 3·9 이 실제 동작과 어긋나지 않게 된다.

**Architecture:**
- `SmartNotiNotificationListenerService.processNotification` 가 `SourceNotificationRoutingPolicy.route(...)` 를 호출할 때 신규 SILENT 에 대해 `silentMode = SilentMode.ARCHIVED` 를 전달한다. 그러면 policy 가 이미 `cancelSourceNotification = false` 로 분기하도록 shipped 된 로직 (`#100`) 이 실제로 활성화되고, capture 경로에서 저장되는 `NotificationUiModel` 의 `silentMode` 도 ARCHIVED 로 영속화된다.
- Detail 의 "처리 완료로 표시" 는 현재 `repository.markSilentProcessed(id)` 만 호출하고 끝난다. Repository 에 `sourceEntryKeyForId(id): String?` 조회를 추가하고, listener service 가 노출한 프로세스 간 cancel 경로 (`SmartNotiNotificationListenerService.cancelSourceEntry(key)` static helper) 를 Detail 이 수행해 tray 원본을 제거한다. `NotificationListenerService#cancelNotification(key)` 는 live service 인스턴스에서만 호출 가능하므로 기존 `activeService` companion 변수를 재사용한다.
- 엔티티에 `sourceEntryKey: String?` 컬럼을 추가 (Room SCHEMA version bump). capture 시점에 `sbn.key` 를 저장해야 Detail 전이에서 어떤 tray entry 를 지울지 알 수 있다.

**Tech Stack:** Kotlin, Room (schema migration), Jetpack Compose, `NotificationListenerService`, Gradle unit tests.

---

## Product intent / assumptions

- #115 이후 journey 는 "ARCHIVED 는 tray 에 남고 Hidden '보관 중' 탭에 노출, PROCESSED 는 tray 에서 사라지고 '처리됨' 탭에 아카이브" 가 계약. 지금은 그 계약을 listener 가 지키지 않아 신규 SILENT 가 "처리됨" 탭으로 곧장 흘러 들어가고 있음.
- capture 배선이 들어오면 `silentSummaryJob` 의 ARCHIVED count 가 처음으로 실제 유저 알림에서 올라오기 시작한다. 요약 카피 ("보관 중 N건") 가 이미 ARCHIVED-only 전제로 작성되어 있으므로 추가 UX 결정 불필요.
- Detail 의 "처리 완료" 후 tray 가 그대로 남아 있으면 사용자는 "버튼이 먹통인가?" 로 오해한다 — 이 드리프트는 pure bug fix. 제품 결정 필요 없음.
- `sourceEntryKey` 컬럼 추가는 기존 레거시 row (null) 와 호환되게 설계. null 이면 Detail 전이는 DB flip 만 수행 (현재와 동일), 신규 row 부터 tray cancel 까지 연쇄된다.
- 실패 모드 허용: 사용자가 이미 tray 에서 해당 알림을 수동 swipe 한 뒤 Detail 에서 "처리 완료" 를 누르면 `cancelNotification(key)` 가 no-op. 문제 없음.

---

## Task 1: Failing test — listener passes `silentMode = ARCHIVED` on SILENT routing  [shipped via #125]

**Objective:** 드리프트를 테스트로 고정. `SmartNotiNotificationListenerService.processNotification` 이 SILENT decision 에 대해 `SourceNotificationRoutingPolicy.route` 를 `silentMode = SilentMode.ARCHIVED` 로 호출하는지 검증.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/notification/SilentArchivedCapturePathTest.kt`

**Steps:**
1. 테스트 용이성을 위해 routing 호출을 얇은 collaborator (`SilentCaptureRoutingSelector` 혹은 `NotificationRoutingGateway` 같은 내부 함수) 로 분리. 이 Task 의 테스트는 해당 collaborator 레벨에서 "SILENT decision + isPersistent=false + isProtected=false → `route(SILENT, …, silentMode = ARCHIVED)` 호출" 을 검증한다.
2. PROTECTED / PERSISTENT 경로 는 기존 동작 유지 — SILENT 이라도 route 가 `silentMode = null` (legacy) 로 호출되거나 아예 protected short-circuit 에 걸려 routing 자체를 skip 해야 함. 테스트로 고정.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.SilentArchivedCapturePathTest"` 로 빨간 상태 확인.

## Task 2: Implement — thread `silentMode` through the capture path  [shipped via #125 (bundled with T1) — `sourceEntryKey` column deferred to Task 3]

**Objective:** Task 1 을 통과시킨다. 신규 SILENT 캡처는 tray 에 남고 DB 에 `silentMode = ARCHIVED` 로 저장.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/notification/SourceNotificationRoutingPolicy.kt` (호출부만 — 분기 로직 자체는 #100 에서 이미 shipped)
- `app/src/main/java/com/smartnoti/app/data/local/NotificationEntity.kt` + `NotificationDao.kt` + Room 마이그레이션 (SCHEMA_VERSION 증가) 로 `sourceEntryKey: String?` 컬럼 추가.
- `app/src/main/java/com/smartnoti/app/data/local/NotificationEntityMapper.kt` — capture 시 `sbn.key` 를 저장, 읽기 시 UI 모델에 전달.
- `app/src/main/java/com/smartnoti/app/domain/model/NotificationUiModel.kt` — `sourceEntryKey: String?` 필드 (persistence-facing, UI 에서는 직접 노출하지 않음).

**Steps:**
1. 라우팅 호출 사이트에서 decision 이 SILENT 이고 protected 가 아니면 `silentMode = SilentMode.ARCHIVED` 를 전달. protected 경로는 그대로 short-circuit (변경 없음).
2. 저장 시 `notification = baseNotification.copy(silentMode = SilentMode.ARCHIVED)` 를 명시 — classifier → processor 체인이 `silentMode` 를 이미 set 하고 있다면 그대로 사용, 아니면 listener 에서 주입.
3. Room 마이그레이션 작성: `ALTER TABLE notifications ADD COLUMN sourceEntryKey TEXT DEFAULT NULL`.
4. 전체 테스트 재실행 — Task 1 초록 + 기존 `SourceNotificationRoutingPolicyTest` / `NotificationRepositoryTest` / `HiddenGroupsSilentModeFilterTest` 영향 없음 확인.

## Task 3: Extend `markSilentProcessed` to chain tray cancel from Detail  [IN PROGRESS via PR #126]

**Objective:** Detail 의 "처리 완료로 표시" 가 DB flip 에 더해 원본 tray 알림까지 제거해 journey Observable step 9 와 일치하게 한다.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/local/NotificationRepository.kt` — `sourceEntryKeyForId(id): String?` suspend 조회 추가 (`markSilentProcessed` 는 현재 반환값 `Boolean` 그대로 유지하고, cancel 연계는 UI 측에서 결정하게 한다 — repository 를 Android framework 에 결합시키지 않기 위해).
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` — `companion object` 에 `fun cancelSourceEntryIfConnected(key: String): Boolean` 추가 (live listener instance 가 있으면 `cancelNotification(key)` 를 main dispatcher 에서 실행, 없으면 false).
- `app/src/main/java/com/smartnoti/app/ui/screens/detail/NotificationDetailScreen.kt` — 버튼 `onClick` 에서 `repository.markSilentProcessed(id)` → true 면 `repository.sourceEntryKeyForId(id)?.let { SmartNotiNotificationListenerService.cancelSourceEntryIfConnected(it) }` 체인.
- 테스트: `app/src/test/java/com/smartnoti/app/data/local/NotificationRepositoryTest.kt` 에 `sourceEntryKeyForId` round-trip 케이스 추가. `NotificationFeedbackPolicyTest` 는 변경 없음 (in-memory 전이는 그대로).

**Steps:**
1. 테스트 먼저 — Repository 에 `sourceEntryKey` 를 동반해 save 후 `sourceEntryKeyForId(id)` 가 해당 값을 반환하는지 확인. 기존 null 레거시 row 는 null 반환.
2. Repository helper 구현.
3. Listener service companion helper 구현 (`activeService` 재사용). 이미 `triggerOnboardingBootstrapIfConnected` 가 같은 패턴을 사용 중이라 그 style 을 따른다.
4. Detail 의 `scope.launch { ... }` 내부에서 순서대로 실행: `markSilentProcessed` → 성공 시 `sourceEntryKeyForId` → 있으면 `cancelSourceEntryIfConnected`. 실패해도 DB 전이는 보존 (idempotent).
5. ADB 검증은 Task 5 에 통합.

## Task 4: Journey Change log entry

**Objective:** shipped 이후 journey 계약이 실제 동작과 다시 일치하게 한다.

**Files:**
- `docs/journeys/silent-auto-hide.md` — Observable step 3 의 "현재 capture 경로는 기본 null … 후속 Task 에서 완결한다" 문구를 shipped 반영으로 갱신. Known gaps 의 두 bullet ("Capture 경로의 ARCHIVED 기본값 미배선", "Detail 전이의 tray cancel 부재") 를 Change log 로 이동. `last-verified` 는 실제 verification recipe 를 돌린 날짜로만 갱신 (docs-sync 규칙).
- `docs/journeys/hidden-inbox.md` — Known gap "Capture 경로의 ARCHIVED 기본값 미배선 여파" 를 해소로 표기, Change log 에 링크.

**Steps:**
1. 두 journey 모두 Observable steps / Exit state 본문은 이미 post-fix 상태로 작성되어 있으므로 Known gaps / Change log 만 편집.
2. `last-verified` 는 Task 5 의 recipe 가 실제로 PASS 난 이후에만 갱신.

## Task 5: Verification recipe

**Steps:**
```bash
# A. 신규 SILENT 가 tray 에 남고 "보관 중" 탭으로 라우팅되는지
adb shell cmd notification post -S bigtext -t "Promo" SilentArchiveSmoke "오늘만 30% 할인"
adb shell dumpsys notification --noredact | grep SilentArchiveSmoke
# expected: 원본 알림 entry 가 tray 에 남아 있음 (이전에는 cancel 되어 사라졌음)

adb shell am force-stop com.smartnoti.app
adb shell am start -n com.smartnoti.app/.MainActivity \
  -e com.smartnoti.app.extra.DEEP_LINK_ROUTE hidden
# expected: 기본 "보관 중" 탭에 SilentArchiveSmoke 카드가 표시

# B. Detail "처리 완료로 표시" 가 tray 에서도 원본을 제거하는지
# (Hidden → 카드 탭 → Detail → "처리 완료로 표시" 버튼 tap)
adb shell dumpsys notification --noredact | grep SilentArchiveSmoke
# expected: 원본 알림이 tray 에서 사라짐. Hidden "처리됨" 탭에 row 이동.
```

## Task 6: Self-review + PR

- 모든 단위 테스트 초록.
- Room 마이그레이션 smoke — 기존 `SmartNotiDatabase` 버전이 올라갔는지, `fallbackToDestructiveMigration` 가 켜져 있으면 migration spec 작성 여부 판단.
- PR 제목: `fix(silent): thread ARCHIVED through capture + chain tray cancel on mark-processed`.

---

## Scope

### In
- Listener service SILENT 경로의 `silentMode = ARCHIVED` 전달.
- `NotificationEntity.sourceEntryKey` 컬럼 + 마이그레이션.
- Detail "처리 완료로 표시" 의 tray cancel 체인.
- 두 journey 문서 Known gap / Change log 갱신.

### Out
- PROTECTED / PERSISTENT SILENT 경로 변경 — 그대로 legacy.
- Swipe-dismiss 를 PROCESSED 로 자동 해석 — 별도 plan (현재 `silent-archive-vs-process-split` open question).
- 기존 legacy null silentMode row 마이그레이션 — 별도 Known gap 으로 유지.
- Hidden 화면 bulk "모두 처리됨" 액션 — 별도 plan 후보.
- Repository 를 Android framework 에 결합 (cancel 은 listener service 책임 유지).

---

## Risks / open questions

- **Live listener instance 부재 시점:** Detail 전이가 listener service 가 disconnect 된 상태에서 실행되면 `cancelSourceEntryIfConnected` 가 false 를 반환한다. 이 경우 tray 원본은 남아 있게 됨. → 의도된 동작 (best-effort). Known gap 으로 기록하고 필요 시 `NotificationManagerCompat.cancel` fallback 은 불가 (listener key 는 본인이 post 한 알림에만 유효하지 않음).
- **Room migration 과 destructive fallback:** 현재 `SmartNotiDatabase` 가 `fallbackToDestructiveMigration` 인지 확인 후, destructive 이면 컬럼 추가만으로 충분. 명시적 `Migration` 객체가 있으면 version bump 에 맞춰 `ADD COLUMN` 문 작성 필수.
- **sourceEntryKey 안정성:** Android 의 `StatusBarNotification.key` 는 원본 앱이 같은 slot 으로 업데이트해도 동일한 값을 유지하므로 tray cancel 키로 안전. 단, 앱이 이미 해당 slot 을 다른 id 로 교체했다면 cancel 이 엉뚱한 대상을 지울 위험은 없음 (key 는 `userId|packageName|id|tag` 로 고정 식별).
- **Test 경계 분리:** Task 1 의 collaborator 추출을 어디까지 할지는 implementer 가 결정. listener service 전체를 테스트로 감싸기보다는 얇은 pure 함수로 분리하는 것을 권장 — `NotificationCaptureProcessor` 근처에 라우팅 결정만 담당하는 helper 를 두는 방식.

## Related journey

- Primary: [silent-auto-hide](../journeys/silent-auto-hide.md) — Observable steps 3 / 9 의 Known gap 을 해소.
- Secondary: [hidden-inbox](../journeys/hidden-inbox.md) — "보관 중" 탭이 실제로 채워지기 시작.
- 선행 plan: [silent-archive-vs-process-split](2026-04-21-silent-archive-vs-process-split.md) Task 3 에서 예고한 "tray cancel 연계는 후속" 약속을 이 plan 이 마무리.

## Decision log

- 2026-04-20: gap-planner 가 #115 직후 journey Known gaps 에 오른 두 드리프트를 묶어 drafted. Repository 를 framework 와 결합하지 않고 listener service companion helper 로 cancel 경로를 분리하는 방향 선택.
