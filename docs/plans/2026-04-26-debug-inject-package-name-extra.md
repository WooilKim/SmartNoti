---
status: shipped
shipped: 2026-04-26
superseded-by: ../journeys/quiet-hours.md
---

# Debug-Inject Receiver: Caller-Provided `package_name` Extra

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** `journey-tester` (and any future verification recipe) can pin the `packageName` of a debug-injected notification by passing `--es package_name <pkg>` to `DebugInjectNotificationReceiver`. This unblocks positive-case ADB end-to-end capture for journeys whose classifier branches gate on package identity — most immediately `quiet-hours` (the `shoppingPackages = setOf("com.coupang.mobile")` branch) but also any future app-rule / protected-source / persistent-notification scenario that is currently impossible to drive without a real third-party APK installed. Production user-observable behavior is unchanged (debug APK only; release APK does not contain this receiver class at all).

**Architecture:** `DebugInjectNotificationReceiver` already assembles a `CapturedNotificationInput` and runs it through the production `NotificationCaptureProcessor` + `DebugClassificationOverride` pipeline before `NotificationRepository.save`. Today it hardcodes `packageName = SYNTHETIC_PACKAGE` (`"com.smartnoti.debug.tester"`) and `sourceEntryKey = "$SYNTHETIC_PACKAGE|..."`. The change reads an optional `package_name` string extra and, when present + non-blank, substitutes both the `packageName` field and the `sourceEntryKey` prefix. `appName` / `sender` continue to default to `SYNTHETIC_APP_NAME` unless an analogous `--es app_name <label>` is also provided (kept as a small bonus extra so the inbox row label is not always `SmartNotiDebugTester` for non-synthetic packages). All other fields (title, body, force_status, quietHours-from-context) keep their current semantics. The receiver lives in `app/src/debug/`, so dead-strip / release-build invariants are preserved.

**Tech Stack:** Kotlin, Android `BroadcastReceiver`, Robolectric unit test, Bash ADB recipe, journey markdown.

---

## Product intent / assumptions

- Receiver remains debug-APK-only. Adding extras does not change packaging, manifest filters, or release-APK surface area. R8 / source-set isolation already keeps it out of release builds; no new gate needed.
- `package_name` extra is **optional**. When absent or blank, the receiver behaves exactly as today (synthetic `com.smartnoti.debug.tester` package). This protects the existing `priority-inbox` recipe — Task 4 of `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md` — from regressing.
- Caller is responsible for picking a valid Android package string. The receiver does not validate against `PackageManager` (the synthetic package never existed either); classifier / repository code paths already tolerate arbitrary `packageName` strings.
- Substitution applies to two fields and only those two: `CapturedNotificationInput.packageName` and `sourceEntryKey` prefix. Everything else (`appName`, `sender`) keeps `SYNTHETIC_APP_NAME` unless `--es app_name <label>` is also passed (also optional).
- Sentinel marker (`force_status`) and packageName extra are **independent**. A caller can pass either, both, or neither. For the quiet-hours positive case the caller passes `--es package_name com.coupang.mobile` and **omits** `force_status` so the real classifier branch fires.
- `contentSignature` derivation (`"$title|$body|debug-inject|$now"`) does not include packageName today and does not need to change — `now` already provides per-injection uniqueness.
- **Open question for user (Risks 섹션에 남김):** Should we also surface a `--es quiet_hours true` extra to force the `quietHours` flag independently of the system clock + Settings window, so the recipe can be run at any wall-clock time? Default proposal: no — that would create a second debug-only override path parallel to `force_status`, and the in-window post + Settings-window-adjustment workflow already documented in `quiet-hours.md` is sufficient. Add only if a future journey needs it.

---

## Task 1: Failing unit test for package_name extra substitution [SHIPPED via PR #342]

**Objective:** Lock the contract that the receiver, given a `package_name` extra, builds a `CapturedNotificationInput` whose `packageName` matches the extra (and whose `sourceEntryKey` reflects it). Lock the negative case: extra absent → `SYNTHETIC_PACKAGE` retained.

**Files:**
- 신규 또는 확장: `app/src/test/java/com/smartnoti/app/debug/DebugInjectNotificationReceiverTest.kt` (Robolectric).

**Steps:**
1. Confirm whether a Robolectric-style test exists for the receiver today. If yes, extend it; if no, create a minimal one that fakes / wires the four repositories (`NotificationRepository`, `RulesRepository`, `CategoriesRepository`, `SettingsRepository`) — pattern same as existing in-process repository tests under `app/src/test/`.
2. Test cases:
   - **Default path:** broadcast with only `title` + `body` extras → row saved with `packageName == "com.smartnoti.debug.tester"`.
   - **Override path:** broadcast with `--es package_name com.coupang.mobile` → row saved with `packageName == "com.coupang.mobile"` and `sourceEntryKey` starting with `"com.coupang.mobile|debug-inject|"`.
   - **Empty string treated as absent:** broadcast with `--es package_name ""` → falls back to `SYNTHETIC_PACKAGE` (defensive).
   - **Optional `app_name`:** broadcast with `--es app_name "Coupang"` → row's `appName` / `sender` reflect that label; absence keeps `SmartNotiDebugTester`.
3. Tests should be red against current `main`. Run `./gradlew :app:testDebugUnitTest --tests "com.smartnoti.app.debug.DebugInjectNotificationReceiverTest"` to confirm failure.

## Task 2: Add the extra parsing + substitution [SHIPPED via PR #342]

**Objective:** Make Task 1 tests green with the smallest possible diff.

**Files:**
- `app/src/debug/java/com/smartnoti/app/debug/DebugInjectNotificationReceiver.kt`

**Steps:**
1. In `onReceive`, after the existing `forceStatus` parsing, add:
   ```kotlin
   val packageNameExtra = intent.getStringExtra("package_name").orEmpty()
   val appNameExtra = intent.getStringExtra("app_name").orEmpty()
   ```
2. Pass both into `inject(...)` (extend its signature).
3. Inside `inject`:
   - `val effectivePackage = packageNameExtra.ifBlank { SYNTHETIC_PACKAGE }`
   - `val effectiveAppName = appNameExtra.ifBlank { SYNTHETIC_APP_NAME }`
   - Substitute into `CapturedNotificationInput.packageName`, `appName`, `sender`, and `sourceEntryKey = "$effectivePackage|debug-inject|$now"`.
4. Update the KDoc on the class to document the new extras (block comment with the canonical `am broadcast` example for `quiet-hours`).
5. Re-run unit tests — all green. `./gradlew :app:testDebugUnitTest` full suite must stay green (no other test should reference the hardcoded constant).

## Task 3: Quiet-hours verification recipe — add positive-case ADB step

**Objective:** Replace the "두 가지 blocker (b)" admission in `quiet-hours.md` Known gaps with an actual recipe step that exercises the positive case live.

**Files:**
- `docs/journeys/quiet-hours.md` — Verification recipe section: add a step "## 4. Positive-case end-to-end (debug APK only)" with the broadcast command. Known gaps: keep the (a) bundle-preview blocker bullet but mark (b) `DebugInjectNotificationReceiver` blocker as resolved with `→ plan: docs/plans/2026-04-26-debug-inject-package-name-extra.md` annotation. Do not edit Goal / Observable steps / Exit state.

**Steps:**
1. Recipe step body:
   ```bash
   # Adjust Settings quiet-hours window to include current hour first.
   # Then inject a notification whose packageName matches the classifier's
   # shoppingPackages set, with no force_status (let the real branch fire).
   adb -s emulator-5554 shell am broadcast \
     -a com.smartnoti.debug.INJECT_NOTIFICATION \
     -p com.smartnoti.app \
     --es title "QuietHrsPositive$(date +%s)" \
     --es body "쇼핑 알림 (조용한 시간 분기 기대)" \
     --es package_name com.coupang.mobile \
     --es app_name "Coupang"

   # Expect: DB row with status=DIGEST and reasonTags containing "조용한 시간"
   #         and NOT containing "사용자 규칙". Detail entry should render the
   #         "지금 적용된 정책" sub-section under the chip row.
   adb -s emulator-5554 exec-out run-as com.smartnoti.app sqlite3 \
     databases/smartnoti.db \
     "SELECT status,reasonTags FROM notifications WHERE title LIKE 'QuietHrsPositive%' ORDER BY postedAtMillis DESC LIMIT 1;"
   ```
2. Add a Change log entry (date = `$(date -u +%Y-%m-%d)`) summarizing: positive-case path now executable; (b) blocker resolved by package_name extra; (a) bundle-preview entry-point limitation still open.
3. **Do not bump `last-verified`** in this Task — the bump happens in Task 5 after the recipe is actually run on the emulator.

## Task 4: Cross-journey note — debug-inject extras as shared verification primitive

**Objective:** Other journeys that have package-gated branches should know this extra exists so future Known-gap entries can reference it instead of re-discovering the limitation.

**Files:**
- `docs/journeys/notification-capture-classify.md` — Code pointers or a one-line note: "Debug-only inject receiver supports `--es package_name <pkg>` and `--es app_name <label>` extras (debug APK only) — see `app/src/debug/.../DebugInjectNotificationReceiver.kt` and `docs/plans/2026-04-26-debug-inject-package-name-extra.md`."
- (No edits to other journeys in this plan — those will reference the extras organically when their own Known gaps come up for triage.)

## Task 5: End-to-end ADB verification + last-verified bump for `quiet-hours`

**Objective:** Actually run the new positive-case recipe on emulator-5554, observe DIGEST + `조용한 시간` in DB, navigate to Detail, screenshot the "지금 적용된 정책" sub-section, and bump `last-verified`.

**Steps:**
1. `./gradlew :app:assembleDebug && adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
2. Adjust Settings quiet-hours window in-app to include the current hour (per existing recipe step).
3. Run the broadcast from Task 3.
4. Run the sqlite3 query — confirm `status == DIGEST` and `reasonTags` contains `조용한 시간` and does NOT contain `사용자 규칙`.
5. In-app: enter inbox → locate the QuietHrsPositive row → tap to Detail → confirm "지금 적용된 정책" sub-section renders. Screenshot.
6. Bump `last-verified` of `docs/journeys/quiet-hours.md` to today (`date -u +%Y-%m-%d`). Add a Change log entry summarizing the live observation.

## Task 6: Self-review + PR

- `./gradlew :app:testDebugUnitTest` full PASS.
- `./gradlew :app:assembleDebug` PASS. `./gradlew :app:assembleRelease` PASS (receiver still excluded from release source set — no new release-side surface).
- PR title: `feat(debug-inject): add package_name + app_name extras for journey verification`.
- PR body: ADB transcript of the positive-case quiet-hours capture, sqlite query result, Detail screenshot.

---

## Scope

**In:**
- `package_name` + `app_name` optional extras on `DebugInjectNotificationReceiver`.
- Robolectric unit test covering default + override + blank-empty + app_name paths.
- `quiet-hours.md` recipe step for positive-case capture; Known gaps annotation; Change log entry.
- One-line note in `notification-capture-classify.md` Code pointers.
- `last-verified` bump for `quiet-hours.md` after live verification.

**Out:**
- A `quiet_hours` boolean extra (Risks open question — defer unless needed).
- Recipe rewrites for app-rule / protected-source / persistent-notification journeys (separate Known-gap drainages, each becomes its own future plan that can now cite this extra).
- Validation of the supplied `package_name` against installed packages — receiver stays permissive.
- Any change to `DebugClassificationOverride` or `BuildConfig.DEBUG` gating in the production listener.

---

## Risks / open questions

- **사용자 결정 필요 — `quiet_hours` extra:** Should the receiver also accept a `--es quiet_hours true|false` extra to force the `CapturedNotificationInput.quietHours` flag independent of system clock + Settings window? Default proposal in this plan: **no**, because it overlaps with the existing "adjust Settings to include current hour" workflow and adds a second debug override surface alongside `force_status`. Reverse if a future journey demonstrates need.
- **Synthetic packageName in classifier branches we don't intend to test:** If a caller passes a real package that hits a *different* classifier branch (e.g. VIP keyword / persistent / protected-source), the resulting row may be classified differently than the recipe author expected. Mitigation: each recipe should pin its expected branch by choosing a package whose classifier behavior is documented in the relevant journey.
- **`appName` ↔ classifier interaction:** Some classifier signals (e.g. duplicate suppression) key on `(packageName, sender)` tuples. The receiver currently uses `appName == sender == SYNTHETIC_APP_NAME`; the override path keeps `appName == sender == effectiveAppName`. If a future journey needs distinct sender vs appName, that is a separate extra, out of scope here.
- **Source-set / R8 invariants:** The receiver and override resolver are already debug-only. Adding two more `getStringExtra` calls does not change that. Verify `assembleRelease` PASS in Task 6 to be safe.

---

## Related journey

- `docs/journeys/quiet-hours.md` — Known gap "Positive-case ADB end-to-end capture ... blocker (b)" is drained by this plan. On ship, the (b) clause becomes a Change log entry with the recipe and live-observation evidence; (a) bundle-preview blocker remains open and unchanged.
- 참조: `docs/journeys/notification-capture-classify.md` — Code pointers note added so future readers know the debug-inject receiver is parametrizable.
- 참조: `docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md` — same receiver, same override resolver. This plan is purely additive and does not modify priority-inbox behavior.
