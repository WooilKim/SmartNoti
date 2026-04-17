package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSuppressionPolicyTest {

    @Test
    fun opt_in_disabled_never_suppresses_source_notification() {
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = false,
                decision = NotificationDecision.DIGEST,
            )
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = false,
                decision = NotificationDecision.SILENT,
            )
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = false,
                decision = NotificationDecision.PRIORITY,
            )
        )
    }

    @Test
    fun opt_in_enabled_suppresses_digest_and_silent_only() {
        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                decision = NotificationDecision.DIGEST,
            )
        )
        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                decision = NotificationDecision.SILENT,
            )
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                decision = NotificationDecision.PRIORITY,
            )
        )
    }
}