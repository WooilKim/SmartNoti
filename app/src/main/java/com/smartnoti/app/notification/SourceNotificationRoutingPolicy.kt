package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision

internal object SourceNotificationRoutingPolicy {
    fun route(
        decision: NotificationDecision,
        hidePersistentSourceNotification: Boolean,
        suppressSourceNotification: Boolean,
    ): SourceNotificationRouting {
        return when (decision) {
            NotificationDecision.PRIORITY -> SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            )
            // Silent means the user asked SmartNoti to keep the notification quiet —
            // hiding the source and surfacing an aggregated "숨겨진 알림 N건" summary
            // instead is always the correct behavior, regardless of the legacy
            // per-app suppression opt-in. No individual replacement is posted; the
            // aggregated summary is handled separately by the listener service.
            NotificationDecision.SILENT -> SourceNotificationRouting(
                cancelSourceNotification = true,
                notifyReplacementNotification = false,
            )
            NotificationDecision.DIGEST -> if (hidePersistentSourceNotification || suppressSourceNotification) {
                SourceNotificationRouting(
                    cancelSourceNotification = true,
                    notifyReplacementNotification = true,
                )
            } else {
                SourceNotificationRouting(
                    cancelSourceNotification = false,
                    notifyReplacementNotification = false,
                )
            }
        }
    }
}

internal data class SourceNotificationRouting(
    val cancelSourceNotification: Boolean,
    val notifyReplacementNotification: Boolean,
)
