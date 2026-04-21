package com.smartnoti.app.navigation

import com.smartnoti.app.domain.model.NotificationDecision

data class ReplacementNotificationEntry(
    val notificationId: String,
    val parentRoute: String,
)

object ReplacementNotificationEntryRoutes {
    fun forDecision(decision: NotificationDecision): String = when (decision) {
        NotificationDecision.PRIORITY -> Routes.Priority.route
        NotificationDecision.DIGEST -> Routes.Digest.route
        NotificationDecision.SILENT -> Routes.Home.route
        // IGNORE never posts a replacement notification (Task 4 early-return
        // in the notifier) so this route is unused in practice. Keep Home as
        // a defensive fallback — plan
        // `2026-04-21-ignore-tier-fourth-decision` Task 2.
        NotificationDecision.IGNORE -> Routes.Home.route
    }

    fun sanitize(route: String?): String = when (route) {
        Routes.Home.route,
        Routes.Priority.route,
        Routes.Digest.route,
        -> route
        else -> Routes.Home.route
    }
}
