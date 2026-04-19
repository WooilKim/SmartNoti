# Onboarding Quick-Start Auto-Suppression Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** When the user accepts SmartNoti onboarding quick-start recommendations, SmartNoti should automatically enable source suppression for the relevant apps already present in the notification tray so Digest / Silent-targeted notifications can actually disappear from the system notification center, not just be reclassified in-app.

**Architecture:** Extend onboarding quick-start application so it writes both rule state and suppression settings. Use already-captured / bootstrapped notifications to derive the affected app package set instead of asking the user to configure apps manually. Keep this tied to quick-start acceptance, not to general app launch. Reuse existing settings and listener suppression logic rather than inventing a second suppression path.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore settings, NotificationListenerService suppression path, Room-backed captured notifications, Gradle unit tests.

---

## Product intent / assumptions
- This should happen when the user taps `이대로 시작할게요` in onboarding quick-start.
- It should preserve the user's current product intent: immediate usefulness without per-app setup.
- The suppression target app list should be inferred from the currently captured / bootstrapped notifications, especially apps whose notifications are being classified into Digest / Silent by the selected quick-start presets.
- Important / Priority notifications must not be accidentally suppressed.
- Avoid fake/demo-only behavior.

## Task 1: Add failing tests for quick-start suppression setting application

**Objective:** Define the expected behavior before implementation.

**Files:**
- Add a focused test file under `app/src/test/java/com/smartnoti/app/ui/screens/onboarding/...`
- Possibly extend existing onboarding quick-start applier tests if present

**Required behaviors to test:**
1. accepting quick-start presets can produce both rule updates and suppression-setting updates
2. when promo/repeat presets are selected and current captured notifications exist, the related packages are added to suppressed source apps
3. `suppressSourceForDigestAndSilent` becomes enabled when suppression-targetable presets are selected
4. important-only quick-start selection does not blindly suppress all captured apps
5. existing suppressed app selections are preserved/merged rather than destructively wiped unless explicitly intended

Run the new targeted tests and confirm they fail first.

---

## Task 2: Introduce a small onboarding quick-start settings applier

**Objective:** Separate settings-side onboarding application from UI code.

**Files:**
- Create a helper near onboarding quick-start logic, likely under `app/src/main/java/com/smartnoti/app/ui/screens/onboarding/...`
- Modify `OnboardingScreen.kt` to call the helper instead of writing only rules directly

**Implementation direction:**
- Keep Compose code thin.
- Compute which package names should be suppression targets from currently available captured notifications.
- Only include packages whose notifications are relevant to selected quieting/bundling presets (promo/repeat), not every package.

---

## Task 3: Apply suppression settings during onboarding acceptance

**Objective:** Make `이대로 시작할게요` write rules plus suppression settings.

**Files:**
- Modify `OnboardingScreen.kt`
- Potentially use `NotificationRepository` and `SettingsRepository`

**Implementation direction:**
- When onboarding quick-start is accepted:
  - merge quick-start rules as before
  - inspect current captured/bootstrapped notifications
  - derive suppression target packages for quick-start quieting/bundling presets
  - set `suppressSourceForDigestAndSilent = true` when appropriate
  - merge the target package set into `suppressedSourceApps`
- Keep `직접 설정할게요` behavior conservative unless explicitly needed otherwise.

---

## Task 4: Verify the suppression path remains consistent with existing listener routing

**Objective:** Ensure settings written by onboarding are enough to trigger the existing suppression/cancel logic.

**Files:**
- Add/adjust tests if needed around onboarding-applied settings and listener expectations

**Verification:**
- quick-start selection including promo/repeat should create a settings state that existing listener routing already understands
- important-only path should not accidentally create noisy suppression behavior

---

## Task 5: Run full verification

**Commands:**
```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME='/Users/wooil/Library/Android/sdk'
export ANDROID_SDK_ROOT='/Users/wooil/Library/Android/sdk'
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew testDebugUnitTest
./gradlew :app:assembleDebug
```

**Optional emulator verification target:**
- preload tray notifications with SmartNotiTestNotifier
- complete onboarding with quick-start defaults
- confirm affected Digest/Silent notifications are not just captured in Home, but are also actually hidden/cancelled from the system tray where current Android/device behavior allows it

---

## Commit guidance
Use a focused commit message such as:
```bash
git add -A
git commit -m "feat: auto-enable source suppression from onboarding quick start"
```
