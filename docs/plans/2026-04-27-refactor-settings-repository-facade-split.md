---
status: planned
---

# `SettingsRepository` 705-line per-domain façade split Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. Pure refactor — behavior must not change. Pin every public method with characterization tests before moving any code. Single-instance singleton contract (`SettingsRepository.getInstance(context)`) and the `observeSettings(): Flow<SmartNotiSettings>` aggregate flow MUST remain bit-identical to today.

**Goal:** `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` (현재 705 lines, code-health Critical threshold 700 초과) 의 30+ suspend setters + observe pattern + `applyPendingMigrations` + `currentNotificationContext` 를 도메인별 sibling repository 4개 + 1개 Migration runner 로 분해하고, 기존 `SettingsRepository` 클래스는 sibling 들을 합성하는 façade 로 축소한다. 41개 호출 site (production + test) 의 시그니처는 변경하지 않는다 — façade 가 모든 기존 메서드를 sibling 으로 위임만 하므로 import 1줄도 바뀌지 않는다. 사용자 가시 동작 / DataStore key set / migration 동작 / observe 흐름 모두 변경 없음. 본 plan 은 같은 세션의 `2026-04-27-refactor-settings-screen-split.md` + `2026-04-27-refactor-settings-suppression-cluster-split.md` 가 검증한 carve-out → characterization 패턴을 data 레이어로 이식한다.

**Architecture:**
- 같은 패키지 (`data/settings/`) 에 sibling 5개 신설:
  - `SettingsQuietHoursRepository.kt` — quiet hours: `setQuietHoursEnabled` / `setQuietHoursStartHour` / `setQuietHoursEndHour` / `setQuietHoursPackages` / `addQuietHoursPackage` / `removeQuietHoursPackage` (~6 setters; key 4개: `QUIET_HOURS_ENABLED`, `QUIET_HOURS_START_HOUR`, `QUIET_HOURS_END_HOUR`, `QUIET_HOURS_PACKAGES`)
  - `SettingsDuplicateRepository.kt` — duplicate burst heuristic: `setDuplicateDigestThreshold` / `setDuplicateWindowMinutes` (~2 setters; key 2개)
  - `SettingsDeliveryProfileRepository.kt` — per-tier 전달 모드: priority/digest/silent × {alertLevel, vibrationMode, headsUpEnabled, lockScreenVisibility} 12 setters (key 12개) + replacement auto-dismiss 2 setters (`setReplacementAutoDismissEnabled` / `setReplacementAutoDismissMinutes`; key 2개) + `setInboxSortMode` (key 1개)
  - `SettingsSuppressionRepository.kt` — digest/silent suppression: `setSuppressSourceForDigestAndSilent` / `setSuppressedSourceApps` / `setSuppressedSourceAppExcluded` / `setSuppressedSourceAppsExcludedBulk` / `toggleSuppressedSourceApp` / `setHidePersistentNotifications` / `setHidePersistentSourceNotifications` / `setProtectCriticalPersistentNotifications` / `setShowIgnoredArchive` (~9 setters; key 5개 set-typed + 4개 boolean — 모두 "정리/억제" 정책에 응집)
  - `SettingsOnboardingRepository.kt` — onboarding 1회성 flag + Categories 마이그레이션 + Home 카드 ack: `observeOnboardingCompleted` / `setOnboardingCompleted` / `requestOnboardingActiveNotificationBootstrap` / `consumeOnboardingActiveNotificationBootstrapRequest` / `isOnboardingBootstrapPending` / `observeRulesToCategoriesMigrated` / `setRulesToCategoriesMigrated` / `observeUncategorizedPromptSnoozeUntilMillis` / `setUncategorizedPromptSnoozeUntilMillis` / `observeQuickStartAppliedCardAcknowledgedAtMillis` / `setQuickStartAppliedCardAcknowledgedAtMillis` / `observeCategoriesMigrationAnnouncementSeen` / `setCategoriesMigrationAnnouncementSeen` (~13 메서드; key 8개)
  - `SettingsMigrationRunner.kt` — `applyPendingMigrations()` 만 owner. 현재 3개 v* migration block (`SUPPRESS_SOURCE_MIGRATION_V1_APPLIED`, `REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED`, `INBOX_SORT_MODE` default materialization) 모두 동일 `dataStore.edit { }` 안에서 atomically 적용. 새 migration 추가 시 본 파일이 single-edit point.
- `SettingsRepository.kt` 는 thin façade 로 축소: ~150 lines 이하 목표. 책임은 다음 세 가지로 한정 — (1) DataStore 인스턴스 보유, (2) 5개 sibling + Migration runner composition, (3) `observeSettings(): Flow<SmartNotiSettings>` (aggregate read) + `currentNotificationContext(...)` (cross-domain composite) 이 두 메서드는 다중 도메인 키를 동시에 읽으므로 façade 에 남는다 + `clearAllForTest` (test-only). 모든 setter 는 sibling 으로 위임 (1-line `= quietHours.setEnabled(enabled)` 등). 기존 caller 의 `settingsRepository.setQuietHoursEnabled(true)` 호출은 façade 가 그대로 노출하므로 41 호출 site 모두 import 변경 0건.
- DataStore key 정의는 sibling 별로 옮긴다 (`object Keys` companion 안에서) — 이렇게 하면 도메인 변경 시 grep 범위가 sibling 1개로 좁아진다. companion 의 stale `private val *` 키 declaration 들은 façade 에서 모두 제거.
- `getInstance(context)` 싱글톤 + `clearInstanceForTest` 패턴 유지 — sibling 들은 façade 가 보유한 단일 DataStore 인스턴스를 그대로 받아 stateless 하게 동작 (각 sibling 이 자체 DataStore singleton 을 만들지 않는다).
- 직전 두 plan (`2026-04-27-refactor-settings-screen-split` / `…-suppression-cluster-split`) 이 검증한 패턴 (characterization test → carve-out → 사이즈 검증 → behavior unchanged) 을 그대로 재사용.

**Tech Stack:** Kotlin, Jetpack DataStore (Preferences), Coroutines/Flow, JUnit Robolectric (existing `SettingsRepositoryQuietHoursPackagesTest`, `SettingsRepositoryQuietHoursWindowTest`, `SettingsRepositoryMigrationTest`, `SettingsRepositorySuppressedSourceAppsExcludedTest`, `DuplicateThresholdSettingsTest`, `DeliveryProfileSettingsTest` 6개 가 이미 cover — 모두 façade 시그니처를 통해 호출하므로 본 refactor 후에도 GREEN 유지 기대).

---

## Product intent / assumptions

- 본 plan 은 **순수 리팩터** — 사용자 가시 동작 / DataStore on-disk schema / key 이름 / default 값 / migration 순서 / aggregate `SmartNotiSettings` 모델 / `currentNotificationContext` 시맨틱 모두 변경 없음.
- 기존 `SettingsRepository` public API (모든 suspend setter, observe* Flow, `getInstance`, `clearAllForTest`) 의 시그니처 100% 유지 — façade 가 sibling 으로 1:1 위임. 즉 41개 호출 site (`SettingsScreen.kt` / `SmartNotiNotificationListenerService.kt` / `MainActivity.kt` / `OnboardingQuickStartSettingsApplier.kt` / 6개 unit test 등) 코드 0줄 변경.
- DataStore key 분포는 도메인 응집도 기준으로 분류 — 한 도메인의 setter 와 그 도메인이 owning 하는 key 는 같은 sibling 에 배치. 도메인이 모호한 key (`SUPPRESS_SOURCE_MIGRATION_V1_APPLIED` 같은 migration gate) 는 `SettingsMigrationRunner` 가 보유.
- `applyPendingMigrations()` 는 **단일 `dataStore.edit { }` 트랜잭션** — 3개 migration block 의 원자성을 깨뜨리지 않는다 (이 atomicity 가 테스트로 명시 보장되지는 않으나, 현 코드의 명백한 의도이며 본 refactor 가 이를 깨면 첫 launch race 에서 partial state 발생 가능).
- 신규 sibling 들은 모두 같은 package `com.smartnoti.app.data.settings` — visibility 는 `internal` (façade 와 같은 모듈만 접근). 외부 caller 는 façade 만 사용.
- carving 후 façade size 통과 기준: ≤ 200 lines (300+ 줄의 setter 위임만 남기 위해 보수적 상한). 미달 시 setter group 별로 추가 carve.
- 기존 6개 test class 는 façade 시그니처를 통해 호출하므로 sibling 분해 후에도 zero-edit 으로 GREEN 유지 — 이 가정은 Task 1 grep 으로 사전 확인.

판단 필요 (Risks / open questions 에 escalate):
- `currentNotificationContext(duplicateCountInWindow)` 가 quiet hours 도메인 + duplicate 도메인 두 곳을 동시에 읽는다. 본 plan 은 façade 에 남기는 것으로 결정했으나, 도메인 객체 (예: `NotificationContextBuilder`) 로 추출이 나은지는 후속 plan 으로 분리 (스코프 폭주 방지).
- `SettingsOnboardingRepository` 의 책임 (`onboarding flag` + `categories migration flag` + `home card ack` 3개 묶음) 이 도메인 응집도 측면에서 약함 — 후속에 `SettingsCategoriesMigrationRepository` / `SettingsHomeCardAckRepository` 로 더 쪼개는 게 자연스러울 수 있다. 본 plan 은 705-line 임계 해소가 1차 목적이므로 한 sibling 에 묶는다.
- 신규 sibling 들의 visibility 를 `internal` 로 둘지 `public` 으로 둘지 — `internal` 이면 모듈 외부 호출 차단 (의도) 이지만 향후 multi-module split 시 부담. 본 plan 은 `internal` 로 시작하고 multi-module 도래 시 별도 plan 으로 승격.
- `SettingsRepositoryMigrationTest` 가 façade 의 `applyPendingMigrations()` 를 호출하는데, runner 분리 후에도 façade 가 동일 메서드를 노출 (위임만) — 테스트 zero-edit 가정은 Task 1 에서 실제 시그니처 grep 으로 재검증.

---

## Task 1: Pin behavior with characterization tests + caller-grep audit [IN PROGRESS via PR #TBD]

**Objective:** Refactor 전에 `SettingsRepository` 의 모든 public API 가 caller 코드와 6개 test 의 시그니처를 통해 호출되는지 grep 으로 매핑하고, 부족한 cover (특히 `clearAllForTest`, `currentNotificationContext`, `consumeOnboardingActiveNotificationBootstrapRequest` 같은 multi-key 동작) 를 추가 characterization test 로 채운다. 41 호출 site 중 façade-only 호출 비율 100% 임을 입증해야 sibling 분해가 caller-impact-zero 임이 보장된다.

**Files:**
- 신규 `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryFacadeContractTest.kt`
- (참고만, 변경 없음): `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryQuietHoursPackagesTest.kt`, `SettingsRepositoryQuietHoursWindowTest.kt`, `SettingsRepositoryMigrationTest.kt`, `SettingsRepositorySuppressedSourceAppsExcludedTest.kt`, `DuplicateThresholdSettingsTest.kt`, `DeliveryProfileSettingsTest.kt`

**Steps:**
1. `grep -rn "settingsRepository\." app/src/main app/src/test app/src/debug | sort -u | wc -l` 로 호출 횟수 베이스라인 기록 (PR 본문에 before/after 비교).
2. `grep -rn "SettingsRepository\." app/src/main app/src/test app/src/debug` 로 static (companion) 호출도 catalog (`getInstance`, `clearInstanceForTest`).
3. 신규 `SettingsRepositoryFacadeContractTest` 작성 — fake `Context` (Robolectric `ApplicationProvider.getApplicationContext()`) 로 다음 cross-domain 동작을 assertion:
   - `observeSettings()` 가 5개 도메인 sibling 의 값을 단일 `SmartNotiSettings` 로 합성해 emit (각 sibling 의 default 값이 정확히 모델 default 와 일치).
   - `currentNotificationContext(duplicateCountInWindow=N)` 가 quiet hours `enabled` + `(start,end)` + 현재 hour + 전달받은 N 을 정확히 묶어 `NotificationContext` 반환.
   - `applyPendingMigrations()` 호출 후 `SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT=true`, `REPLACEMENT_AUTO_DISMISS_ENABLED/MINUTES`=defaults, `INBOX_SORT_MODE=RECENT` 모두 단일 read snapshot 에 동시 visible (atomicity 확인).
   - `consumeOnboardingActiveNotificationBootstrapRequest()` 가 `pending=true && completed=false` → `pending=false, completed=true` 로 atomically transition (race-free).
   - `clearAllForTest()` 가 모든 도메인 키 reset.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.settings.*"` → 모든 기존 테스트 + 신규 contract test GREEN.
5. PR 본문에 caller catalog (production / test / debug 분류) 첨부.

## Task 2: Carve out `SettingsQuietHoursRepository` + `SettingsDuplicateRepository`

**Objective:** 도메인 경계가 가장 명확한 두 sibling 부터 추출. 두 도메인 모두 setter 개수가 적고 (6 + 2) 이미 전용 test 가 있어 회귀 위험이 가장 낮다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/data/settings/SettingsQuietHoursRepository.kt`
- 신규 `app/src/main/java/com/smartnoti/app/data/settings/SettingsDuplicateRepository.kt`
- 변경 `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt`

**Steps:**
1. `SettingsQuietHoursRepository` 신설 — constructor 에 `dataStore: DataStore<Preferences>` 받음 (façade 가 보유한 인스턴스 그대로 주입). 6개 suspend setter 와 `companion object Keys { val ENABLED = booleanPreferencesKey("quiet_hours_enabled"); … }` 이동.
2. 동일 패턴으로 `SettingsDuplicateRepository` 신설 (2개 setter + 2개 key).
3. façade `SettingsRepository` 에 `private val quietHours = SettingsQuietHoursRepository(context.dataStore)` + `private val duplicate = …` 생성. 기존 6+2개 setter 를 1-line 위임 (`suspend fun setQuietHoursEnabled(enabled: Boolean) = quietHours.setEnabled(enabled)`).
4. 기존 façade companion 의 8개 key declaration 제거 (sibling 으로 이동했으므로). `observeSettings()` 안의 read 는 sibling 의 key constant 를 import 해서 사용 (여전히 같은 트랜잭션 안에서 multi-key read).
5. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest --tests "com.smartnoti.app.data.settings.*"` GREEN.
6. `wc -l SettingsRepository.kt` 후 수치 기록 (예상: 705 → ~600).

## Task 3: Carve out `SettingsDeliveryProfileRepository` + `SettingsSuppressionRepository`

**Objective:** 가장 큰 두 도메인 (전달 모드 12+2+1 setter / suppression 9 setter) 을 분리해 façade 사이즈를 critical threshold 아래로 떨어뜨린다.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/data/settings/SettingsDeliveryProfileRepository.kt`
- 신규 `app/src/main/java/com/smartnoti/app/data/settings/SettingsSuppressionRepository.kt`
- 변경 `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt`

**Steps:**
1. Task 2 와 동일 패턴으로 두 sibling 추출. `SettingsDeliveryProfileRepository` 의 `setString` / `setBoolean` private helper 도 함께 이동 (현 façade line 596-606).
2. 12 alert/vibration/headsup/lockscreen setter (priority/digest/silent × 4) + replacement auto-dismiss 2 + inbox sort 1 = 15 setter 이동. 모두 façade 에서 1-line 위임.
3. Suppression 9 setter + 9 key 이동. atomic dual-write 메서드 (`setSuppressedSourceAppExcluded` / `setSuppressedSourceAppsExcludedBulk`) 의 `dataStore.edit { }` 트랜잭션 boundary 가 깨지지 않도록 cut-and-paste (이동 후 코드 byte diff 0 확인).
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.settings.*"` GREEN — 특히 `SettingsRepositorySuppressedSourceAppsExcludedTest` 의 atomic transition 시나리오.
5. `wc -l SettingsRepository.kt` ≤ 350 인지 검증.

## Task 4: Carve out `SettingsOnboardingRepository` + `SettingsMigrationRunner`

**Objective:** 마지막 두 sibling 을 분리해 façade 를 thin composition 만 남긴 ≤ 200 line 모듈로 마무리.

**Files:**
- 신규 `app/src/main/java/com/smartnoti/app/data/settings/SettingsOnboardingRepository.kt`
- 신규 `app/src/main/java/com/smartnoti/app/data/settings/SettingsMigrationRunner.kt`
- 변경 `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt`

**Steps:**
1. Onboarding 13 메서드 + 8 key 이동. 단 `consumeOnboardingActiveNotificationBootstrapRequest` 와 `requestOnboardingActiveNotificationBootstrap` 의 dual-key atomic transition (`pending` + `completed` 동시 mutate) 은 단일 `dataStore.edit { }` 안 그대로 유지.
2. `SettingsMigrationRunner.applyPendingMigrations()` 신설 — 3개 v* gate (`SUPPRESS_SOURCE_MIGRATION_V1_APPLIED`, `REPLACEMENT_AUTO_DISMISS_MIGRATION_V2_APPLIED`, `INBOX_SORT_MODE` default materialization) 를 단일 `dataStore.edit { }` 안에서 적용. 각 migration block 은 자기 도메인의 key 를 mutate (e.g. `SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT` 는 suppression sibling 의 key 를 import 해서 사용).
3. façade `SettingsRepository.applyPendingMigrations()` 는 `migrationRunner.applyPendingMigrations()` 위임만.
4. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.settings.*"` GREEN — 특히 `SettingsRepositoryMigrationTest` 의 v1+v2 동시 적용 + idempotency 시나리오.
5. `wc -l SettingsRepository.kt` ≤ 200 인지 최종 검증. 초과 시 setter group 별 추가 carve.
6. 전체 `./gradlew :app:testDebugUnitTest` + `:app:assembleDebug` GREEN.

## Task 5: PR + journey doc note

**Objective:** Refactor 사실을 doc 에 한 줄로 기록. 사용자 가시 동작 변경 0 — `quiet-hours.md` Verification recipe 가 영향받지 않음을 명시.

**Files:**
- 변경 `docs/journeys/quiet-hours.md` Known gap 의 "code health (2026-04-27): SettingsRepository 705 lines …" bullet 에 본 plan 링크 추가 (gap 본문은 변경하지 않음, "→ plan: …" 만 추가).
- 변경 본 plan 의 frontmatter 를 `status: shipped` + Change log 에 PR 링크.

**Steps:**
1. `./gradlew :app:assembleDebug` + `:app:testDebugUnitTest` 최종 GREEN.
2. PR 본문에 다음 명시:
   - 5개 신규 sibling 파일 + 1개 Migration runner + façade size before/after (705 → ≤ 200).
   - 동작 변경 없음 — 6개 기존 test 가 zero-edit 으로 GREEN, 신규 façade contract test 가 cross-domain 동작 추가 cover.
   - 41개 caller 코드 0줄 변경 (Task 1 grep catalog 첨부).
   - DataStore on-disk schema 변경 없음 (key 이름 + default 값 + migration 순서 동일).
3. PR 제목 후보: `refactor(settings): split SettingsRepository into per-domain sibling repositories behind façade`.
4. frontmatter 를 `status: shipped` 로 flip + Change log 에 PR 링크.

---

## Scope

**In:**
- 5개 신규 sibling repository (quiet-hours / duplicate / delivery-profile / suppression / onboarding) + 1개 `SettingsMigrationRunner` 분리.
- 기존 `SettingsRepository` 클래스를 thin façade 로 축소 (≤ 200 lines).
- 신규 `SettingsRepositoryFacadeContractTest` (cross-domain composition / atomicity / migration 순서 cover).
- `quiet-hours.md` Known gap bullet 에 plan 링크 한 줄.

**Out:**
- DataStore key 이름 변경 / default 값 변경 / migration 순서 변경.
- caller 코드 수정 (façade 가 모든 기존 시그니처 보존).
- aggregate `SmartNotiSettings` 모델 분리 / per-domain model 추출.
- multi-module split (sibling visibility 를 `internal` 로 두어 향후 검토 여지 유지).
- `currentNotificationContext` 의 도메인 객체 추출 (별도 plan).
- `SettingsScreen.kt` 의 도메인별 ViewModel 분리 (별도 plan; 이미 카브아웃된 sub-section 들이 상위 façade 를 통해 호출하므로 본 plan 과 독립).

---

## Risks / open questions

- **DataStore singleton scope**: `private val Context.dataStore by preferencesDataStore(name = "smartnoti_settings")` 가 file-level 이므로 sibling 들이 같은 `dataStore` 인스턴스를 받는지 확인 — Task 2 의 sibling constructor 는 `context.dataStore` 를 직접 받지 말고 façade 가 한 번 evaluate 한 인스턴스를 주입해야 한다 (그렇지 않으면 sibling 별로 별도 lazy init path 가 생길 위험). 이 contract 를 façade 의 init block 에 명시.
- **`internal` 가시성 승격**: 신규 sibling 5개의 visibility 를 `internal` 로 두면 현 모듈 외부 호출 차단. multi-module 으로 분리되는 시점에 `public` 으로 승격 필요 — 본 plan 은 single-module 가정.
- **migration 순서**: `applyPendingMigrations()` 의 3개 block 이 단일 `dataStore.edit { }` 안에서 순차 적용된다. runner 로 분리 후에도 동일 트랜잭션 boundary 유지 — Task 4 의 cut-and-paste 가 boundary 를 깨면 첫 launch 에서 partial state 발생 가능. Task 1 의 atomicity test 가 회귀 가드.
- **테스트 zero-edit 가정**: 6개 기존 test 가 모두 façade 의 `setX(...)` / `observeY()` 를 호출 — sibling 분해 후에도 façade 가 동일 시그니처로 위임하므로 변경 없음 가정. 만약 어느 test 가 companion 의 private key 를 reflection 으로 접근한다면 (낮은 확률) 해당 test 는 별도 케어 — Task 1 grep 단계에서 reflection / `Keys` 직접 접근 여부 추가 확인.
- **Migration runner 의 sibling key import 의존성**: runner 가 v1 migration 에서 `SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT` (suppression sibling 소유) 를 mutate 하고, v2 에서 `REPLACEMENT_AUTO_DISMISS_ENABLED/MINUTES` (delivery sibling 소유) 를 mutate, default materialization 에서 `INBOX_SORT_MODE` (delivery sibling 소유) 를 mutate 한다. 즉 runner 는 3개 sibling 의 `Keys` 를 import 해야 함 — 같은 패키지이므로 `internal` 가시성으로 접근 가능. 후속에 새 migration 추가 시 runner 의 단일 책임 (atomic) vs sibling 의 도메인 owner 책임 (key 정의) 사이 경계는 case-by-case 로 결정.
- **`currentNotificationContext` 의 위치**: façade 에 남기는 결정은 cross-domain reader 라는 본질을 보존하기 위함. 후속 plan 에서 `NotificationContextBuilder` 같은 stateless builder 로 추출하면 façade 가 더 얇아진다 — 본 plan 의 ≤ 200 line 목표를 달성하면 충분 (≤ 200 자체가 critical/moderate 둘 다 안전).
- **사이즈 통과 기준**: 705 → ≤ 200 line 목표는 보수적. setter 위임 30개 (1-line × 30 = ~30 lines) + observeSettings (~40 lines, 5개 sibling 의 read 합성) + currentNotificationContext (~13 lines) + composition / singleton boilerplate (~30 lines) + DataStore extension (~3 lines) ≈ 120 lines 합산이라 통과 가능 추정. 미달 시 setter 위임을 sibling 별 namespace expose (`val quietHours: SettingsQuietHoursRepository` 직접 노출) 로 전환하는 fallback — 단 이 fallback 은 caller signature 변경이므로 본 plan scope 내에서는 사용 안 함.

---

## Related journey

본 plan 은 사용자 가시 동작을 바꾸지 않으므로 어떤 journey 의 Observable steps / Exit state / Verification recipe 도 갱신하지 않는다. `docs/journeys/quiet-hours.md` 의 마지막 Known gap bullet ("**code health (2026-04-27)**: `…/SettingsRepository.kt` — file is 705 lines (over 700 critical threshold) …") 에 본 plan 링크가 한 줄 추가된다 (gap 본문 미변경). plan ship 시점에 해당 Known gap bullet 은 "(resolved YYYY-MM-DD, plan `…repository-facade-split`)" prefix 로 갱신되며, journey Change log 에 한 줄 추가된다.
