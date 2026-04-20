# Silent Source Notification Migration Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** SmartNoti 가 이미 SILENT 로 분류해 DB 에 저장한 알림 중, 아직 시스템 알림센터에 원본이 남아 있는 것들을 리스너가 접속하는 시점에 한 번 쓸어내 tray 에서 제거한다. 이렇게 하면 [silent-auto-hide](../journeys/silent-auto-hide.md) 가 shipped 되기 전에 캡처된 SILENT 원본이 자연 소모를 기다리지 않고도 깔끔하게 정리된다.

**Architecture:** 기존 `SmartNotiNotificationListenerService.onListenerConnected()` 경로에 one-shot sweep job 을 추가한다. `activeNotifications` 를 `NotificationRepository.observeAll().first()` 의 SILENT row 와 `packageName + contentSignature` 로 매칭해 `cancelNotification(sbn.key)` 를 호출한다. [protected-source-notifications](../journeys/protected-source-notifications.md) 의 `ProtectedSourceNotificationDetector` 는 sweep 단계에서도 동일하게 적용해 media / call / foreground-service 원본은 건너뛴다. onboarding bootstrap 과는 책임이 다르므로(bootstrap 은 **캡처**, migration 은 **cancel**) 별도 coroutine job 으로 분리한다.

**Tech Stack:** Kotlin, NotificationListenerService, Room, Gradle unit tests, Claude Code implementation.

---

## Product intent / assumptions

- 이 sweep 은 **모든 listener connect 시점에 동작**해야 한다. 업그레이드뿐 아니라 단순 재시작, 권한 재허용 등 어떤 이유로 연결이 재수립되어도 잔존 원본을 정리하는 기회가 있어야 한다. (idempotent 하므로 반복 실행이 안전하다.)
- `packageName + contentSignature` 매칭은 현재 저장 로직과 동일한 규칙을 사용한다 — 재발명하지 않는다.
- Protected 알림은 **절대** cancel 하지 않는다. 이는 [protected-source-notifications](../journeys/protected-source-notifications.md) 의 불변조건.
- SILENT 요약 알림 count 는 observer 가 자동으로 재계산하므로 sweep 과 직접 연결할 필요 없음 (cancel 이 일어나든 아니든 DB count 자체는 변하지 않는다).
- Sweep 이 완료되었는지 추적하는 영속 플래그는 **만들지 않는다**. 매번 connect 시 최신 active tray 와 최신 DB 를 비교하는 게 단순하고 올바르다.

## Scope

**In:**
- SILENT 상태로 DB 에 저장된 알림 중 tray 에 원본이 남아있는 케이스의 일괄 cancel
- Protected 알림 bypass (재확인)
- 단위 테스트로 매칭 로직 + protection 우회 검증

**Out:**
- DIGEST 잔존 원본 정리 (DIGEST 는 opt-in 기반이고 이미 replacement 로 관리되므로 별도 결정 필요)
- Priority 원본 (절대 cancel 하지 않는 불변)
- 새 UI 요소 — 이 기능은 사용자 눈에 보이는 액션 없음, 조용히 청소만 수행
- Migration 완료 여부 영속화

---

## Task 1: Add failing tests for silent-source sweep behavior

**Objective:** 동작을 코드로 고정하기 전에 기대를 테스트로 명시.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/notification/SilentSourceMigrationSweeperTest.kt`
- sweep 로직을 `SmartNotiNotificationListenerService` 에서 직접 실행하지 말고, 순수 Kotlin 으로 테스트 가능한 `SilentSourceMigrationSweeper` (또는 비슷한 이름) 클래스에 추출해 테스트.

**Step 1: Write failing tests**
최소 커버리지:
1. DB 에 SILENT row 가 있고 `packageName + contentSignature` 가 일치하는 active SBN 이 있으면 cancel 대상에 포함.
2. DB row 는 SILENT 가 아니지만 같은 signature 의 active SBN 이 있으면 cancel 대상 아님.
3. Signature 는 일치하지만 `ProtectedSourceNotificationSignals.isProtected == true` 이면 cancel 대상 아님.
4. Active SBN 목록이 비어 있으면 cancel 호출 0회.
5. 동일 (packageName, signature) 에 해당하는 SBN 여러 건이 tray 에 있으면 각각에 대해 cancel 호출.

**Step 2: Run targeted tests**
```bash
./gradlew :app:testDebugUnitTest --tests \
  "com.smartnoti.app.notification.SilentSourceMigrationSweeperTest"
```

**Step 3:** 구현은 Task 2 에서. 지금은 모두 실패하는 상태여야 한다.

---

## Task 2: Implement `SilentSourceMigrationSweeper`

**Objective:** 순수 로직을 추출해 테스트 가능하게 만든 뒤, 리스너에서 호출.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/notification/SilentSourceMigrationSweeper.kt`
  - 입력: `activeNotifications: List<StatusBarNotification>`, `currentSilentNotifications: List<NotificationUiModel>` (또는 `packageName + contentSignature` set), protected detector.
  - 출력: cancel 해야 할 `sbn.key` 목록.
  - cancel 호출 자체는 이 클래스가 하지 않는다 (테스트 친화성 확보).
- 수정: `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
  - `onListenerConnected` 에 one-shot sweep job 추가. 다른 observer job 들과는 분리.
  - sweep 결과 key 목록에 대해 `cancelNotification(key)` 를 Main dispatcher 로 호출.
  - 기존 `silentSummaryJob`, `storeSyncJob`, `enqueueOnboardingBootstrapCheck` 순서 유지.

**Step 1: 데이터 구조 정리**
- Signature 재계산은 `DuplicateNotificationPolicy.contentSignature(title, body)` + persistent suffix 규칙을 그대로 재사용 (리스너 코드 참고).
- SBN 에서 title/body 추출 방식도 기존 `processNotification` 과 동일.

**Step 2: 구현**
- Sweeper 는 stateless. 한 번 호출에 한 번의 비교.
- Sweeper 결과를 받은 리스너가 cancel 을 수행.

**Step 3: 테스트 통과 확인**
Task 1 의 테스트가 모두 초록.

---

## Task 3: Verify end-to-end on emulator

**Objective:** 실제 tray 에 남아 있는 SILENT 원본이 재시작 후 사라지는지 관측.

**Steps:**
1. 현재 DB 에 SILENT row 가 몇 개 있는지 확인 (Home StatPill `조용히`, Hidden 헤더).
2. 시스템 tray 에 남아 있는 SILENT 원본이 있는지 `dumpsys notification --noredact` 로 확인. 사전 단계에서 이들은 이 migration 이 없으면 유지되는 잔존물.
3. 앱 force-stop + 재시작.
4. 재시작 후 tray 에 해당 원본이 제거됐는지 확인.
5. Protected 알림(`-S media`) 을 게시한 뒤 재시작 → 제거되지 않고 남아있어야 함.

## Task 4: Update journey docs

**Objective:** 문서와 동기화 (→ `.claude/rules/docs-sync.md`).

**Files:**
- `docs/journeys/silent-auto-hide.md`
  - Observable steps 에 "0. 리스너 접속 시 `SilentSourceMigrationSweeper` 가 잔존 원본을 cancel" 삽입.
  - Known gaps 에서 "이 기능 릴리스 이전에 캡처된 SILENT 원본은 tray 에 그대로 남아 있을 수 있음" 항목 제거.
  - Change log 에 migration 구현 엔트리 추가 (PR / commit 해시).
- `docs/journeys/README.md` Verification log 에 migration 검증 결과 추가 여부는 PR 작성자 판단.

## Task 5: Self-review and PR

- 테스트 전체 실행 (`./gradlew :app:testDebugUnitTest`).
- journey 문서 변경 포함해서 PR. 제목은 "feat: sweep stale silent source notifications on listener connect" 계열.
- PR body 에 Task 3 의 ADB 스크립트와 before/after tray 상태 dump 를 첨부하면 리뷰어가 빠르게 검증 가능.

---

## Risks / open questions

- **Cancel 실패**: cancelNotification 은 non-cancelable 알림(ongoing 등)에 대해 실패할 수 있음. 이들은 대개 Protected detector 가 미리 걸러내지만, 엣지 케이스에서 cancel 이 silently 실패해도 sweep 은 계속 진행해야 함. 예외 catch 로 처리.
- **DB 와 tray 의 race**: sweep 을 돌리는 순간에도 새 알림이 posted 될 수 있다. onListenerConnected 직후의 sweep 은 해당 순간의 스냅샷만 보고 처리하므로, 경미한 타이밍 miss 는 다음 connect 에서 정리됨 (idempotent 의 장점).
- **사용자가 의도적으로 복원한 SILENT 를 cancel 하지 않는가**: 복원한다는 의미가 불명확. 현재는 "DB 가 SILENT = 사용자가 숨김을 원함" 으로 해석. 재분류 UI 가 돌아오면 DB 가 먼저 업데이트되므로 tray 의 새 원본은 다음 sweep 대상에서 자연히 제외됨.
