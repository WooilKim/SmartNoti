---
status: planned
fixes: 511
priority: P0
last-updated: 2026-04-27
---

# Fix #511 — Cancel source notification whenever SmartNoti posts a replacement

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Source issue: https://github.com/WooilKim/SmartNoti/issues/511 (P0 release-blocker, Railway-style 4–6 entry duplicate in tray).

**Goal:** 사용자가 SmartNoti 가 "처리" 한 알림 (DIGEST / SILENT / PRIORITY-with-overlay) 에 대해 원본 + replacement 가 트레이에 동시에 보이지 않게 한다. R3CY2058DLJ 에서 관측된 Gmail × 2 + group summary + SmartNoti silent_group × 3 (총 4–6 entry) → SmartNoti replacement 만 1 그룹 표시되고 Gmail 원본 / group summary 는 트레이에서 즉시 사라진다. PRIORITY pass-through (SmartNoti 가 replacement 를 게시하지 않은 경우) 는 source 그대로 둔다.

**Architecture:**
- 현재 source-cancel 경로는 `NotificationSuppressionPolicy.shouldSuppressSourceNotification(packageName, suppressedSourceApps)` 가 `packageName ∈ suppressedSourceApps` 일 때만 cancel 을 트리거. `SuppressedSourceAppsAutoExpansionPolicy` 가 그 set 을 자동 확장하지만 `decision == DIGEST` 일 때만. SILENT (Quiet Hours, IGNORE→SILENT, default-SILENT 등) 는 expansion 도 cancel 도 안 함 → 본 issue 의 직접 원인.
- Option C 채택: replacement-builder 들이 `notify(replacement)` 가 성공한 직후 동일 트랜잭션에서 source 를 cancel 한다. suppress list 와 분리 (list 는 "다음 번에도 자동으로 숨길지" 의 기록용으로만 의미). 호출 site 는 `SmartNotiNotifier`, `SilentHiddenSummaryNotifier` (group summary + child path), 그 외 replacement-builder grep 로 식별.
- Cancel 은 `SmartNotiNotificationListenerService` 가 보유한 listener 인스턴스에 `cancelNotification(sourceKey)` 위임 (이미 `MarkSilentProcessedTrayCancelChain` 에서 검증된 패턴). builder 들은 이 위임을 직접 들고 있지 않으므로 신규 `SourceCancellationService` 인터페이스 (혹은 기존 `SourceTrayActions` 확장) 를 통해 주입.
- 기존 cohort 정리는 `MigrateOrphanedSourceCancellation` runner (`MigrateAppLabelRunner` 패턴 mirror) 가 다음 launch 에서 1회성 sweep — DataStore flag (`migrate_orphan_source_cancellation_v1_done`) 로 가드.

**Tech Stack:** Kotlin, AndroidX `NotificationListenerService`, NotificationManager, Room (read-only for migration), DataStore (flag), Gradle unit tests, ADB e2e on R3CY2058DLJ.

---

## Product intent / assumptions

- "SmartNoti 가 replacement 를 posted 했다" = "SmartNoti 가 그 알림을 책임지고 보여줄게요" 라는 UX 계약이다. 따라서 source 는 항상 사라져야 한다 (issue body 의 Option C 원칙).
- SILENT 분류가 트레이에 SmartNoti 의 silent_group replacement 로 보이는 건 의도된 동작 — 사용자는 "정돈된 느낌" 을 위해 원본을 빼는 데 동의한 것으로 본다 (issue body 명시).
- PRIORITY pass-through 는 SmartNoti 가 replacement 를 발행하지 않으므로 source 를 건드리지 않는다 (이 분기는 변경 없음).
- DIGEST 의 경우는 이미 auto-expansion + suppress 로 source 가 사라지지만, 본 PR 의 새 경로가 "replacement post → source cancel" 이라는 단일 invariant 로 통일하므로 DIGEST 도 동일 코드 패스를 따른다 (auto-expansion 은 다음 알림의 list-membership 기록 용도로만 남는다).
- **Open question for user (Risks 절에 명시):** 사용자가 "SILENT 분류는 원본을 보고 싶다" 고 할 가능성 — 본 plan 의 default 는 toggle 없음 (UX 일관성). 명시 요청 시 Settings 토글 추가는 별도 후속 plan.

---

## Task 1: Failing test — Railway fixture asserts source cancellation [IN PROGRESS via PR #513]

**Objective:** Issue body 의 정확한 fixture (SILENT 분류 + Quiet Hours + `sourceSuppressionState = NOT_CONFIGURED` + replacement posted) 가 들어왔을 때 `cancelNotification(sourceKey)` 가 호출되는지 RED 상태로 고정.

**Files:**
- 신규: `app/src/test/java/com/smartnoti/app/notification/ReplacementSourceCancellationTest.kt`
- 필요 시 보강: `app/src/test/java/com/smartnoti/app/notification/NotificationDecisionPipelineTest.kt`

**Steps:**
1. Fake `SourceCancellationService` (또는 동등 fake) 가 호출 record. `NotificationDecisionPipeline` (혹은 replacement post 의 entry point) 에 inject.
2. Input: `decision = SILENT`, `quietHoursActive = true`, `currentSuppressedApps = {}` (NOT_CONFIGURED 시 set 비어있음 가정), `packageName = "com.google.android.gm"`, `sourceKey = "0|com.google.android.gm|...|RailwayBuildFailed|..."`.
3. Assert: replacement 이 게시된 후 `fakeSourceCancellation.cancelled` 에 `sourceKey` 1 entry. RED 확인.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.ReplacementSourceCancellationTest"` 로 실패 확인.

## Task 2: Inventory replacement-builder sites + investigate `replacementNotificationIssued = 0` anomaly

**Objective:** Cancel-after-post 패턴을 적용해야 할 모든 site 를 식별 + Railway DB 의 `replacementNotificationIssued = 0` (트레이에는 replacement 보임에도) 원인 파악.

**Files (read-only this task):**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotifier.kt`
- `app/src/main/java/com/smartnoti/app/notification/SilentHiddenSummaryNotifier.kt`
- `app/src/main/java/com/smartnoti/app/notification/SilentGroupTrayPlanner.kt`
- `app/src/main/java/com/smartnoti/app/notification/NotificationDecisionPipeline.kt` (line 186 `SourceNotificationSuppressionStateResolver.replacementNotificationRecorded(...)`)
- `app/src/main/java/com/smartnoti/app/notification/SourceNotificationSuppressionStateResolver.kt`

**Steps:**
1. Grep `notificationManager.notify(`, `NotificationManagerCompat.notify(` 모든 호출 site 열거 (단순 channel-config notify 제외).
2. 각 site 에 대해: replacement 인지 (source 가 존재하는 변환물인지) vs. independent in-app notification 인지 분류. Replacement 만 본 PR scope.
3. `replacementNotificationIssued` 플래그가 어느 시점에 세팅되는지 확인 — Railway row 가 `0` 인 이유: (a) 게시 전 row 가 먼저 저장되고 게시 후 update 가 안 일어남, (b) silent_group 경로는 `recordReplacement` 를 호출하지 않음, (c) DB write race. 본 task 는 진단까지만 — fix 는 Task 3 와 함께.
4. 결과 표 (site → replacement 여부 → cancel-after-post 적용 필요 여부) 를 PR description 에 포함.

## Task 3: Implement cancel-after-post invariant

**Objective:** Task 1 의 테스트를 GREEN. Task 2 에서 식별된 모든 replacement-builder site 가 동일 invariant 따름.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/notification/SourceCancellationService.kt` (interface + listener-backed impl, `MarkSilentProcessedTrayCancelChain` 의 위임 패턴 mirror)
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotifier.kt` — replacement post 후 cancel 호출 추가
- `app/src/main/java/com/smartnoti/app/notification/SilentHiddenSummaryNotifier.kt` — `postGroupSummary` / `postGroupChild` 양쪽
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` — service 가 `SourceCancellationService` impl 을 host, builder 들에 주입
- `app/src/main/java/com/smartnoti/app/notification/NotificationDecisionPipeline.kt` — 필요 시 `recordReplacement` 호출 보강 (Task 2 결과에 따라)

**Steps:**
1. `SourceCancellationService.cancel(sourceKey: String)` 정의 — 활성 listener 가 없으면 best-effort no-op + 로그 (실패 시에도 replacement 는 보존).
2. 각 replacement post 호출 직후 `runCatching { sourceCancellation.cancel(sourceKey) }.onFailure { Log.w(..., "source-cancel failed", it) }` 패턴.
3. PRIORITY pass-through 는 replacement 를 안 만드므로 자연스럽게 영향 없음 (Task 4 에서 회귀 검증).
4. `replacementNotificationIssued` 도 동일 시점에 `true` 로 update — Railway row 의 `0` 이 사라지도록.
5. Task 1 테스트 GREEN 확인. 기존 `SuppressedSourceAppsAutoExpansionPolicyTest`, `NotificationSuppressionPolicyTest`, `MarkSilentProcessedTrayCancelChainTest` 등 영향 받는지 전체 unit suite 실행.

## Task 4: Regression test — PRIORITY pass-through MUST NOT cancel source

**Objective:** PRIORITY 분기 (replacement 없음) 가 `SourceCancellationService.cancel` 을 호출하지 않음을 못박는다.

**Files:**
- 보강: `app/src/test/java/com/smartnoti/app/notification/ReplacementSourceCancellationTest.kt` (Task 1 와 동일 파일에 case 추가) 혹은 별도 `PriorityPassThroughSourcePreservedTest.kt`
- 참고: 기존 `PriorityNotificationBuilderTest` 가 있으면 보강

**Steps:**
1. Input: `decision = PRIORITY`, replacement 미게시 분기.
2. Assert: `fakeSourceCancellation.cancelled` 가 비어 있음.
3. `decision = PRIORITY` 인데 overlay 가 게시되는 (하이브리드) 분기가 코드에 존재한다면 그 case 도 별도 테스트 — overlay 도 replacement 의 일종이므로 cancel 해야 함. (Task 2 의 inventory 결과로 확정.)

## Task 5: Migration — `MigrateOrphanedSourceCancellation` runner

**Objective:** 현재 cohort (Railway 처럼 이미 트레이에 source + replacement 둘 다 있는 사용자) 를 다음 앱 launch 에서 1회 정리.

**Files:**
- 신규: `app/src/main/java/com/smartnoti/app/data/local/MigrateOrphanedSourceCancellationRunner.kt` (`MigrateAppLabelRunner` 패턴 mirror)
- `app/src/main/java/com/smartnoti/app/MainActivity.kt` — 런너 등록 (기존 migration 등록 지점 옆)
- DataStore key: `migrate_orphan_source_cancellation_v1_done` (`SettingsOnboardingRepository` 또는 별도 key store)

**Steps:**
1. Runner: 다음 launch 1회만 실행, flag set 후 no-op.
2. 동작: 활성 listener 의 active notifications 와 DB 의 `replacementNotificationIssued = 1` (혹은 본 PR 에서 backfill 한 신규 invariant) 인 row 를 join → 같은 `packageName` + `sourceEntryKey` 의 source 가 active 면 `cancelNotification(sourceKey)`.
3. Listener 가 active 가 아니면 runner 는 flag 만 set 하지 않고 다음 launch 에서 재시도 (best-effort).
4. **Risks 절 참고**: Railway row 의 `replacementNotificationIssued = 0` 은 Task 3 에서 fix 되므로, migration 은 Task 3 fix 적용 후 신규 게시되는 알림에 대해서만 유효. 이미 게시된 cohort 는 기존 `replacement` notification 의 `tag/id` 를 트레이에서 역추출해야 함 (active notifications 스캔 기반). 단순히 DB column 의존하면 Railway 같은 케이스는 못 잡음.

## Task 6: ADB e2e on R3CY2058DLJ

**Objective:** 실제 디바이스에서 issue body 의 시나리오 재현 → 본 PR 의 fix 가 트레이에서 실제로 작동.

**Steps:**
```bash
# 사전: emulator 가 아닌 R3CY2058DLJ. Quiet Hours 진입 (Settings 의 시간 editor 로 현재 시각 포함하도록 설정).
adb -s R3CY2058DLJ shell settings get global ... # Quiet Hours active 확인 (DataStore 직접 확인)

# Gmail 원본 시나리오 합성 (BigTextStyle Railway-style)
adb -s R3CY2058DLJ shell cmd notification post -S bigtext -t "Railway" RailwayBuild "Build failed for stock-dashboard"
adb -s R3CY2058DLJ shell cmd notification post -S bigtext -t "Railway" RailwayDeploy "Deployment crashed for stock-web"

# 트레이 스냅샷
adb -s R3CY2058DLJ shell dumpsys notification --noredact | grep -iE "Railway|smartnoti_silent_group|smartnoti_silent_summary"

# Expectation: 원본 (`pkg=com.smartnoti.test.synthetic` 혹은 동등 source) 0 개, SmartNoti silent_group 1 그룹 + N children
```

결과 (스크린샷 + dumpsys grep 출력) 를 PR body 에 첨부.

## Task 7: Journey doc updates

**Files:**
- `docs/journeys/silent-auto-hide.md` — Observable steps 에 "replacement post 시 source 도 동기 cancel" 명시, Known gaps 의 "Quiet Hours SILENT 잔존 원본" 항목을 Change log 로 이동, Code pointers 에 `SourceCancellationService` 추가, `last-verified` 는 Task 6 ADB sweep 날짜로 bump.
- `docs/journeys/quiet-hours.md` — Observable steps 에 "Quiet Hours 발화 SILENT 알림은 SmartNoti 가 replacement 게시하면서 원본을 트레이에서 제거" 추가, Known gaps 갱신, Change log + `last-verified` bump.
- `docs/journeys/digest-suppression.md` — DIGEST 분기도 "replacement post → source cancel" 단일 invariant 를 따르게 됨을 Change log 에 기록 (`SuppressedSourceAppsAutoExpansionPolicy` 는 list 기록용으로만 남음). `last-verified` bump.
- 본 plan frontmatter `status: shipped` + `superseded-by` 는 implementer 가 PR merge 시점에 갱신.

## Task 8: Self-review + PR

- 모든 unit test 통과 (`./gradlew :app:testDebugUnitTest`).
- ADB Task 6 결과 첨부.
- PR title: `fix(#511): cancel source notification whenever SmartNoti posts a replacement`.
- PR body: invariant 명시 (post → cancel), Task 2 의 site inventory 표, Risks 의 fallback 로깅 + retry 동작 설명.

---

## Scope

**In:**
- `SourceCancellationService` 신규 + 기존 replacement-builder 4–N 곳에 cancel-after-post 적용
- `replacementNotificationIssued` 컬럼 정확하게 세팅
- `MigrateOrphanedSourceCancellationRunner` 1회성 sweep
- 3 개 journey doc 갱신 + ADB e2e

**Out:**
- Settings 의 per-app opt-out toggle (Open question 으로 보류)
- `SuppressedSourceAppsAutoExpansionPolicy` 의 SILENT 확장 (Option A) — Option C 가 우월하므로 폐기
- DIGEST 분기 동작 변경 (이미 cancel 됨, invariant 통일만)
- PRIORITY pass-through 의 source 동작 (변경 없음 — 회귀 테스트로 보호만)

---

## Risks / open questions

- **R1: `cancelNotification(sourceKey)` 가 silently 실패하는 sources.** 일부 source app (특히 `FLAG_NO_CLEAR` 또는 foreground service 알림) 은 listener 의 cancel 이 무시될 수 있음. Fallback: warn-level 로그 + 1회 retry (50ms 후) + 그래도 실패면 metric (`source_cancel_failed_count`) 증가. 사용자에게는 silent failure (replacement 는 정상 게시됨).
- **R2: `replacementNotificationIssued = 0` for current Railway cohort.** Task 2 진단 결과에 따라 Task 3 의 fix 가 column 을 정확히 set 해도, **이미 게시된 알림** 은 column 만으로는 식별 불가. Task 5 의 migration runner 는 active notifications 를 스캔해서 trapped duplicate 를 잡아야 함 (DB column 의존만으로는 못 잡음). 본 PR 에서 둘 다 구현.
- **R3 (Open question for user): SILENT 의 source 를 보고 싶은 사용자.** Issue body 는 "정돈된 느낌" 우선이라 toggle 없음 default 가 옳다고 가정하지만, "SILENT 는 원본 그대로 두고 SmartNoti replacement 만 추가" 를 원하는 사용자 segment 가 있을 수 있음. 본 plan 은 toggle 없이 출시하고, 사용자 신고 누적 시 후속 plan 으로 Settings 토글 (`keepSourceForSilent: Boolean`) 추가. **사용자 명시 요청 시 본 PR 에 포함.**
- **R4: Migration runner 의 timing.** Listener 가 launch 시점에 bind 안 돼있을 수 있음 — runner 는 listener-bind 콜백을 기다리거나 다음 launch 로 미루는 best-effort 패턴. flag 는 실제 sweep 성공 후에만 set.
- **R5: PRIORITY-with-overlay 분기 존재 여부.** Task 2 의 inventory 가 "PRIORITY 분기에서도 overlay 가 게시되는 site" 를 발견하면 그것도 replacement 로 분류하고 cancel 적용. 발견 안되면 PRIORITY 는 손대지 않음.

---

## Related journey

본 plan 이 ship 되면 다음 journey 의 Known gaps / Change log 가 갱신됨:

- `docs/journeys/silent-auto-hide.md` — Quiet Hours SILENT 의 원본 잔존 gap 해소
- `docs/journeys/quiet-hours.md` — Quiet Hours 발화 SILENT 의 트레이 표현 명확화
- `docs/journeys/digest-suppression.md` — "replacement post → source cancel" 단일 invariant 를 모든 replacement 분기로 통일

연결 issue: https://github.com/WooilKim/SmartNoti/issues/511
