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
                suppressedApps = setOf("com.coupang.mobile"),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.DIGEST,
            )
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = false,
                suppressedApps = setOf("com.coupang.mobile"),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.SILENT,
            )
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = false,
                suppressedApps = setOf("com.coupang.mobile"),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.PRIORITY,
            )
        )
    }

    @Test
    fun opt_in_enabled_but_app_not_selected_does_not_suppress() {
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                suppressedApps = emptySet(),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.DIGEST,
            )
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                suppressedApps = setOf("com.kakao.talk"),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.SILENT,
            )
        )
    }

    @Test
    fun opt_in_enabled_suppresses_selected_digest_and_silent_only() {
        val selectedApps = setOf("com.coupang.mobile")

        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                suppressedApps = selectedApps,
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.DIGEST,
            )
        )
        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                suppressedApps = selectedApps,
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.SILENT,
            )
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                suppressedApps = selectedApps,
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.PRIORITY,
            )
        )
    }
}
