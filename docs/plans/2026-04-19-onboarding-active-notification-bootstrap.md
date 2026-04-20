# Onboarding Active Notification Bootstrap Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** When first-time onboarding completes, SmartNoti should immediately inspect notifications already sitting in Android's notification center, run them through the existing SmartNoti capture / classification / routing pipeline, and apply the same effects that newly posted notifications get.

**Architecture:** Reuse `SmartNotiNotificationListenerService` as the single source of truth for notification capture and suppression logic. Add a one-shot onboarding bootstrap trigger that processes `activeNotifications` exactly once at first onboarding completion, rather than duplicating classification in UI code. Keep behavior bounded to first onboarding completion so normal launches do not repeatedly reprocess the tray.

**Tech Stack:** Kotlin, Jetpack Compose, NotificationListenerService, Room, DataStore, Gradle unit tests, Claude Code implementation.

---

## Product intent / assumptions
- Scope this to **first onboarding completion** only.
- Use the notifications already visible in the system notification center at that moment.
- Reuse the same SmartNoti pipeline used by `onNotificationPosted(...)` so existing rules, duplicate handling, persistence, source suppression, and replacement notification behavior stay consistent.
- If current SmartNoti routing would cancel/suppress a source notification, that should also be able to happen for bootstrapped active notifications.
- Do **not** build fake/demo-only data for this.

## Task 1: Add failing tests for one-shot onboarding bootstrap behavior

**Objective:** Define the desired behavior before implementation.

**Files:**
- Create or modify tests around onboarding/bootstrap behavior under `app/src/test/java/com/smartnoti/app/...`
- Likely create a focused test file for the new bootstrap coordinator/helper

**Step 1: Write failing tests**
Cover at least:
1. bootstrap runs only when explicitly requested for first onboarding completion
2. active notifications from other packages are processed through the bootstrap path
3. self notifications / ignored blank group summaries are skipped
4. duplicate processing is prevented if bootstrap is requested again after it already completed

**Step 2: Run targeted tests to verify failure**
Use a focused Gradle command for the new test file.

**Step 3: Implement minimal code later**
Do not implement before the tests fail for the intended reason.

---

## Task 2: Implement a one-shot onboarding bootstrap trigger

**Objective:** Make onboarding completion request a single active-notification pass.

**Files:**
- Modify onboarding completion flow in `app/src/main/java/com/smartnoti/app/navigation/AppNavHost.kt`
- Possibly add a small helper in `app/src/main/java/com/smartnoti/app/notification/...`
- If needed, add a small persisted flag in `app/src/main/java/com/smartnoti/app/data/settings/...`

**Implementation direction:**
- Avoid putting notification-processing logic in Compose/UI.
- Prefer signaling the listener/service layer that onboarding has just completed and a bootstrap pass should run once.
- Ensure this does not fire repeatedly on every future launch.

---

## Task 3: Reuse listener processing for current active notifications

**Objective:** Process notification-center items with the same behavior as newly posted notifications.

**Files:**
- Modify `app/src/main/java/com/smartnoti/app/notification/SmartNotiNotificationListenerService.kt`
- Possibly extract shared logic from `onNotificationPosted(...)` into a reusable internal method that accepts a `StatusBarNotification`

**Implementation direction:**
- Reuse the existing capture flow instead of copy/pasting classification logic.
- Process `activeNotifications` from the listener service when the onboarding bootstrap trigger is consumed.
- Preserve existing guards:
  - skip SmartNoti’s own package
  - skip ignored blank group-summary captures
  - preserve current duplicate counting / persistence / suppression behavior
- If a bootstrapped notification should be cancelled/suppressed according to current routing, let that happen via the existing routing path.

---

## Task 4: Verify onboarding completion still works and bootstrap is one-shot

**Objective:** Confirm no regression in onboarding flow and no repeated tray reprocessing.

**Files:**
- Update/add tests as needed
- Re-run full affected tests

**Verification:**
- onboarding completion still navigates to Home
- the bootstrap request is consumed once
- repeated app resumes / launches do not keep reprocessing the same tray notifications unless a new explicit request is made

---

## Task 5: Run full verification

**Objective:** Validate the implementation end-to-end.

**Commands:**
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

**Optional emulator verification goal:**
- start with notifications already present in the tray before finishing onboarding
- complete onboarding
- confirm SmartNoti Home / DB reflects those existing notifications without waiting for brand-new posts only

---

## Commit guidance
Use a focused commit after verification, for example:
```bash
git add -A
git commit -m "feat: bootstrap active notifications after onboarding"
```
