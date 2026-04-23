# Duplicate Notifications for Under-Configured Users — Suppress Defaults (A + C)

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 새로 설치했거나 설정을 건드리지 않은 사용자가 DIGEST / SILENT 알림을 원본(앱) + SmartNoti replacement 두 번 보는 증상을 제거한다. 앱을 처음 켜자마자 "조용한 알림은 묶여서 온다" 는 제품 약속이 실제로 관측되도록 기본값을 정렬한다. PRIORITY 는 의도상 원본 유지 + 강조 알림이 공존해야 하므로 변경하지 않는다.

**Architecture:**
- (A) `SmartNotiSettings.suppressSourceForDigestAndSilent` 의 default 를 `false → true` 로 바꾸고, 업그레이드 사용자에게는 `suppress_source_migration_v1_applied` DataStore one-shot migration 키로 기존 저장값을 **덮어쓴다**. migration 은 `SettingsRepository` 초기화 시점의 suspend 훅에서 1회 실행.
- (C) `NotificationSuppressionPolicy.shouldSuppressSourceNotification(...)` 에서 global toggle 이 ON 이고 `suppressedApps` 가 empty 일 때 "all packages opt-in" 으로 해석한다. 비어있지 않은 경우 기존 opt-in 화이트리스트 의미를 유지. IGNORE / PRIORITY 분기는 그대로.

**Tech Stack:** Kotlin, Jetpack DataStore Preferences, Gradle unit tests.

---

## Product intent / assumptions

- 제품 약속: "DIGEST/SILENT 은 원본 대신 SmartNoti 가 정리해서 알려준다." 현재 default 조합 (global toggle OFF + empty app list) 에서는 두 알림 모두 관측됨. 이것이 사용자가 관찰한 "duplicate notifications" 증상의 근본 원인.
- **사용자 결정 (이 세션에서 확정):**
  - 선택지 A (default 를 true 로 flip) + C (empty = all) 를 **모두** 적용.
  - 기존 사용자에게도 migration 으로 `suppressSourceForDigestAndSilent = true` 를 강제 주입. 의도적으로 OFF 를 선택했던 사용자 (거의 없을 것으로 추정) 는 다시 꺼야 함 — 명시적 trade-off 로 수용.
- PRIORITY 는 변경하지 않음. PRIORITY 는 원본 + SmartNoti heads-up 강조가 공존해야 한다는 것이 현재 `NotificationSuppressionPolicy` 의 분기 의도.
- empty-set 의미 변화는 **runtime 정책** 변경. 저장된 set 자체는 여전히 empty 로 유지 — 사용자가 어떤 앱을 추가하면 그 순간부터 화이트리스트 의미로 되돌아간다. 이 semantic flip 은 runtime 정책 안에만 존재하므로 migration 이 필요 없다.
- Auto-expansion 정책 (`SuppressedSourceAppsAutoExpansionPolicy`) 은 이 plan 과 독립. empty-set 에서는 어떤 앱도 "추가 필요" 가 아니므로 no-op. 비어있지 않게 된 이후에는 기존 경로 그대로 동작.

---

## Task 1: Failing test — empty suppressedApps + toggle ON suppresses DIGEST/SILENT

**Objective:** (C) 의 새 의미를 단위 테스트로 고정.

**Files:**
- 수정: `app/src/test/java/com/smartnoti/app/notification/NotificationSuppressionPolicyTest.kt`

**Steps:**
1. 신규 케이스: `suppressDigestAndSilent = true`, `suppressedApps = emptySet()`, decision ∈ {DIGEST, SILENT} → `true`.
2. 신규 케이스: 같은 조건 + `decision = PRIORITY` → `false` (PRIORITY 는 empty-set 에도 영향 받지 않음 확인).
3. 신규 케이스: `suppressDigestAndSilent = false`, `suppressedApps = emptySet()` → `false` (global off 는 여전히 승리).
4. 신규 케이스: `suppressedApps = setOf("com.example.other")`, `packageName = "com.android.shell"`, decision = DIGEST → `false` (비어있지 않으면 기존 화이트리스트 의미 유지).
5. IGNORE 분기에 대한 기존 테스트는 그대로 통과해야 함.
6. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.NotificationSuppressionPolicyTest"` 로 실패 확인.

## Task 2: Implement empty-set semantic in `NotificationSuppressionPolicy`

**Objective:** Task 1 이 초록이 되게.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/notification/NotificationSuppressionPolicy.kt`

**Steps:**
1. IGNORE early-return / `suppressDigestAndSilent` 게이트는 유지.
2. `suppressedApps` 가 empty 이면 packageName 체크를 건너뛴다 — 구현은 "empty OR packageName in set" 형태로 정리. 주석에 "empty = all captured packages (opt-out semantics)" 와 그 이유 (default 에서도 제품 약속이 성립하게 하기 위함) 을 남긴다.
3. DIGEST / SILENT 브랜치는 기존대로 `true`, PRIORITY 는 `false` 반환.
4. 전체 테스트 실행 — 기존 test 들 (`NotificationSuppressionPolicyTest`, `SourceNotificationRoutingPolicyTest`, `SuppressedSourceAppsAutoExpansionPolicyTest`) 이 여전히 통과해야 함. 의미 변화를 가정하는 테스트가 있으면 그 곳도 본 task 에서 업데이트.

## Task 3: Failing test — default settings have `suppressSourceForDigestAndSilent = true`

**Objective:** (A) 의 default flip 을 모델 단에서 고정.

**Files:**
- 수정: `app/src/test/java/com/smartnoti/app/data/settings/SmartNotiSettingsTest.kt` (없으면 생성).

**Steps:**
1. `SmartNotiSettings()` default instance 의 `suppressSourceForDigestAndSilent` 가 `true` 인지 단정.
2. `suppressedSourceApps` 는 여전히 `emptySet()` 이 default 인지 단정 (C 의미를 사용하기 위해 의도적으로 비움).
3. 실패 확인.

## Task 4: Implement default flip + one-time migration

**Objective:** fresh install 과 upgrade 양쪽에서 toggle 이 ON 상태가 되도록.

**Files:**
- 수정: `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt` — default `= true`.
- 수정: `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` — migration 훅 + 새 key `suppress_source_migration_v1_applied`.

**Steps:**
1. `SmartNotiSettings` 의 `suppressSourceForDigestAndSilent` default 를 `true` 로 바꾼다.
2. `SettingsRepository` 에 `suspend fun applyPendingMigrations()` 를 추가. 안에서:
   - `SUPPRESS_SOURCE_MIGRATION_V1_APPLIED` (이름은 `booleanPreferencesKey("suppress_source_migration_v1_applied")`) 가 true 면 early return.
   - 아니면 `context.dataStore.edit { prefs -> prefs[SUPPRESS_SOURCE_FOR_DIGEST_AND_SILENT] = true; prefs[SUPPRESS_SOURCE_MIGRATION_V1_APPLIED] = true }` 로 한 번에 기록 (fresh 유저에게도 idempotent 하게 ON 으로 확정됨).
3. 호출 지점: `SmartNotiApplication.onCreate` 의 코루틴 스코프에서 `SettingsRepository.getInstance(this).applyPendingMigrations()` 호출. listener 가 첫 알림을 처리하기 전에 완료되도록 **같은 코루틴 안에서 listener 바인딩/bootstrap 전에 await** 하는지 현재 구조를 확인 후 배치. 만약 listener 가 다른 프로세스 엔트리에서 먼저 살 수 있다면 (NotificationListenerService) `SettingsRepository.observeSettings()` 가 migrated 값을 읽기 시작하는 시점이 migration flush 이후임을 보장하는 쪽이 안전 — 구현 task 에서 lib 구조 점검 후 적절히 위치 결정.
4. 테스트: repository 단위 테스트 (`SettingsRepositoryMigrationTest`) 를 추가해 (a) migration flag 없음 + stored toggle OFF → 실행 후 ON + flag set, (b) migration flag 이미 set → 실행 후 기존 stored 값 보존, (c) migration flag 없음 + stored 값 없음 (fresh) → ON + flag set 을 검증. In-memory DataStore context 또는 기존 `clearAllForTest` 헬퍼 활용.
5. Task 1/3 테스트가 초록이 되었는지 확인.

## Task 5: Manual verification — fresh install + upgrade-from-old

**Objective:** 두 시나리오 모두에서 증상이 사라짐을 관측.

**Steps:**
```bash
# ---- 시나리오 A: Fresh install ----
./gradlew :app:installDebug
adb shell pm clear com.smartnoti.app
# 리스너 권한 부여 후 앱 실행, onboarding 은 Skip 또는 기본값 수락
adb shell cmd notification post -S bigtext -t "AdShop" FreshProm "(광고) 오늘만 할인"
# 원본이 tray 에서 사라지고 smartnoti_replacement_digest 만 남는지 확인
adb shell dumpsys notification --noredact | grep -iE "FreshProm|smartnoti_replacement_digest"

# ---- 시나리오 B: Upgrade from pre-migration state ----
# 1) migration 이전 상태를 재현: 이전 버전 APK 로 깔거나, 현재 APK 로 pm clear 후
#    강제로 datastore 에 suppressSourceForDigestAndSilent=false 를 기록하는
#    상태를 만든다. 가장 현실적인 재현: debug build 의 hidden devtool 탭 또는
#    ADB 로 prefs xml 을 덮어쓰기 (DataStore proto 는 간접 경로). 구현 시 방법을 하나 골라 PR 본문에 기술.
# 2) 새 빌드 설치/기동 → applyPendingMigrations 실행
# 3) Settings → "원본 알림 자동 숨김" 토글이 ON 으로 표시되는지 UI 확인
# 4) 시나리오 A 와 동일한 DIGEST 알림 게시 → 원본 사라지는지 확인

# ---- 시나리오 C: idempotency ----
# 앱 재시작 여러 번. toggle 을 OFF 로 수동 변경 후 재시작 → 여전히 OFF 유지 (migration 재실행되지 않음) 확인.
```

결과를 PR 본문에 세 항목 체크 + 관련 `dumpsys` snippet 으로 첨부.

## Task 6: Update journey docs

**Files:**
- `docs/journeys/digest-suppression.md`
  - Preconditions 의 두 번째/세 번째 bullet 수정: "default 로 `suppressSourceForDigestAndSilent = true`", "`suppressedSourceApps` 가 비어있으면 캡처된 모든 앱이 opt-in 으로 해석됨 (비어있지 않으면 그 리스트가 화이트리스트)".
  - Observable steps 에서 auto-expansion 설명은 유지하되, empty-set 시나리오에서는 expansion step 이 건너뛰어진다는 짧은 각주 추가.
  - Known gap (sticky 제외 리스트 미구현) 은 그대로 유지 — 이 plan 과 무관.
  - Change log 에 `2026-04-24: ...` 라인 추가: default flip + empty-set all-packages semantic + migration 요약 + commit 해시.
- `docs/journeys/silent-auto-hide.md`
  - Preconditions / 관련 문구에 동일한 empty-set 의미를 반영. (SILENT 도 동일 로직을 사용하므로 누락 시 drift 발생.)
  - Change log 에 동일 날짜 라인 추가.
- `docs/journeys/README.md` Verification log 하단에 이번 변경 1행 기록 (행동 드리프트 교정).

## Task 7: Self-review + PR

- `./gradlew :app:testDebugUnitTest :app:lintDebug` green.
- PR 제목: `fix(suppression): default-on toggle + empty-set = all apps, with one-shot migration`.
- PR 본문: 시나리오 A/B/C 결과 + migration 동작 설명 + "OFF 로 명시 전환했던 사용자도 한 번 덮어씀" 공지 (user-visible note 후보).

---

## Scope

**In:**
- `SmartNotiSettings` default flip.
- `SettingsRepository` 의 one-shot migration 훅.
- `NotificationSuppressionPolicy` 의 empty-set semantics.
- 관련 단위 테스트.
- digest-suppression / silent-auto-hide journey 갱신.

**Out:**
- PRIORITY 의 suppression 동작 (변경 없음).
- UI: "자동 숨김" 토글/앱 리스트 카피 변경은 별도 plan. 현재 스크린은 토글 ON 이 default 이므로 혼란 가능성 낮음.
- Auto-expansion 정책 본체의 리팩터링.
- Onboarding 의 PROMO_QUIETING 프리셋 변경.
- 사용자 재-opt-out 을 위한 "후회 방지" UI (예: 업그레이드 직후 Home 에 "자동 숨김이 켜졌습니다 · 되돌리기" 배너) — Risks 에 남기고 필요 시 후속 plan.

## Risks / open questions

- **의도적 OFF 사용자의 재-ON:** migration 이 저장값을 덮어쓰므로, "나는 원본도 보고 싶다" 고 명시적으로 OFF 했던 사용자는 업그레이드 직후 한 번 다시 꺼야 한다. 현재 가정은 "그런 사용자는 매우 소수". 걱정되면 이 plan 과 별개로 업그레이드 직후 1회성 고지 배너를 후속 plan 으로 추가.
- **empty-set 의미의 UI 반영:** Settings 의 "앱 선택 리스트" 가 비어 있으면 UI 에는 "아무 것도 선택 안 됨" 처럼 보일 수 있다. 정책은 뒤에서 "all apps" 처럼 동작하므로 약간의 인지 부조화. 이번 plan 은 runtime 정책만 바꾸고 UI 카피는 그대로 둔다 — 후속 `ui-ux-inspector` 발견 시 별도 plan.
- **Migration 타이밍 — listener 와의 경쟁:** `applyPendingMigrations` 가 완료되기 전에 `NotificationListenerService` 가 첫 알림을 처리하기 시작할 가능성. `observeSettings()` 가 DataStore 변경을 flow 로 관측하므로 migration flush 직후의 값은 다음 emit 으로 들어오지만, migration 이전 `false` 로 한두 건이 처리되어 원본이 tray 에 남는 끝자락 경쟁이 존재. 구현 task 에서 "Application.onCreate 의 첫 suspend job 으로 await 한 뒤 리스너 바인딩 promote" 또는 "listener 진입 시 migration 먼저 await" 중 하나를 고른다 — 둘 다 수용 가능하면 전자가 더 간단.
- **DataStore proto vs Preferences:** 현재 `preferencesDataStore(name = "smartnoti_settings")` 기반. migration 은 같은 store 안에서 key 한 개로 구현하면 충분하므로 proto migration 필요 없음.
- **Auto-expansion 과의 상호작용:** empty-set 상태에서 DIGEST 가 들어오면 `SuppressedSourceAppsAutoExpansionPolicy` 가 app 을 추가하려 할 수 있다 — 추가되는 순간 empty-set 에서 "화이트리스트 1개" 로 의미가 전환된다. 동작 자체는 문제 없음 (해당 app 은 어차피 계속 suppress 됨), 하지만 의미 전환이 암묵적이다. 구현 시 auto-expansion 쪽에 "이미 empty-set 으로 모두 포함되는 상태면 expand 하지 않는다" no-op 가드를 추가할지 결정 — open question.

## Related journey

- 주요: [`digest-suppression`](../journeys/digest-suppression.md) — Known gap "신규/미설정 사용자에게 DIGEST/SILENT 가 이중으로 뜬다" (본 plan 으로 해소 예정).
- 부차: [`silent-auto-hide`](../journeys/silent-auto-hide.md) — 동일 policy 경로를 공유하므로 문서 동기화 필요.
