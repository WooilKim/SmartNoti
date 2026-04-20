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
    fun digest_without_hide_or_suppress_keeps_source_notification() {
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.DIGEST,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
        )

        assertFalse(routing.cancelSourceNotification)
        assertFalse(routing.notifyReplacementNotification)
    }

    @Test
    fun silent_always_cancels_source_without_individual_replacement() {
        val suppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = true,
        )
        assertTrue(suppressed.cancelSourceNotification)
        assertFalse(suppressed.notifyReplacementNotification)

        val unsuppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
        )
        assertTrue(unsuppressed.cancelSourceNotification)
        assertFalse(unsuppressed.notifyReplacementNotification)
    }
}
