package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceNotificationRoutingPolicyTest {

    @Test
    fun persistent_hidden_priority_keeps_source_notification_and_skips_replacement() {
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.PRIORITY,
            hidePersistentSourceNotification = true,
            suppressSourceNotification = false,
        )

        assertFalse(routing.cancelSourceNotification)
        assertFalse(routing.notifyReplacementNotification)
    }

    @Test
    fun digest_suppression_cancels_source_and_shows_replacement() {
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.DIGEST,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = true,
        )

        assertTrue(routing.cancelSourceNotification)
        assertTrue(routing.notifyReplacementNotification)
    }

    @Test
    fun silent_suppression_cancels_source_without_posting_replacement_notification() {
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = true,
        )

        assertTrue(routing.cancelSourceNotification)
        assertFalse(routing.notifyReplacementNotification)
    }
}
