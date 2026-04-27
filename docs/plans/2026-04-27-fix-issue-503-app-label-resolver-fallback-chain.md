---
status: shipped
shipped: 2026-04-28
fixes: 503
last-updated: 2026-04-28
superseded-by: docs/journeys/notification-capture-classify.md
---

# Fix issue #503 — appName falls back to packageName for 16 popular apps

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task. P1 release-prep gate; do not skip the failing-test step or the ADB end-to-end re-verification on R3CY2058DLJ.

**Goal:** SmartNoti 가 캡처한 알림의 `appName` 컬럼에 친절한 사용자 라벨이 들어간다 (Gmail, 쿠팡, 하나카드페이, 네이버, YouTube, LinkedIn 등). 현재 16개 패키지에서 `appName == packageName` 인 raw 한 fallback 이 정리함/디테일/홈 등 모든 UI 표면에 노출되는 회귀를 해소한다. 신규 캡처는 즉시 정상 라벨로 저장되고, 기존 `appName == packageName` 행은 1회성 cold-start migration 으로 재해결된다.

**Architecture:** 결함의 진원지는 `SmartNotiNotificationListenerService.resolveApplicationLabel(packageName)` (현재 `getApplicationInfo + getApplicationLabel.toString()` 한 단계 + 모든 `Exception` 을 packageName fallback 으로 swallow). 이를 (a) 새 `AppLabelResolver` (pure Kotlin seam, PackageManager 만 의존) 로 추출해 fallback chain 을 명시화하고, (b) listener / `NotificationPipelineInputBuilder` 양쪽이 같은 resolver 를 사용하도록 wiring 하며, (c) 반복 호출 비용을 막기 위해 in-process per-package memoization (앱 업그레이드/제거 invalidation) 을 추가한다. (d) 기존 DB 회수는 `MigratePromoCategoryActionRunner` 와 동일한 cold-start runner 패턴 + `SettingsRepository` flag 가드로 1회만 실행되는 `MigrateAppLabelRunner` 가 담당.

**Tech Stack:** Kotlin, Android `PackageManager`, Room (in-place UPDATE), DataStore (idempotent flag), Gradle JUnit/Robolectric unit tests, ADB end-to-end on R3CY2058DLJ.

---

## Product intent / assumptions

- **Decision (no user input needed)**: 친절한 라벨이 진짜로 없는 시스템 패키지 (예: 일부 OEM background service) 는 packageName 을 마지막 fallback 으로 허용한다 — UI 가 빈 문자열을 렌더하는 것보다 packageName 이 차라리 정직하다. 단, fallback 에 도달하기 전에 `pm.getNameForUid(uid)` / `applicationInfo.loadLabel(pm)` / `pm.getApplicationLabel(applicationInfo)` 세 단계를 모두 시도해야 한다.
- **Decision (no user input needed)**: Resolver 의 catch 절은 더 이상 `Exception` 을 통째로 삼키지 않는다. `NameNotFoundException` / `ResourcesNotFoundException` 만 명시적으로 catch 하고, 그 외는 `DiagnosticLogger` (PR #485, default OFF) 가 활성일 때 한 줄 로그로 남기고 다시 raise — 이렇게 하면 사용자가 toggle 을 켰을 때 H1-H5 가설 중 어느 가지가 실패했는지 즉시 진단 가능하다.
- **Decision (no user input needed)**: Migration 은 cold-start 1회. Runner 는 `appName = packageName` 인 row 만 batch UPDATE 하고, resolver 가 새 라벨을 못 찾는 패키지는 row 를 그대로 둔다 (다음 launch 에서 OS 라벨이 회복되면 또 시도).
- **Open question (surface in Risks)**: 매 알림 캡처마다 PackageManager 를 다시 calling 할지 (always-fresh) vs 한 번 resolve 후 in-process LRU cache 에 보관할지 (faster). 본 plan 의 default 는 **per-package memoization + 앱 install/upgrade/remove broadcast 시 invalidation**. 사용자가 always-fresh 를 원하면 이 의사결정만 뒤집으면 됨 — 코드 영향은 resolver 한 곳.

---

## Task 1: Failing tests for AppLabelResolver fallback chain [IN PROGRESS via PR #507]

**Objective:** 회귀를 코드로 고정. 5단계 P1 gate 의 "tests-first" 칸.

**Files (new):**
- `app/src/test/java/com/smartnoti/app/notification/AppLabelResolverTest.kt`

**Steps:**
1. `ApplicationInfo` + `PackageManager` mock (Robolectric or pure Mockito) 를 사용해 다음 케이스를 작성, 모두 RED 로 실패하는지 확인.
   - **Happy path**: `pm.getApplicationInfo("com.google.android.gm", 0)` 가 정상 `ApplicationInfo` 반환 + `applicationInfo.loadLabel(pm)` 이 `"Gmail"` 반환 → resolver 가 `"Gmail"` 반환.
   - **loadLabel returns blank**: `loadLabel` 이 빈 CharSequence 반환 → resolver 가 `pm.getApplicationLabel(applicationInfo)` 으로 fallback → 비어있지 않은 라벨 반환. (둘은 OEM 에 따라 결과가 다를 수 있음.)
   - **loadLabel returns packageName-string**: `loadLabel` 이 `"com.coupang.mobile"` 자체를 반환 (Android 의 known degraded behavior) → resolver 가 packageName 과 동일 문자열을 감지하고 다음 단계 (`pm.getNameForUid(uid)`) 를 시도해야 함.
   - **NameNotFoundException**: `getApplicationInfo` 가 throw → resolver 가 `pm.getNameForUid(0)` fallback 시도, 그것도 null 이면 마지막에 packageName 반환 (silent log 1줄, **but never throws**).
   - **ResourcesNotFoundException**: `loadLabel` 이 throw → catch 되어 다음 단계로 진행.
2. 각 케이스마다 expected vs actual 을 assert 하는 한 줄 명명: `resolves Gmail label when ApplicationInfo present`, `falls back to getNameForUid when loadLabel returns packageName`, …
3. `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.notification.AppLabelResolverTest"` → RED 5건 확인.

## Task 2: Implement AppLabelResolver with explicit fallback chain

**Objective:** Task 1 의 5케이스 GREEN.

**Files (new):**
- `app/src/main/java/com/smartnoti/app/notification/AppLabelResolver.kt`

**Files (touched):**
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` — `resolveApplicationLabel` 제거하고 새 resolver 의 인스턴스 위임.

**Steps:**
1. `class AppLabelResolver(private val packageManager: PackageManager)` 정의. `fun resolve(packageName: String): String` 시그니처.
2. Fallback chain (각 단계에서 결과가 비거나 packageName 과 동일하면 다음 단계):
   1. `applicationInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() && it != packageName }`
   2. `pm.getApplicationLabel(applicationInfo).toString().takeIf { ... }` — H1 의 가설 대로 두 API 가 다른 라벨을 반환하는 디바이스가 실제로 존재.
   3. `pm.getNameForUid(applicationInfo.uid)?.takeIf { ... }` — 제3 가설.
   4. 마지막 fallback: packageName (UI 안 깨지게).
3. Catch 는 `NameNotFoundException`, `ResourcesNotFoundException`, 그 외 `RuntimeException` (시스템 컨텍스트 권한 부족 케이스) 으로 분류해 각각 다음 단계로 넘어가되, **`Exception` 통째 swallow 금지**.
4. `DiagnosticLogger` (PR #485) 가 활성이면 fallback 단계 + 사유를 `app_label_resolver` 카테고리로 1줄 기록. Default OFF 라 production 영향 없음.
5. Listener 에서 `appNameLookup = appLabelResolver::resolve` 로 wiring 변경. `NotificationPipelineInputBuilder` 의 lookup 시그니처는 그대로 유지 (`(String) -> String`).
6. `./gradlew :app:testDebugUnitTest` 전체 실행 → 새 5건 GREEN, 기존 회귀 0건.

## Task 3: Per-package memoization + invalidation on package install/upgrade/remove

**Objective:** Open question 의 default 결정 — once-per-package + cached. 매 알림마다 PackageManager round-trip 비용을 절감하면서, 앱이 업그레이드되어 라벨이 바뀌는 케이스도 다음 호출에서 정확히 반영.

**Files (touched):**
- `app/src/main/java/com/smartnoti/app/notification/AppLabelResolver.kt` — `ConcurrentHashMap<String, String>` 캐시 추가. Cache hit 면 즉시 반환, miss 면 위 chain 실행 후 결과 저장.
- `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt` (또는 기존 BroadcastReceiver 가 있다면 거기) — `Intent.ACTION_PACKAGE_REPLACED` / `ACTION_PACKAGE_REMOVED` / `ACTION_PACKAGE_ADDED` 수신 시 해당 packageName 의 cache 항목 invalidate.

**Files (new tests):**
- `app/src/test/java/com/smartnoti/app/notification/AppLabelResolverCacheTest.kt`
  - 같은 packageName 두 번 resolve → PackageManager 호출 횟수가 1번이어야 함 (verify mock).
  - `invalidate(packageName)` 후 다시 resolve → 호출 2번 + 새 라벨 반환.

**Steps:**
1. Cache field + lock-free read path 작성.
2. `fun invalidate(packageName: String)` / `fun clearAll()` API 노출.
3. Listener `onCreate` 에서 BroadcastReceiver 등록 (`PackageReceiver`), `onDestroy` 에서 unregister. Receiver 는 resolver 의 invalidate 호출만 담당.
4. 단위 테스트 GREEN.

## Task 4: MigrateAppLabelRunner — re-resolve appName == packageName rows

**Objective:** 이미 raw packageName 으로 저장된 16 패키지 (실기에서 확인됨) 의 row 를 cold-start 에서 1회 재해결.

**Files (new):**
- `app/src/main/java/com/smartnoti/app/data/local/MigrateAppLabelRunner.kt` — `MigratePromoCategoryActionRunner` 패턴 그대로 mirror (idempotent flag, suspend `run()`).
- `app/src/test/java/com/smartnoti/app/data/local/MigrateAppLabelRunnerTest.kt` — Room in-memory DB + fake resolver.

**Files (touched):**
- `app/src/main/java/com/smartnoti/app/data/local/NotificationDao.kt` — 새 query: `@Query("SELECT DISTINCT packageName FROM notifications WHERE appName = packageName") suspend fun selectPackagesNeedingLabelResolution(): List<String>` + `@Query("UPDATE notifications SET appName = :label WHERE packageName = :pkg AND appName = packageName") suspend fun updateAppLabel(pkg: String, label: String): Int`.
- `app/src/main/java/com/smartnoti/app/data/settings/SettingsRepository.kt` — 새 flag `app_label_resolution_migration_v1_applied` (`isAppLabelResolutionMigrationV1Applied` / `setAppLabelResolutionMigrationV1Applied`).
- `app/src/main/java/com/smartnoti/app/MainActivity.kt` (또는 `MigratePromoCategoryActionRunner` 가 wire 된 같은 진입점) — runner 호출 추가, 기존 runner 와 동일한 lifecycle gate 사용.

**Steps:**
1. Runner: flag 읽기 → 이미 적용 시 즉시 return. 아니면 `selectPackagesNeedingLabelResolution()` → 각 packageName 에 `appLabelResolver.resolve(pkg)` → 결과가 packageName 과 다르면 batched `updateAppLabel(pkg, label)`. 모든 패키지 처리 후 flag flip.
2. Batch 단위는 packageName 당 한 번 (DISTINCT 라 16건 안팎 — 부담 없음). 수천 row 를 직접 UPDATE 하지 않고 packageName 인덱스 활용.
3. Runner 단위 테스트:
   - Fake 16 packageName fixtures, fake resolver 가 각각 친절한 라벨 반환 → 16건 update + flag = true.
   - 두 번 호출 → 두 번째는 no-op (flag 가드).
   - Resolver 가 일부 packageName 은 fallback (packageName 그대로) 반환 → 그 row 는 update 되지 않고 남음 (다음 launch 에서 또 시도 가능).
4. `./gradlew :app:testDebugUnitTest` GREEN.

## Task 5: Update notification-capture-classify journey doc [SHIPPED via PR closure-503]

**Files:**
- `docs/journeys/notification-capture-classify.md`
  - **Code pointers** 절에 `notification/AppLabelResolver` 추가 (resolver + fallback chain 설명, plan 링크).
  - **Code pointers** 절에 `data/local/MigrateAppLabelRunner` 추가 (M2 cold-start migration, plan 링크).
  - **Change log** 에 2026-04-27 (또는 시스템 시계 기준 시점) 한 줄: "Issue #503 — appName fallback chain explicit + cold-start migration. Recipe verified end-to-end on R3CY2058DLJ. plan: docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md".
  - **`last-verified`** frontmatter 는 ADB recipe 가 GREEN 일 때만 bump.
- (선택) `docs/journeys/categories-management.md` — appLabel 표시는 categories 화면에서도 노출되므로 references 만 한 줄 추가 (Optional).

## Task 6: ADB end-to-end verification on R3CY2058DLJ [SHIPPED via PR closure-503]

**Objective:** P1 5-step gate 의 마지막 칸 — 실제 디바이스에서 친절한 라벨이 회수됐음을 SQL 로 증명.

**Steps:**
```bash
# 1. Fresh debug-build install
./gradlew :app:installDebug
adb -s R3CY2058DLJ shell am force-stop com.smartnoti.app
adb -s R3CY2058DLJ shell am start -n com.smartnoti.app/.MainActivity
sleep 5  # cold-start migration 실행 시간

# 2. DB pull + 회귀 row 검사 — 16 패키지가 친절한 라벨로 회복됐는지
adb -s R3CY2058DLJ shell run-as com.smartnoti.app cat databases/smartnoti.db > /tmp/smartnoti-after.db
sqlite3 /tmp/smartnoti-after.db "SELECT DISTINCT packageName, appName FROM notifications WHERE packageName != appName ORDER BY packageName;"

# 3. 16건 모두 packageName != appName (즉, 친절한 라벨로 갱신) 인지 확인
# Gmail row → appName = "Gmail", 쿠팡 row → "쿠팡", 하나카드 row → "하나카드페이", …

# 4. 새 알림이 정상 라벨로 저장되는지 신규 캡처 테스트
adb -s R3CY2058DLJ shell cmd notification post -S bigtext -t "Test" Issue503Capture "fallback chain test"
sqlite3 /tmp/smartnoti-after.db "SELECT packageName, appName FROM notifications WHERE title = 'Issue503Capture' ORDER BY postedAtMillis DESC LIMIT 1;"

# 5. (Optional) DiagnosticLogger toggle ON → fallback path 로그 확인
```

기록물 (PR 본문 첨부): SQL output before/after + 16 패키지 라벨 회복 표.

## Task 7: Self-review + PR [SHIPPED via PR closure-503]

- 모든 단위 테스트 GREEN (resolver + cache + runner + 기존 회귀 0건).
- ADB end-to-end 증거 (Task 6) PR 본문 첨부.
- PR 제목: `fix(#503): explicit appName fallback chain + cold-start re-resolution migration`.
- Journey doc 변경 포함.
- Closes #503.

---

## Scope

**In scope:**
- `AppLabelResolver` 추출 + fallback chain 명시화.
- Per-package in-process cache + install/upgrade broadcast invalidation.
- `MigrateAppLabelRunner` cold-start 1회 회수.
- `notification-capture-classify` journey 갱신.
- ADB end-to-end 검증.

**Out of scope:**
- Multi-locale 라벨 (사용자 시스템 locale 만 사용 — Android 기본 동작).
- 사용자가 직접 appName 을 override 하는 UI (별도 plan).
- 라벨 변경을 푸시 알림으로 surface (불필요).
- DiagnosticLogger 의 default 를 ON 으로 바꾸기 (별도 의사결정).

---

## Risks / open questions

- **Cache invalidation race**: 사용자가 앱을 업그레이드하는 동안 동시에 알림이 도착하면 stale 라벨이 한 번 저장될 수 있다. 다음 알림에서는 새 BroadcastReceiver invalidation 이후 라벨이 회복되므로 영향 미미 — 별도 합의 없이 수용.
- **System packages with no displayable label**: `getNameForUid` 까지 모두 빈 값을 반환하는 OEM 시스템 패키지가 일부 존재할 수 있다. 마지막 fallback 으로 packageName 을 사용 — UI 가 깨지지는 않는다.
- **Migration 비용**: DISTINCT packageName 16건 안팎이라 부담 없음. 만약 다른 사용자 디바이스에서 수백 패키지가 영향받는다면 batch 처리 필요 (현재는 packageName 단위 loop 면 충분).
- **Resolver context (foreground vs background)**: H3 가설 — Android 12+ 의 일부 케이스에서 NotificationListener 의 PackageManager 가 system context 라 user-installed 앱 라벨을 못 찾을 가능성. 본 plan 의 fallback chain (`getNameForUid` 추가) 이 이를 우회. 만약 그래도 실패하면 `LauncherApps.getApplicationInfo(packageName, 0, Process.myUserHandle())` 추가 단계가 필요할 수 있음 — Task 6 의 ADB 결과로 판단.
- **Open question (surfaced for user judgment)**: 매 알림 캡처마다 PackageManager 를 호출할지 (always-fresh, ~1ms 비용 × N 알림) vs 한 번 resolve 후 cache (faster, 단 invalidation broadcast 누락 시 stale 가능). **본 plan 의 default: per-package memoization + 앱 install/upgrade/remove broadcast invalidation**. 사용자가 always-fresh 를 선호하면 Task 3 를 drop 하고 resolver 만 그대로 두면 됨.
- **DiagnosticLogger 의존**: 본 plan 은 PR #485 (DiagnosticLogger) 의 카테고리 확장만 한다 — diagnostic toggle 자체의 기본값은 변경하지 않는다.

---

## Related journey

- [`notification-capture-classify`](../journeys/notification-capture-classify.md) — appName 컬럼은 이 journey 의 step 3 (extras parsing + PackageManager 라벨 조회) 에서 채워짐. 본 plan 이 shipped 되면 해당 journey 의 Known gap (현재 미기재 — issue 가 user-filed 라 journey 에 등록 전) 를 본 plan 의 "→ plan: …" 링크로 대체하지 않고 Change log 에 직접 기록한다.

---

## Change log

- 2026-04-27: Plan drafted (#504).
- 2026-04-27: Tasks 1–4 shipped via PR #507 (commit `73c63b5`) — `AppLabelResolver` with explicit fallback chain (`loadLabel` → `getApplicationLabel` → `getNameForUid` → packageName), per-package memoization, install/upgrade/remove broadcast invalidation, `MigrateAppLabelRunner` cold-start one-shot migration gated by `app_label_resolution_migration_v1_applied` Settings flag.
- 2026-04-28: Tasks 5–7 (this PR) — ADB end-to-end re-verification on R3CY2058DLJ. DB row count `appName == packageName` = **0** (down from 16 distinct broken packages). All 61 distinct packages resolve to friendly labels (Gmail / 쿠팡 / 하나카드 / NAVER / YouTube / LinkedIn / 카카오톡 / 신한은행 / Discord / Disney+ / 캐치테이블 / 토스 / 당근 / 스타벅스 등). Migration runner ran idempotently at cold-start, flag flipped. `notification-capture-classify` journey Code pointers + Change log updated. Plan flipped to `status: shipped`. Closes #503.
