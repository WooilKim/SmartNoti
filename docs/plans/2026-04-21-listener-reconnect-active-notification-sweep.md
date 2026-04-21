---
id: listener-reconnect-active-notification-sweep
title: 리스너 재접속 시 활성 알림 재-스윕 — 권한 토글/리스너 disconnect 후 누락 메움
status: planned
owner: @wooilkim
journey: notification-capture-classify
created: 2026-04-21
---

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 NotificationListener 권한을 토글하거나 시스템이 일시적으로 리스너를 disconnect 했다가 재접속할 때, 그 공백 동안 시스템 tray 에 쌓인 알림이 SmartNoti 의 캡처 파이프라인에서 누락되지 않도록, 매 `onListenerConnected` 시점에 `activeNotifications` 를 "이미 처리했는지" 체크해 미처리 분만 소급 처리한다.

**Architecture:** 기존 `OnboardingActiveNotificationBootstrapCoordinator` / `ActiveStatusBarNotificationBootstrapper` 를 재사용해 sweep 로직을 확장한다. 신규 컴포넌트 `ListenerReconnectSweepCoordinator` (또는 기존 coordinator 에 second entry point) 를 도입해 onboarding 1회성 트리거와는 별개로, 매 reconnect 마다 `activeNotifications` 를 스캔한다. 중복 캡처 방지는 기존 `NotificationRepository` 의 content-signature + postTime 기반 upsert 로직에 위임하거나, 처리된 `(packageName, key, postTime)` 의 집합을 DataStore / in-memory 에 보관해 skip 한다. 분류 / 저장 / 소스 라우팅은 기존 `processNotification` 경로 그대로.

**Tech Stack:** Kotlin, NotificationListenerService, Room, DataStore, coroutines, Gradle 단위 테스트.

---

## Product intent / assumptions

- 이 기능은 **리스너가 disconnect 된 구간의 누락을 메우는 안전망** 이다. 새 알림 캡처의 1차 경로는 여전히 `onNotificationPosted`.
- "이미 처리된 알림" 을 중복으로 다시 캡처하면 안 된다 — 두 번 저장되거나, 두 번 cancel 되거나, 두 번 replacement 알림이 뜨면 UX 붕괴.
- 온보딩 bootstrap (1회성) 과 reconnect sweep (상시) 은 **서로 간섭하지 않아야** 한다. 온보딩 직후의 첫 reconnect 가 bootstrap 대상과 sweep 대상을 중복 처리하는 케이스를 회피해야 함.
- 사용자 판단이 필요한 점 (open question 으로 아래 명시):
  - 중복 방지 키를 무엇으로 잡을지 (postTime 기반 dedup vs repository 상의 content-signature upsert 에 전적으로 위임).
  - sweep 의 throttle 필요 여부 (연속 disconnect/reconnect 가 빠르게 반복되면 짧은 창 안에 여러 번 sweep 이 돌지 않도록 코어당 최소 간격을 두는 것).

## Scope

### In

- `SmartNotiNotificationListenerService.onListenerConnected` 안에서 매 reconnect 마다 sweep 트리거.
- 이미 저장된 알림은 skip, 미처리 알림만 `processNotification` 로 재주입.
- 온보딩 1회성 bootstrap 경로는 그대로 유지 — 이번 plan 은 **추가** 경로일 뿐 기존을 대체하지 않음.
- 기존 `ActiveStatusBarNotificationBootstrapper#shouldProcess` 의 capture ignore 규칙 (SmartNoti 자체 알림 / `NotificationCapturePolicy`) 을 reconnect sweep 에도 그대로 적용.
- `notification-capture-classify` / `onboarding-bootstrap` journey Known gaps 업데이트.

### Out

- `onNotificationRemoved` 재구독 / 시스템 tray 와 DB 의 양방향 동기화 (보류 — 별도 plan).
- 리스너가 아예 꺼진 동안 (앱 프로세스 kill 된 동안) 시스템이 이미 cancel 한 알림 복원 — 시스템 tray 에 없으면 복원 불가, 이번 plan 범위 밖.
- sweep 결과를 사용자에게 알림으로 surface (e.g. "방금 N건을 소급 처리했어요") — 지금은 silent.
- Android 14+ `getSnoozedNotifications` 처리.

## Tasks

### Task 1: Failing tests for reconnect sweep behavior (tests-first) [shipped via PR #94]

**Objective:** 동작을 테스트로 먼저 고정.

**Files:**
- `app/src/test/java/com/smartnoti/app/notification/ListenerReconnectActiveNotificationSweepTest.kt` (신규)

**Steps:**
1. Reconnect 시 `activeNotifications` 중 아직 DB 에 없는 것만 `processNotification` 가 호출됨을 검증.
2. 이미 DB 에 존재하는 (동일 package + postTime + contentSignature) 알림은 skip 됨.
3. SmartNoti 자신의 알림 / `NotificationCapturePolicy.shouldIgnoreCapture` 대상은 skip (기존 bootstrap 계약 유지).
4. 온보딩 bootstrap 요청 플래그가 펜딩인 상태에서 reconnect 가 발생하면 **bootstrap 경로가 먼저 consume** 되고 reconnect sweep 은 그때 이미 처리된 건은 중복 처리하지 않음.
5. 빠른 연속 reconnect (e.g. 500ms 이내 두 번) 에서 sweep 이 두 번 돌더라도 동일 알림은 한 번만 처리됨 (dedup 키로 커버).

### Task 2: Dedup 전략 + sweep coordinator 구현 [IN PROGRESS via PR #102]

**Objective:** reconnect sweep 의 정식 진입 경로 생성.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/OnboardingActiveNotificationBootstrap.kt` 또는 신규 파일 `ListenerReconnectActiveNotificationSweepCoordinator.kt`.
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`.
- 필요 시 `data/local/NotificationRepository.kt` 에 "존재 여부 확인 (package + contentSignature + postTime)" 헬퍼 추가.

**Steps:**
1. `ListenerReconnectActiveNotificationSweepCoordinator.sweep(activeNotifications)` 구현 — 현재 `ActiveStatusBarNotificationBootstrapper` 의 shouldProcess 필터 재사용.
2. Dedup: `NotificationRepository.existsByContentSignature(packageName, contentSignature, postTime)` 를 조회해 존재하면 skip. (대안: 처리된 `(packageName, key, postTime)` 를 coordinator 내부 Set 에 보관해 단일 프로세스 수명 동안 dedup.)
3. `SmartNotiNotificationListenerService.onListenerConnected` 에서 `enqueueOnboardingBootstrapCheck` 뒤에 `enqueueReconnectSweep()` 호출 — bootstrap 이 있었다면 그게 끝난 뒤 sweep 이 돌도록 coroutine 순서 보장.
4. Sweep 내부 구현은 bootstrap 과 마찬가지로 `activeNotifications` 를 `processNotification` 로 보냄.

### Task 3: 온보딩 bootstrap 과의 상호작용

**Objective:** 두 경로가 겹치지 않게.

**Steps:**
1. 온보딩 bootstrap 이 실행되는 reconnect 에서는 sweep 을 **즉시** 실행하지 않고, bootstrap 완료 후 1회만 실행 (또는 bootstrap 이 처리한 집합을 sweep 이 dedup 로 자동 skip).
2. 단위 테스트에서 시나리오 커버 (Task 1 의 케이스 4).

### Task 4: Journey 문서 갱신

**Files:**
- `docs/journeys/notification-capture-classify.md` — Known gaps 의 "일반 재접속 … 보완 경로 없음" 불릿을 "→ plan: 2026-04-21-listener-reconnect-active-notification-sweep" 링크로 추가 (원문은 유지), 구현 후 Change log 에 날짜 + 요약.
- `docs/journeys/onboarding-bootstrap.md` — Known gaps 의 "권한 토글해도 재실행되지 않음" 불릿에 동일 plan 링크 주석. 구현 후 Observable steps 가 바뀐다면 거기도 반영.
- `docs/journeys/README.md` Verification log — 구현 완료 sweep 기록.

### Task 5: Verification recipe + 수동 검증

**Objective:** 실제 환경에서 드리프트 없음 확인.

**Steps:**
1. Verification recipe (아래) 실행.
2. `logcat` 에서 sweep 트리거 로그 + 중복 skip 카운트 확인.
3. DB 상 중복 row 없음 확인 (동일 package + postTime + contentSignature 는 1 row).

## Verification recipe (post-implementation)

```bash
# 0. 온보딩 완료된 기존 설치 전제 (pm clear 없음)

# 1. 리스너 권한 OFF
adb shell cmd notification disallow_listener \
  com.smartnoti.app/.notification.SmartNotiNotificationListenerService

# 2. 리스너가 꺼진 동안 알림 2건 posted
adb shell cmd notification post -S bigtext -t "Bank" ReconA "인증번호 111111"
adb shell cmd notification post -S bigtext -t "Promo" ReconB "오늘만 50% 할인"

# 3. 리스너 권한 ON → onListenerConnected 재발화
adb shell cmd notification allow_listener \
  com.smartnoti.app/.notification.SmartNotiNotificationListenerService

# 4. 앱 열어서 두 알림이 모두 분류/저장됐는지 확인
adb shell am start -n com.smartnoti.app/.MainActivity
# 기대: ReconA → PRIORITY (인증번호 키워드), ReconB → DIGEST/SILENT (프로모 룰에 따라)

# 5. 리스너를 다시 끄지 않고 같은 알림을 재게시 (동일 postTime 이 아닌 새 post) 해도
#    sweep 이 불필요하게 중복 저장하지 않는지 확인 — 이 경우는 onNotificationPosted 경로 기본 동작이지만,
#    재빠른 disallow/allow 반복 후에도 DB row 가 원본 개수를 넘지 않는지 확인.
adb shell cmd notification disallow_listener \
  com.smartnoti.app/.notification.SmartNotiNotificationListenerService
adb shell cmd notification allow_listener \
  com.smartnoti.app/.notification.SmartNotiNotificationListenerService
# 기대: 2건 그대로, 중복 저장 없음.
```

## Risks / open questions

- **Dedup 키 결정** — 현재 Repository 의 upsert 가 동일 `(packageName, contentSignature, postTime)` 를 자동 merge 하는지 확인 필요. 만약 merge 가 아닌 insert-only 라면 sweep 은 별도의 pre-check 쿼리가 필요. plan-implementer 가 Task 2 착수 시 `NotificationRepository#save` 를 직접 읽고 확정.
- **Bootstrap + sweep 경합** — 온보딩 bootstrap 이 방금 막 처리한 알림을 sweep 이 곧바로 다시 processNotification 에 넣어 cancel 경로를 재실행하면 UX 부작용 (두 번 cancel 호출은 무해하지만, replacement 알림 재게시는 소음). coordinator 레벨에서 순차 실행 보장 + dedup 로 커버.
- **Throttle 필요성** — 사용자가 Quick Settings 의 "알림 리스너" 토글을 빠르게 여러 번 누르면 sweep 이 여러 번 돌 수 있음. dedup 로 결과는 동일하지만 I/O 낭비. 초기 구현은 throttle 없음, 실측 후 결정.
- **리스너가 꺼진 동안 시스템이 이미 dismiss 한 알림** — `activeNotifications` 에 없으면 SmartNoti 입장에선 원천적으로 복구 불가. 이 plan 의 한계로 인정하고 journey Known gaps 에 명시.
- **Active notifications 가 null 인 순간** — 리스너 서비스가 막 bind 된 직후 `getActiveNotifications()` 가 null / throw 하는 레이스가 있음. runCatching + null-safe 처리.

## Related journey

- Source: [notification-capture-classify](../journeys/notification-capture-classify.md) — Known gap "일반 재접속 시점의 누락은 현재 보완 경로 없음" 을 resolve.
- 연관: [onboarding-bootstrap](../journeys/onboarding-bootstrap.md) — Known gap "권한을 토글해도 재실행되지 않음" 이 reconnect sweep 으로 자연스럽게 해소 (엄밀한 "재실행" 은 아니지만 미처리 분은 메움).
- 선행 plan: [2026-04-19-onboarding-active-notification-bootstrap](2026-04-19-onboarding-active-notification-bootstrap.md) — 이 plan 은 해당 1회성 부트스트랩 구조를 **재사용/확장** 한다.

## Decision log

- 2026-04-21: 초안 작성. 두 journey 의 Known gaps (capture 누락 / onboarding 재실행 없음) 가 같은 구조적 공백을 가리키고 있어 단일 plan 으로 묶음. 첫 구현은 dedup 을 Repository upsert 에 위임하는 최소 경로를 권장하며, 결과에 따라 별도 in-memory Set dedup 을 추가.
