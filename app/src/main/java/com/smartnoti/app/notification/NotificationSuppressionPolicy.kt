package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision

object NotificationSuppressionPolicy {
    fun shouldSuppressSourceNotification(
        suppressDigestAndSilent: Boolean,
        decision: NotificationDecision,
    ): Boolean {
        if (!suppressDigestAndSilent) return false

        return when (decision) {
            NotificationDecision.PRIORITY -> false
            NotificationDecision.DIGEST,
            NotificationDecision.SILENT,
            -> true
        }
    }
}