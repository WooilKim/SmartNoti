package com.smartnoti.app.navigation

import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementNotificationEntryRoutesTest {

    @Test
    fun decision_maps_to_expected_parent_route() {
        assertEquals(Routes.Priority.route, ReplacementNotificationEntryRoutes.forDecision(NotificationDecision.PRIORITY))
        assertEquals(Routes.Digest.route, ReplacementNotificationEntryRoutes.forDecision(NotificationDecision.DIGEST))
        assertEquals(Routes.Home.route, ReplacementNotificationEntryRoutes.forDecision(NotificationDecision.SILENT))
    }

    @Test
    fun sanitize_keeps_supported_parent_routes() {
        assertEquals(Routes.Home.route, ReplacementNotificationEntryRoutes.sanitize(Routes.Home.route))
        assertEquals(Routes.Priority.route, ReplacementNotificationEntryRoutes.sanitize(Routes.Priority.route))
        assertEquals(Routes.Digest.route, ReplacementNotificationEntryRoutes.sanitize(Routes.Digest.route))
    }

    @Test
    fun sanitize_falls_back_to_home_for_unknown_route() {
        assertEquals(Routes.Home.route, ReplacementNotificationEntryRoutes.sanitize(null))
        assertEquals(Routes.Home.route, ReplacementNotificationEntryRoutes.sanitize("detail/id-1"))
        assertEquals(Routes.Home.route, ReplacementNotificationEntryRoutes.sanitize("settings"))
    }
}
