# SmartNoti

SmartNoti is an Android Jetpack Compose MVP scaffold for a notification-filtering app that prioritizes important alerts, bundles lower-priority ones into a digest, and explains why each decision was made.

## Current scope

- Android Compose app skeleton
- Onboarding flow
- Home / Priority / Digest / Rules / Settings screens
- Notification detail screen
- Fake repositories and preview data for product demo work
- Pure Kotlin classification rule engine with unit tests

## Project layout

- `app/src/main/java/com/smartnoti/app/...` — app code
- `app/src/test/java/com/smartnoti/app/domain/usecase/...` — pure Kotlin tests for classification logic
- `docs/device-functional-test-checklist.md` — real-device functional checklist
- `docs/real-notification-verification-with-testnotifier.md` — emulator verification flow using a separate real notification sender app

## Real-notification verification

For end-to-end verification, SmartNoti now has a documented flow that uses a separate verifier app package instead of shell-posted notifications.

- Main doc: `docs/real-notification-verification-with-testnotifier.md`
- Verifier project expected as a sibling repo/project: `/Users/wooil/source/SmartNotiTestNotifier`
- GitHub repo: `https://github.com/WooilKim/SmartNotiTestNotifier`
- Verifier package: `com.smartnoti.testnotifier`

This flow is useful when you need to validate:
- onboarding permission gate → quick-start → Home progression
- real notification capture in the emulator
- promo / repeat / important classification outcomes
- Home quick-start effect copy based on actual captured notifications

## Build prerequisites

- JDK 17+
- Android Studio Iguana+ or compatible Android Gradle Plugin support

## Suggested next steps

1. Open in Android Studio.
2. Let Gradle sync.
3. Run unit tests.
4. Connect `NotificationListenerService` and Room/DataStore.

## Verification in this environment

The project was verified locally with:

- Gradle wrapper generation
- Android SDK Platform 34 + Build-Tools 34 installation
- `./gradlew test` passing

If you open this in Android Studio, it should sync and run from the generated wrapper-based setup.
