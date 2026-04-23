package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision

object NotificationSuppressionPolicy {
    fun shouldSuppressSourceNotification(
        suppressDigestAndSilent: Boolean,
        suppressedApps: Set<String>,
        packageName: String,
        decision: NotificationDecision,
    ): Boolean {
        // IGNORE is a user-declared delete-level classification (plan
        // `2026-04-21-ignore-tier-fourth-decision` Task 4). The user has asked
        // SmartNoti to delete the notification — not quiet it, not digest it
        // — so the source tray cancel must run unconditionally. Neither the
        // global `suppressDigestAndSilent` opt-in nor the per-app
        // `suppressedApps` set gate this path.
        if (decision == NotificationDecision.IGNORE) return true

        if (!suppressDigestAndSilent) return false
        // Empty `suppressedApps` is interpreted as "all captured packages
        // opt-in" (opt-out semantics). This is what makes the default install
        // — global toggle ON + empty list — actually deliver the product
        // promise that DIGEST/SILENT show as a SmartNoti replacement instead
        // of the original. A non-empty list keeps the older allow-list
        // semantics so users can intentionally narrow the scope. See plan
        // `2026-04-24-duplicate-notifications-suppress-defaults-ac.md` Task 2.
        val packageOptedIn = suppressedApps.isEmpty() || packageName in suppressedApps
        if (!packageOptedIn) return false

        return when (decision) {
            NotificationDecision.PRIORITY -> false
            NotificationDecision.DIGEST,
            NotificationDecision.SILENT,
            -> true
            // Handled by the early-return above; kept here so the `when` stays
            // exhaustive if a future refactor removes the guard.
            NotificationDecision.IGNORE -> true
        }
    }
}