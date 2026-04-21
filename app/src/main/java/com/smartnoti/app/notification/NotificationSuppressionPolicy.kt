package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision

object NotificationSuppressionPolicy {
    fun shouldSuppressSourceNotification(
        suppressDigestAndSilent: Boolean,
        suppressedApps: Set<String>,
        packageName: String,
        decision: NotificationDecision,
    ): Boolean {
        if (!suppressDigestAndSilent) return false
        if (packageName !in suppressedApps) return false

        return when (decision) {
            NotificationDecision.PRIORITY -> false
            NotificationDecision.DIGEST,
            NotificationDecision.SILENT,
            -> true
            // Task 4 of plan `2026-04-21-ignore-tier-fourth-decision` will add
            // an unconditional tray-cancel for IGNORE (bypassing the
            // suppressDigestAndSilent / suppressedApps gates). Task 2 only
            // mirrors the SILENT behaviour here so exhaustiveness holds; the
            // Task 4 refactor will split IGNORE into its own early branch.
            NotificationDecision.IGNORE -> true
        }
    }
}