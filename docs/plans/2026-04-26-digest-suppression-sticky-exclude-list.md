---
status: planned
journey: digest-suppression
---

# Digest Suppression Sticky Exclude List

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** 사용자가 Settings 의 Suppressed Apps 리스트에서 명시적으로 제외(uncheck)한 앱은 그 다음 DIGEST 알림이 와도 `SuppressedSourceAppsAutoExpansionPolicy` 가 다시 추가하지 않는다. 사용자 입장에서는 "한 번 끈 앱이 다음 알림에서 다시 켜져 있다" 는 억울함이 사라지고, "이 앱만큼은 원본 tray 알림을 그대로 보고 싶다" 는 의지가 sticky 하게 보존된다. 다른 모든 앱에 대한 auto-expansion 동작은 동일하게 유지된다 (regression 0).

**Architecture:** `SmartNotiSettings` 에 `suppressedSourceAppsExcluded: Set<String>` 신규 필드 1개 추가. `SettingsRepository` 가 DataStore key 한 개를 추가로 read/write 하고 `setSuppressedSourceAppExcluded(packageName, excluded)` API 를 노출. `SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(...)` 가 `excludedApps` 파라미터를 받고, `packageName in excludedApps` 면 `null` 반환 (현행 fast-fail 가드들과 동급). `SmartNotiNotificationListenerService.processNotification` 가 policy 호출 시 `settings.suppressedSourceAppsExcluded` 를 같이 넘긴다. Settings UI 의 앱 토글 행은 "체크 해제" 가 단순히 `suppressedSourceApps` 에서 제거하는 것에 더해 `suppressedSourceAppsExcluded` 에 추가도 하고, "다시 체크" 는 두 곳 모두에서 일관되게 변경한다 (`SettingsSuppressedAppPresentationBuilder` 가 두 set 의 union/diff 를 토글 상태로 노출).

**Tech Stack:** Kotlin, DataStore Preferences, Jetpack Compose (Settings 화면), JUnit unit tests.

---

## Product intent / assumptions

- 사용자가 Settings 에서 앱을 명시적으로 uncheck 하면 그 의지는 sticky — auto-expansion 이 덮어쓰지 않는다. 이것이 본 plan 의 contract.
- "최초 체크된 적 없던 앱" 과 "사용자가 체크 해제한 앱" 은 의미가 다르다 — 전자는 auto-expansion 대상, 후자는 영구 제외.
- 사용자가 한 번 제외한 앱을 Settings 에서 다시 명시적으로 체크하면 `excluded` 에서 제거되고 auto-expansion 대상으로 복귀한다. (제외 → 체크 → 제외 시퀀스 모두 idempotent.)
- 빈 `suppressedSourceApps` 의 "모든 앱 opt-in (opt-out semantic)" 의미는 그대로 보존. 단, 빈 상태에서 사용자가 한 앱을 제외하면 즉시 `suppressedSourceApps` 가 "그 한 앱을 뺀 모든 앱" 으로 좁아지지 않는다 — opt-out semantic 은 `excludedApps` 와 직교적으로 평가되어야 한다 (Risks 참조).
- 마이그레이션은 noop — 신규 필드는 default `emptySet()` 이고 기존 사용자 동작은 변경 없음.
- **Open question (PM 결정 필요)**: 사용자가 빈 `suppressedSourceApps` 상태 (default) 에서 한 앱을 Settings 에서 uncheck 했을 때, `NotificationSuppressionPolicy` 가 이 앱의 다음 DIGEST 알림을 어떻게 처리할지 — 옵션 A) 즉시 원본 유지 (excluded 는 suppression 자체에서도 빼는 화이트리스트 의미), 옵션 B) auto-expansion 만 차단하고 빈-set opt-out semantic 은 그대로 유지 (이번 plan 의 base 가정). 본 plan 은 옵션 B 로 디폴트 설계하고, A 로 바꾸려면 `NotificationSuppressionPolicy` 수정이 추가로 필요. Risks 섹션의 Q1 참고.

---

## Task 1: Failing test for `SuppressedSourceAppsAutoExpansionPolicy` excluded gate

**Objective:** "excluded set 에 있으면 expansion 이 절대 일어나지 않는다" 는 contract 를 단위 테스트로 고정.

**Files:**
- `app/src/test/java/com/smartnoti/app/notification/SuppressedSourceAppsAutoExpansionPolicyTest.kt` (기존 파일에 `@Test` 추가)

**Steps:**
1. 기존 `SuppressedSourceAppsAutoExpansionPolicyTest` 의 표준 case (DIGEST + global on + non-empty currentApps + new package → expanded) 가 그대로 PASS 하는지 먼저 확인.
2. 신규 case 추가:
   - `excludedApps = setOf("com.foo")`, `decision = DIGEST`, `currentApps = setOf("com.bar")`, `packageName = "com.foo"` → `null`.
   - `excludedApps = setOf("com.foo")`, `currentApps = setOf("com.bar")`, `packageName = "com.baz"` → `currentApps + "com.baz"` (excluded 는 다른 앱에 영향 없음).
   - `excludedApps = setOf("com.foo")`, `decision = SILENT` 등 다른 decision 값에서도 기존 fast-fail 우선순위 유지 (excluded 와 무관하게 `null`).
   - `excludedApps = emptySet()` 이면 모든 기존 케이스가 변동 없이 통과.
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.SuppressedSourceAppsAutoExpansionPolicyTest"` 로 신규 테스트가 컴파일/RED 확인 (signature 변경 전이므로 컴파일 단계에서 실패).

## Task 2: Extend `SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull`

**Objective:** Task 1 의 테스트가 GREEN.

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SuppressedSourceAppsAutoExpansionPolicy.kt`

**Steps:**
1. 시그니처에 `excludedApps: Set<String>` 파라미터 추가 (default 없음 — 호출 site 에서 명시적으로 전달).
2. 기존 fast-fail 가드들과 같은 줄 묶음에 `if (packageName in excludedApps) return null` 추가. `packageName.isBlank()` 와 `currentApps.isEmpty()` 가드 사이에 두는 것이 자연스럽다.
3. KDoc 갱신 — "사용자가 Settings 에서 명시적으로 uncheck 한 앱은 sticky 하게 제외" 한 줄.

## Task 3: Add `suppressedSourceAppsExcluded` field to `SmartNotiSettings`

**Objective:** 도메인 모델 + repository 에 신규 set 을 흘릴 자리를 만든다.

**Files:**
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsModels.kt`
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt`

**Steps:**
1. `SmartNotiSettings` 에 `val suppressedSourceAppsExcluded: Set<String> = emptySet()` 추가. `suppressedSourceApps` 바로 아래에 두고 KDoc 으로 "auto-expansion 영구 제외" 의미 명시.
2. `SettingsRepository` 의 DataStore key 정의 부근에 `KEY_SUPPRESSED_SOURCE_APPS_EXCLUDED = stringSetPreferencesKey("suppressed_source_apps_excluded")` 추가.
3. `observeSettings()` 흐름에서 신규 key 를 read 해 `SmartNotiSettings.suppressedSourceAppsExcluded` 채우기.
4. 신규 API 추가:
   - `suspend fun setSuppressedSourceAppExcluded(packageName: String, excluded: Boolean)` — `excluded=true` 면 set 에 추가 + `suppressedSourceApps` 에서도 제거, `excluded=false` 면 set 에서 제거. (두 key 의 atomic write 는 `dataStore.edit { … }` 한 트랜잭션으로.)
5. 기존 `setSuppressedSourceApps(set)` API 는 호환 유지 — 단 KDoc 에 "이 API 는 excluded set 을 건드리지 않는다 — 호출자는 두 set 의 의미가 정합한지 책임진다" 명시.

## Task 4: Failing repository test for sticky exclude semantics

**Objective:** Repository 레벨에서 toggle 시퀀스 (체크 → uncheck → 다른 알림으로 auto-expand 시도 → 여전히 제외) 가 동작하는지 확정.

**Files:**
- `app/src/test/java/com/smartnoti/app/data/settings/SettingsRepositoryTest.kt` (기존 파일 가정, 없으면 신규)

**Steps:**
1. `setSuppressedSourceAppExcluded("com.foo", excluded = true)` 호출 후 `observeSettings().first()` 의 `suppressedSourceApps` 에 `"com.foo"` 가 없고 `suppressedSourceAppsExcluded` 에 `"com.foo"` 가 있는지 검증.
2. 같은 앱에 대해 `setSuppressedSourceAppExcluded("com.foo", excluded = false)` → `suppressedSourceAppsExcluded` 에서 제거되는지 검증 (이 호출만으로 `suppressedSourceApps` 에는 다시 들어가지 않음 — 사용자가 체크박스를 ON 하면 그건 별도의 `setSuppressedSourceApps` 경유).
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.data.settings.SettingsRepositoryTest"` 로 RED → GREEN 사이클 확인.

## Task 5: Wire excluded set into the listener call site

**Files:**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`

**Steps:**
1. `processNotification(...)` 의 `SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(...)` 호출 시 `excludedApps = settings.suppressedSourceAppsExcluded` 추가.
2. (있다면) 다른 호출 site 도 동일하게 보강.
3. `setSuppressedSourceApps(expanded)` 호출 시 의미상 변경 없음 — excluded 가 차단해서 expansion 자체가 안 일어나기 때문.

## Task 6: Settings UI — sticky uncheck wires to new API

**Objective:** Settings 의 Suppressed Apps 토글이 신규 API 를 호출해 sticky 의지를 보존.

**Files:**
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/smartnoti/app/ui/screens/settings/SettingsSuppressedAppPresentationBuilder.kt`

**Steps:**
1. `SettingsSuppressedAppPresentationBuilder` 가 row 의 `isChecked` 를 계산할 때 `suppressedSourceApps + (capturedApps - suppressedSourceAppsExcluded)` 의미를 반영하도록 보강. (현재 의미 "빈 set = 모든 앱 opt-in" 와 합치되 excluded 가 차감된 set 이 표시됨.)
2. 토글 OFF 콜백을 `setSuppressedSourceApps(currentSet - packageName)` 단독 호출에서 → `setSuppressedSourceAppExcluded(packageName, excluded = true)` 호출로 교체.
3. 토글 ON 콜백은 `setSuppressedSourceAppExcluded(packageName, excluded = false)` + 기존 `setSuppressedSourceApps(currentSet + packageName)` 를 함께 호출 (체크하면 excluded 에서 빠지고 명시 opt-in 도 됨).
4. 기존 "모두 선택 / 모두 해제" 버튼: "모두 해제" 도 모든 가시 앱을 `excludedApps` 에 추가하는 효과로 바꿔 sticky 보존. "모두 선택" 은 가시 앱 모두에 대해 `setSuppressedSourceAppExcluded(_, false)` 를 일괄 호출.

## Task 7: Compose-level smoke test for the toggle wiring

**Objective:** `SettingsSuppressedAppPresentationBuilder` 의 새 입력/출력 시그니처에 대한 단위 테스트 추가 (Compose UI test 가 아니라 builder 의 pure 함수 레벨).

**Files:**
- `app/src/test/java/com/smartnoti/app/ui/screens/settings/SettingsSuppressedAppPresentationBuilderTest.kt`

**Steps:**
1. `excludedApps = setOf("com.foo")` 가 들어왔을 때 row 의 `isChecked = false` 로 표시되는지.
2. `excludedApps = emptySet()` 이고 `suppressedSourceApps = emptySet()` (default opt-out semantic) 일 때 모든 row `isChecked = true`.
3. `excludedApps = setOf("com.foo")`, `suppressedSourceApps = emptySet()` 일 때 `com.foo` 만 `isChecked = false`, 나머지는 `true`.

## Task 8: Update journey doc + Change log

**Files:**
- `docs/journeys/digest-suppression.md`

**Steps:**
1. Known gaps 의 "sticky 제외 리스트는 미구현" bullet 을 "→ plan: `docs/plans/2026-04-26-digest-suppression-sticky-exclude-list.md`" annotation 으로 표시 (gap 본문 텍스트는 변경하지 않음 — 이 plan 이 ship 될 때 plan-implementer 가 "(resolved YYYY-MM-DD ...)" prefix 를 단다).
2. Plan ship 시 (Task 9 직후) Change log 에 한 줄 추가 — date / 한 줄 요약 / commit hash. 본 plan 은 plan-implementer 가 후속 작업으로 수행. (gap-planner 는 여기서 멈춘다.)
3. Observable steps 1 (`expandedAppsOrNull(...)`) 의 한 줄 보강: "사용자가 Settings 에서 명시적으로 uncheck 한 앱은 `suppressedSourceAppsExcluded` 에 들어가 이 단계에서 자동 제외된다" 도 plan-implementer 시점에 반영.

## Task 9: Verification recipe

**Objective:** ADB 로 sticky 제외 동작이 살아있는지 확인.

**Steps:**
```bash
# 0. testnotifier 설치 + onboarding 완료 가정 (digest-suppression journey 의 recipe 와 동일).
adb -s emulator-5554 shell am start -n com.smartnoti.app/.MainActivity
sleep 2

# 1. Settings → Suppressed Apps 진입 → com.smartnoti.testnotifier 의 row 를 OFF.
#    좌표는 uiautomator dump 로 추출 (recipe 본문 참고).

# 2. testnotifier 의 PROMO_DIGEST 시나리오 한 번 더 게시.
adb -s emulator-5554 shell am start -n com.smartnoti.testnotifier/.MainActivity
sleep 2
adb -s emulator-5554 shell input tap <PROMO_DIGEST 좌표>
sleep 2

# 3. 기대: tray 의 com.smartnoti.testnotifier 알림이 그대로 남고
#    smartnoti_replacement_digest_* 채널에는 새 entry 가 게시되지 않는다 (옵션 A 채택 시)
#    또는 replacement 는 게시되지만 다음 onboarding/sweep 에서 testnotifier 가
#    suppressedSourceApps 에 다시 자동 추가되지 않는다 (옵션 B 채택 시).
adb -s emulator-5554 shell dumpsys notification --noredact \
  | grep -E "pkg=com.smartnoti.(testnotifier|app)" | head -20

# 4. Settings 다시 진입 → testnotifier row 가 여전히 OFF 인지 확인 (sticky).
```

---

## Scope

**In:**
- `SuppressedSourceAppsAutoExpansionPolicy` 시그니처 변경 + 한 줄 가드 추가.
- `SmartNotiSettings` 신규 set 필드 1개 + repository 한 API 추가.
- Settings UI 토글이 새 API 호출하도록 wiring.
- 단위 테스트 3건 (policy, repository, presentation builder).
- Journey 문서의 Known-gap bullet 옆 annotation.

**Out:**
- `NotificationSuppressionPolicy` 의 빈-set opt-out semantic 변경 (Risks Q1 참고). 옵션 A 채택 시 본 plan 범위 밖에서 별도 plan 필요.
- "한 번 excluded 된 앱을 일정 기간 후 자동 복귀" 같은 시간 기반 정책 (사용자 요청 없음).
- Settings UI 의 시각적 리디자인 — 토글 표현은 기존 동일.
- 마이그레이션 — 신규 필드 default 가 emptySet 이라 기존 사용자 동작 무변경.
- IGNORE / SILENT 분류의 sticky 제외는 본 plan 의 contract 에 포함하지 않음. DIGEST 만 다룸 (auto-expansion 자체가 DIGEST-only).

---

## Risks / open questions

- **Q1 (PM 결정 필요)**: 빈 `suppressedSourceApps` 상태에서 사용자가 한 앱을 uncheck 하면 본 plan 은 옵션 B (auto-expansion 만 차단) 로 동작. 옵션 A (즉시 원본 유지 = `NotificationSuppressionPolicy` 가 `excludedApps` 도 화이트리스트로 본다) 가 더 사용자 의도에 가깝다는 의견이 가능. plan-implementer 는 옵션 B 로 ship 하고, 사용자 피드백을 기다린 후 옵션 A 로의 전환을 별도 plan 에서 다룬다.
- **Q2**: "모두 해제" 버튼이 가시 앱 전체를 excludedApps 에 넣을 때, 사용자가 필터로 좁혀 본 부분집합만 영향을 받게 할 것인가 vs 모든 캡처 앱을 영향을 받게 할 것인가. 기존 "모두 해제" 가 "현재 필터로 보이는 앱만" 정책이므로 동일 정책 유지가 자연스럽다 (Task 6.4 가정).
- **Q3**: `excludedApps` 와 `suppressedSourceApps` 가 동시에 같은 packageName 을 가지면 (예: 손으로 set 두 개를 직접 편집 / migration bug) 의미 충돌. policy 는 excluded 우선 (auto-expansion 차단) 으로 동작. UI 는 `isChecked = false` 로 표시하고 다음 사용자 토글로 두 set 이 정합 상태로 수렴하게 둔다.
- **Q4**: `OnboardingQuickStartSettingsApplier` 가 첫 로드 시 `setSuppressedSourceApps(...)` 를 호출하는데, 이때 `suppressedSourceAppsExcluded` 는 무엇을 해야 하는가. 본 plan 은 onboarding 시 `excludedApps = emptySet()` 으로 명시 초기화하는 것은 추가하지 않는다 (default 값이 이미 emptySet 이므로). plan-implementer 가 onboarding 경로를 살짝 보고 noop 임을 확인하는 것으로 충분.
- **Q5**: 이 PR 은 코드만 건드리고 ADB recipe (Task 9) 를 plan-implementer 가 직접 돌리는 게 아니라, 후속 journey-tester sweep 에 위임할 수도 있다. plan-implementer 는 Task 9 를 PR 에 옮겨 적고 직접 한 번 실행하는 것을 권장.

---

## Related journey

- [`docs/journeys/digest-suppression.md`](../journeys/digest-suppression.md) — Known gap "sticky 제외 리스트 미구현" 을 본 plan 으로 해결. Plan ship 시 plan-implementer 가 해당 bullet 을 `(resolved YYYY-MM-DD, plan 2026-04-26-digest-suppression-sticky-exclude-list)` prefix 로 갱신하고 Change log 추가.
