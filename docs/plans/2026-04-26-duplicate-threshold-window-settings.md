---
status: shipped
shipped: 2026-04-26
superseded-by: ../journeys/duplicate-suppression.md
---

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 Settings 에서 "중복 알림 묶기" 임계값(반복 횟수)과 시간 창(분)을 직접 조정할 수 있게 한다. 현재는 `DuplicateNotificationPolicy` 의 `DEFAULT_WINDOW_MILLIS = 10 * 60 * 1000L` 와 `NotificationClassifier` 의 하드코딩 `>= 3` 분기로 인해 사용자가 "더 자주 묶기" / "덜 자주 묶기" 를 조정할 수 없다. 이 plan 이 ship 되면 사용자는 운영 설정에서 임계값(예: 2/3/4/5)과 창(예: 5/10/15/30분) 을 선택할 수 있고, classifier 가 즉시 그 값을 반영한다. 룰/Category 경로는 영향 없음 — 이 knob 은 "rule 매치도 priority keyword 도 없을 때만 발화하는" base heuristic 의 임계값을 조정한다.

**Architecture:** `SmartNotiSettings` 에 `duplicateDigestThreshold: Int` (default 3) + `duplicateWindowMinutes: Int` (default 10) 두 필드를 추가하고, `SettingsRepository` 가 두 값을 read/write/observe 하도록 확장한다. `DuplicateNotificationPolicy` 가 더 이상 `DEFAULT_WINDOW_MILLIS` 를 자체 보유하지 않고 호출자에서 주입받게 한다 (생성자는 현재 이미 `windowMillis` 파라미터를 받음 — default 만 제거). `SmartNotiNotificationListenerService.processNotification` 가 settings snapshot 에서 두 값을 읽어 (a) policy 인스턴스를 매 호출 사이트에서 짧게 빌드하거나 settings-driven re-creation 을 적용하고, (b) `CapturedNotificationInput.duplicateThreshold` 를 통해 classifier 까지 전달한다 — 또는 더 간단하게 classifier 의 하드코딩 `>= 3` 분기를 `>= input.duplicateThreshold` 로 일반화. Settings 화면에는 `OperationalSummaryCard` 의 운영 상태 row 안에 quiet-hours editor 와 동일한 dropdown UX 로 두 selector 추가 (`AssistChip + DropdownMenu`).

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, Gradle unit tests.

---

## Product intent / assumptions

- 임계값 후보: `2 / 3 / 4 / 5 / 7 / 10` (사용자가 "더 자주 묶기" 부터 "거의 묶지 말기" 까지 직관적으로 선택 가능). Default = 3 (현재 동작 유지).
- 창 후보 (분): `5 / 10 / 15 / 30 / 60`. Default = 10 (현재 동작 유지).
- 둘 다 양수 정수이며 0 / 음수는 disallow — UI 는 dropdown 이므로 invalid 값이 들어올 일 없음. `SettingsRepository` 의 setter 에서 `coerceAtLeast(1)` 가드 한 줄로 안전.
- 변경 즉시 발효 (앱 재시작 불필요) — listener 가 매 `processNotification` 호출에서 settings snapshot 을 읽으므로 다음 알림부터 새 값 적용.
- 이 knob 은 base heuristic 만 영향 — priority keyword / VIP / rule 매치는 그대로 우선 (Observable steps 6 의 cascade 순서 불변).
- REPEAT_BUNDLE 룰의 사용자 임계값과는 별개 — REPEAT_BUNDLE 은 "사용자가 명시적으로 묶음 임계를 지정하는 별도 메커니즘" 이고 (→ duplicate-suppression 의 Out of scope), 본 plan 의 knob 은 룰이 없을 때 적용되는 base heuristic 의 임계값.
- **사용자 판단 필요**: 임계값/창 후보 리스트의 정확한 숫자, Settings 카드 안 라벨 카피 ("반복 묶음 기준" 등), `start == end` 류 경고가 필요한 엣지 케이스 (이 도메인에는 해당 없음). Default 유지 + 후보 6×5 조합 자체는 product judgment 가 가능 — 구현 단계에서 user 에게 확인.

---

## Task 1: Pin current behavior with failing tests [SHIPPED via PR #354]

**Objective:** 현재 하드코딩된 3/10min 동작이 settings-driven 으로 바뀌어도 default 가 동일함을 회귀 고정 + 새 knob 의 contract 를 RED 로 고정.

**Files:**
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/DuplicateNotificationPolicyTest.kt`
- 신규 또는 보강: `app/src/test/java/com/smartnoti/app/domain/usecase/NotificationClassifierTest.kt`
- 신규: `app/src/test/java/com/smartnoti/app/data/settings/DuplicateThresholdSettingsTest.kt` (Robolectric 또는 pure unit, 가능한 후자)

**Steps:**
1. `DuplicateNotificationPolicyTest` — `windowStart(now)` 가 생성자 주입 `windowMillis` (예: `5 * 60 * 1000`) 를 정확히 반영함을 검증. Default 제거 후에도 명시적 주입이 동작.
2. `NotificationClassifierTest` — `CapturedNotificationInput` 의 새 필드 (가칭 `duplicateThreshold: Int`) 가 `2` 일 때 `duplicateCountInWindow == 2` 가 DIGEST 분기에 진입하고, `5` 일 때 동일 입력이 SILENT fall-through 로 빠지는 두 케이스. Threshold 0 / 음수 입력은 effectively no-op (코드는 `coerceAtLeast(1)` 로 가드된 상태로 호출됨을 가정).
3. `DuplicateThresholdSettingsTest` — `SmartNotiSettings(duplicateDigestThreshold=4, duplicateWindowMinutes=15)` 가 round-trip 으로 영속화/복원되는지. (`SettingsRepository` 의 DataStore round-trip 단위 테스트 패턴 참고 — 기존 `quiet_hours_*` 키 테스트 자리에 동일 형태로 추가.)
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.domain.usecase.DuplicateNotificationPolicyTest" --tests "com.smartnoti.app.domain.usecase.NotificationClassifierTest" --tests "com.smartnoti.app.data.settings.DuplicateThresholdSettingsTest"` → 실패 (RED) 확인.

## Task 2: Add settings fields + repository wiring [SHIPPED via PR #354]

**Objective:** `SmartNotiSettings` data class + `SettingsRepository` read/write/observe + DataStore key 추가. Default 동작 유지.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt`
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt`

**Steps:**
1. `SmartNotiSettings` 에 `val duplicateDigestThreshold: Int = 3` + `val duplicateWindowMinutes: Int = 10` 추가.
2. `SettingsRepository` 에 두 DataStore key (`duplicate_digest_threshold` / `duplicate_window_minutes`) read 경로 + `setDuplicateDigestThreshold(Int)` / `setDuplicateWindowMinutes(Int)` suspend setter 추가. setter 는 `coerceAtLeast(1)` 가드.
3. `currentNotificationContext` (있다면) / `observeSettings` 가 새 필드를 함께 노출하는지 확인.
4. `Task 1` 의 `DuplicateThresholdSettingsTest` GREEN 확인.

## Task 3: Generalize policy + classifier to read settings [SHIPPED via PR #354]

**Objective:** `DuplicateNotificationPolicy` 의 default `windowMillis` 제거, `NotificationClassifier` 의 하드코딩 `>= 3` 을 `>= duplicateThreshold` 로 일반화.

**Files:**
- `app/src/main/java/com/smartnoti/app/domain/usecase/DuplicateNotificationPolicy.kt`
- `app/src/main/java/com/smartnoti/app/domain/model/CapturedNotificationInput.kt` (또는 동등 모델 — `duplicateCountInWindow` 필드를 보유한 곳)
- `app/src/main/java/com/smartnoti/app/domain/usecase/NotificationClassifier.kt`

**Steps:**
1. `DuplicateNotificationPolicy` 의 companion `DEFAULT_WINDOW_MILLIS` 제거 — 호출자에서 명시 주입.
2. `CapturedNotificationInput` 에 `duplicateThreshold: Int` 추가 (default 3 유지로 호출 사이트 마이그레이션 부담 최소).
3. `NotificationClassifier.classify` 의 `if (input.duplicateCountInWindow >= 3)` 를 `if (input.duplicateCountInWindow >= input.duplicateThreshold)` 로 변경.
4. `Task 1` 의 `NotificationClassifierTest` 새 케이스 GREEN 확인.

## Task 4: Wire settings into the listener [SHIPPED via PR #354]

**Objective:** 매 `processNotification` 호출에서 settings snapshot 으로 policy / threshold 주입.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/notification/NotificationProcessingCoordinator.kt`

**Steps:**
1. `processNotification` (혹은 coordinator) 가 이미 읽고 있는 settings snapshot 에서 `duplicateWindowMinutes` 와 `duplicateDigestThreshold` 를 추출.
2. `DuplicateNotificationPolicy` 인스턴스를 settings 값으로 빌드 (또는 기존 멤버를 settings-driven 으로 교체) — `windowMillis = settings.duplicateWindowMinutes * 60_000L` 변환.
3. `CapturedNotificationInput` 빌더가 `duplicateThreshold = settings.duplicateDigestThreshold` 를 채워 classifier 에 전달.
4. Robolectric 또는 instrumentation 단위로 listener 의 호출 사이트가 settings 의 두 값을 정확히 반영하는지 검증 (기존 listener 테스트가 있다면 그 자리에 1 케이스 추가).

## Task 5: Settings UI — two dropdown selectors [SHIPPED via PR #354]

**Objective:** 운영 상태 카드 (Settings) 에 quiet-hours editor 와 동일 패턴으로 두 selector 노출.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt`
- 필요 시 `app/src/main/java/com/smartnoti/app/ui/screens/settings/OperationalSummaryCard.kt`

**Design:**
- 카드 안 "중복 알림 묶기" row 신설 — 한 줄 설명 ("같은 내용이 짧은 시간 안에 반복되면 자동으로 모아둬요.") + 두 inline `AssistChip` (label `반복 N회`, `최근 N분`) → 탭 시 `DropdownMenu` 로 후보 리스트 노출.
- Threshold 후보: `2 / 3 / 4 / 5 / 7 / 10`. Window 후보: `5 / 10 / 15 / 30 / 60`. (실제 후보는 user 확인 후 fix.)
- Default = 3 / 10 — 현재 사용자 경험 보존.
- `OperationalSummaryCard` 의 콜백 시그니처 확장 (`onDuplicateThresholdChange: (Int) -> Unit`, `onDuplicateWindowMinutesChange: (Int) -> Unit`) — quiet-hours 콜백과 동일 패턴.

**Steps:**
1. Pure helper `DuplicateThresholdEditorOptions` (가칭, 후보 리스트만 보유) 단위 테스트 — Defaults / 옵션 순서 / 라벨 포맷 (`반복 N회`, `최근 N분`).
2. Settings VM/route 가 `setDuplicateDigestThreshold` / `setDuplicateWindowMinutes` 를 호출하도록 wiring.
3. ADB 검증: Settings 진입 → 두 selector 노출 → 값 변경 → `am force-stop` + 재진입 후 영속 확인 → testnotifier 로 반복 알림 게시 시 새 임계값/창에 따른 DIGEST 분기 발화 확인 (예: threshold=2 로 낮춘 후 2회만 게시해도 두 번째가 DIGEST 로 빠지는지).

## Task 6: Update journey docs [SHIPPED via PR #354]

**Files:**
- `docs/journeys/duplicate-suppression.md` — Preconditions 의 "10분" 표현을 "기본 10분, 사용자 설정 가능" 으로 갱신, Observable steps 1/2/6 에서 하드코딩 값 표현 완화, Code pointers 에 `SettingsRepository` 의 새 키 두 개 추가, Known gaps 의 "Threshold 3 과 window 10분은 하드코딩 — 사용자 커스터마이징 불가" 항목 제거 + Change log 에 ship 일자 + plan 링크 추가.
- `docs/journeys/notification-capture-classify.md` — 필요 시 Change log 에 한 줄 ("duplicate threshold/window 가 settings-driven 으로 일반화").

## Task 7: Self-review + PR [SHIPPED via PR #354]

- 모든 단위 테스트 통과 (`./gradlew :app:testDebugUnitTest`).
- ADB 시나리오 (threshold=2 lowered → 2회 게시 → 두 번째가 DIGEST, 그 후 threshold=3 으로 복귀 → 동일 게시 시 SILENT) 결과를 PR 본문에 첨부.
- PR 제목: `feat(settings): user-tunable duplicate-suppression threshold and window`.

---

## Scope

**In scope:**
- `SmartNotiSettings` 두 필드 추가 + DataStore round-trip.
- `DuplicateNotificationPolicy` / `NotificationClassifier` 의 settings-driven 일반화.
- Settings 화면의 두 dropdown selector + 라벨/카피.
- 단위 테스트 + ADB 검증.
- Journey 문서 갱신.

**Out of scope:**
- REPEAT_BUNDLE 룰 자체의 임계값 UX (이미 RulesScreen 의 `RuleEditorRepeatBundleThresholdController` 가 담당).
- App-별 / Category-별 임계값 (현재는 전역 단일 값).
- 마이그레이션 — 기존 사용자는 default 3/10 으로 시작하므로 별도 migration 불필요.
- Insights 카드 카피의 "반복 N회" 정적 표현 갱신 (영향 미미, 후속 polish).

## Risks / open questions

- **후보 리스트 숫자**: threshold `2/3/4/5/7/10` + window `5/10/15/30/60` 은 product judgment — user 에게 "이 셋이면 충분한가?" 확인 필요. 너무 많으면 dropdown 길어지고, 너무 적으면 power user 가 답답함.
- **Settings 카드 라벨 카피**: "반복 묶음 기준" / "중복 알림 묶기" / "반복 N회 · 최근 N분" — user 가 prefer 하는 표현 확인 필요.
- **Threshold == 1 의 의미**: `coerceAtLeast(1)` 가드로 1까지는 허용하지만, 1 이면 "같은 내용 알림이 1번만 와도 DIGEST" 가 되어 base heuristic 이 너무 공격적. 후보 리스트 최소를 2 로 두면 자연 회피 — 명시적 확인 권장.
- **Live tracker 와 window 변경의 상호작용**: `LiveDuplicateCountTracker` 의 prune 로직은 `windowStartMillis` 를 매 호출마다 받음 — settings 변경 즉시 새 window 가 적용되어 in-memory 엔트리가 적절히 prune 됨. 별도 reset 불필요 (기존 동작과 동일).
- **Listener 가 매 호출에서 settings snapshot 을 다시 읽는지** — 이미 `categories-runtime-wiring-fix` Task 4 (#245) 에서 listener 가 settings/categories/rules 세 스냅샷을 동일 call site 에서 읽도록 정리됨. 본 plan 은 그 경로를 그대로 활용.

## Related journey

- [duplicate-suppression](../journeys/duplicate-suppression.md) — Known gap "Threshold 3 과 window 10분은 하드코딩 — 사용자 커스터마이징 불가" 해소 대상.
