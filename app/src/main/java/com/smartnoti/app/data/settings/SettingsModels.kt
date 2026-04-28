package com.smartnoti.app.data.settings

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.InboxSortMode
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode

data class SmartNotiSettings(
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 23,
    val quietHoursEndHour: Int = 7,
    val digestHours: List<Int> = listOf(12, 18, 21),
    val priorityAlertLevel: String = AlertLevel.LOUD.name,
    val priorityVibrationMode: String = VibrationMode.STRONG.name,
    val priorityHeadsUpEnabled: Boolean = true,
    val priorityLockScreenVisibility: String = LockScreenVisibilityMode.PRIVATE.name,
    val digestAlertLevel: String = AlertLevel.SOFT.name,
    val digestVibrationMode: String = VibrationMode.LIGHT.name,
    val digestHeadsUpEnabled: Boolean = false,
    val digestLockScreenVisibility: String = LockScreenVisibilityMode.PRIVATE.name,
    val silentAlertLevel: String = AlertLevel.NONE.name,
    val silentVibrationMode: String = VibrationMode.OFF.name,
    val silentHeadsUpEnabled: Boolean = false,
    val silentLockScreenVisibility: String = LockScreenVisibilityMode.SECRET.name,
    // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md` Task 4:
    // default ON. Combined with the empty `suppressedSourceApps` set below
    // and the empty-set semantic in `NotificationSuppressionPolicy`, this
    // ensures fresh installs deliver the product promise — DIGEST/SILENT
    // surface as a SmartNoti replacement instead of duplicating the
    // original app's notification. Existing users get the same value
    // injected once via `SettingsRepository.applyPendingMigrations()`.
    val suppressSourceForDigestAndSilent: Boolean = true,
    val suppressedSourceApps: Set<String> = emptySet(),
    // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md` Task 3:
    // packages the user explicitly removed from the Suppressed Apps list in
    // Settings. `SuppressedSourceAppsAutoExpansionPolicy` treats membership
    // here as a hard exclude — it will never re-add these packages to
    // `suppressedSourceApps` even when a fresh DIGEST notification arrives.
    // Default `emptySet()` means existing users see no behavior change.
    val suppressedSourceAppsExcluded: Set<String> = emptySet(),
    val hidePersistentNotifications: Boolean = true,
    val hidePersistentSourceNotifications: Boolean = false,
    val protectCriticalPersistentNotifications: Boolean = true,
    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6: default OFF. When
    // true, SmartNoti exposes the 무시됨 아카이브 route (conditional nav entry)
    // so power users can audit / recover rows the IGNORE rule swept out of
    // the default views. Toggling this is the only in-app surface that reveals
    // IGNORE rows — the repository still persists them regardless.
    val showIgnoredArchive: Boolean = false,
    // Plan `2026-04-26-duplicate-threshold-window-settings.md`. User-tunable
    // base heuristic for the "duplicate burst → DIGEST" cascade. Defaults
    // mirror the historical hard-coded values (3 repeats, 10-minute window)
    // so existing users see no behavior change post-upgrade. The classifier
    // reads `duplicateDigestThreshold` to compare against
    // `ClassificationInput.duplicateCountInWindow`; the listener reads
    // `duplicateWindowMinutes` to size `DuplicateNotificationPolicy`'s rolling
    // window. Rule / Category matches still preempt this knob — it only
    // fires when the user has neither a matching rule nor a priority keyword.
    val duplicateDigestThreshold: Int = 3,
    val duplicateWindowMinutes: Int = 10,
    // Plan `2026-04-27-fix-issue-488-signature-normalize-numbers-time.md`.
    // Opt-in toggle for the duplicate-burst signature normalizer (issue #488
    // Bug 2). When `true`, `NotificationDuplicateContextBuilder` runs the
    // policy's lowercase + whitespace-collapsed signature through
    // `ContentSignatureNormalizer` so amount-only-差 templates (네이버페이
    // 포인트뽑기 `8/12/16/28원이 적립되었어요` 등) collapse to a single shape and
    // `repeat_bundle:N` / base `duplicateDigestThreshold` heuristics fire as
    // intended. **Default is `false`** because collapsing meaningful payment
    // amounts (e.g. `100원 결제` vs `100,000원 결제`) into the same signature
    // is a per-user judgment call — fresh installs and existing users see no
    // behavior change until they opt in via Settings → 중복 알림 묶기. No
    // migration is needed because `false` matches the "key never set" branch.
    val normalizeNumericTokensInSignature: Boolean = false,
    // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 2:
    // Set of packageNames the classifier treats as quiet-hours-eligible.
    // Default mirrors the previously-hardcoded `setOf("com.coupang.mobile")`
    // in `NotificationClassifier`'s constructor wiring so existing users see
    // identical behavior; the UI lets them add/remove freely. Empty set means
    // quiet-hours has no candidates and the branch never fires (intentional —
    // the master switch and the candidate set are orthogonal control points).
    val quietHoursPackages: Set<String> = DEFAULT_QUIET_HOURS_PACKAGES,
    // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3:
    // master toggle for auto-dismissing SmartNoti-posted replacement /
    // summary notifications (DIGEST replacement, SILENT group summary,
    // SILENT group child) after `replacementAutoDismissMinutes` minutes.
    // Default ON because the originating product ask was "tray 에 누적되지
    // 않게 일정 시간 뒤 자동으로 사라지게". Existing users get this default
    // injected once via `SettingsRepository.applyPendingMigrations()`.
    // OFF restores legacy behavior (notification stays in tray until the
    // user swipes). PRIORITY originals are never affected — they live
    // outside SmartNoti's tray-write contract.
    val replacementAutoDismissEnabled: Boolean = true,
    // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3:
    // minutes the replacement / summary stays in the tray before
    // NotificationManager auto-cancels via
    // `NotificationCompat.Builder.setTimeoutAfter`. Default 30 follows the
    // Android docs example value and matches "잠깐 보고 알아채면 충분" UX.
    // Settings UI exposes a 5 / 15 / 30 / 60 / 180 preset. The
    // `ReplacementNotificationTimeoutPolicy` guards against `<= 0`
    // defensively even though the picker never offers it.
    val replacementAutoDismissMinutes: Int = 30,
    // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 2.
    // User-selected sort mode for the unified inbox sub-tabs (Digest /
    // 보관 중 / 처리됨). Stored as `InboxSortMode.name` so adding modes is a
    // backward-compatible append. Default is `RECENT.name` which mirrors the
    // pre-existing behavior; existing installs see no change until the user
    // picks a different mode via the InboxSortDropdown.
    val inboxSortMode: String = InboxSortMode.RECENT.name,
    // Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
    // Task 5. Sticky-permanent dismiss set: packageNames the user has tapped
    // `[무시]` on in a previous `InboxSuggestionCard`. `HighVolumeAppDetector`
    // never proposes these again. Default `emptySet()` so existing users see
    // identical behavior on upgrade.
    val suggestedSuppressionDismissed: Set<String> = emptySet(),
    // Plan `2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md` Task 4.
    // Persisted "다시 묻지 않기" flag for the Settings → 트레이 정리 confirm
    // dialog. Default `false` so first-time users always see the confirm
    // dialog (R1 default — 100+ source-app cancels are non-trivially
    // reversible). Power users that flip the dialog checkbox once skip
    // confirmation thereafter; the checkbox is the only surface that writes
    // this key (no standalone Settings row).
    val trayCleanupSkipConfirm: Boolean = false,
    // Plan `2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
    // Task 5. 24h-snooze map: packageName -> wall-clock millis the snooze
    // expires at. `HighVolumeAppSuggestionPolicy` skips the candidate when
    // `value > nowMillis`. Default `emptyMap()` so existing users see no
    // suggestion behavior change. Persisted as a string-encoded
    // `pkg=until|pkg2=until` payload by [SettingsSuppressionRepository] —
    // DataStore Preferences does not support a native Map<String, Long>.
    val suggestedSuppressionSnoozeUntil: Map<String, Long> = emptyMap(),
) {
    companion object {
        // Single source of truth for the quiet-hours-eligible default set.
        // `OnboardingQuickStartSettingsApplier` reads from here so the
        // onboarding boost-list and the persisted settings default cannot
        // drift apart. See plan
        // `2026-04-26-quiet-hours-shopping-packages-user-extensible.md`
        // Task 4 for the SSOT consolidation rationale.
        val DEFAULT_QUIET_HOURS_PACKAGES: Set<String> = setOf("com.coupang.mobile")
    }
}
