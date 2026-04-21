package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.SilentMode

internal object SourceNotificationRoutingPolicy {
    fun route(
        decision: NotificationDecision,
        hidePersistentSourceNotification: Boolean,
        suppressSourceNotification: Boolean,
        silentMode: SilentMode? = null,
    ): SourceNotificationRouting {
        return when (decision) {
            NotificationDecision.PRIORITY -> SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            )
            // Silent means the user asked SmartNoti to keep the notification quiet.
            // The `silent-archive-vs-process-split` plan introduces two sub-states:
            //   - ARCHIVED  → keep the source in the tray at its original importance
            //                 so the user still sees it exists (nothing cancelled).
            //   - PROCESSED → user acknowledged it, cancel the source. This mirrors
            //                 the legacy behavior that predated SilentMode.
            // `silentMode == null` preserves the legacy cancel-on-SILENT shape so
            // existing call-sites (not yet passing a mode) keep working until the
            // listener threads the mode through in a later task.
            NotificationDecision.SILENT -> {
                val keepSourceInTray = silentMode == SilentMode.ARCHIVED
                SourceNotificationRouting(
                    cancelSourceNotification = !keepSourceInTray,
                    notifyReplacementNotification = false,
                )
            }
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
            // IGNORE — plan `2026-04-21-ignore-tier-fourth-decision` Task 4
            // will formalize the behaviour: cancel the source tray entry
            // unconditionally (user asked to delete), never post a
            // replacement alert. Task 2 only lines up the routing here so
            // exhaustiveness holds.
            NotificationDecision.IGNORE -> SourceNotificationRouting(
                cancelSourceNotification = true,
                notifyReplacementNotification = false,
            )
        }
    }
}

internal data class SourceNotificationRouting(
    val cancelSourceNotification: Boolean,
    val notifyReplacementNotification: Boolean,
)
