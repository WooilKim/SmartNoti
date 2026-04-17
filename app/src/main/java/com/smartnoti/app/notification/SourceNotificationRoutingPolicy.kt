package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision

internal object SourceNotificationRoutingPolicy {
    fun route(
        decision: NotificationDecision,
        hidePersistentSourceNotification: Boolean,
        suppressSourceNotification: Boolean,
    ): SourceNotificationRouting {
        if (!hidePersistentSourceNotification && !suppressSourceNotification) {
            return SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            )
        }

        return when (decision) {
            NotificationDecision.PRIORITY -> SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            )
            NotificationDecision.DIGEST,
            NotificationDecision.SILENT,
            -> SourceNotificationRouting(
                cancelSourceNotification = true,
                notifyReplacementNotification = true,
            )
        }
    }
}

internal data class SourceNotificationRouting(
    val cancelSourceNotification: Boolean,
    val notifyReplacementNotification: Boolean,
)
