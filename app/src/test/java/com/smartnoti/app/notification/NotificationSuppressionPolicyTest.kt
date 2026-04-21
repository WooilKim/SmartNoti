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

    // IGNORE is a user-declared delete-level classification introduced by plan
    // `2026-04-21-ignore-tier-fourth-decision`. Unlike DIGEST / SILENT, the
    // source tray cancel must run unconditionally — the user asked to delete
    // the notification, not just quiet it. The opt-in
    // `suppressDigestAndSilent` flag and per-app `suppressedApps` set both
    // become non-gates for IGNORE.

    @Test
    fun ignore_suppresses_source_even_when_opt_in_disabled() {
        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = false,
                suppressedApps = emptySet(),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.IGNORE,
            )
        )
    }

    @Test
    fun ignore_suppresses_source_even_when_app_not_selected() {
        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                suppressedApps = setOf("com.kakao.talk"),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.IGNORE,
            )
        )
    }

    @Test
    fun ignore_suppresses_source_when_app_is_selected() {
        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = true,
                suppressedApps = setOf("com.coupang.mobile"),
                packageName = "com.coupang.mobile",
                decision = NotificationDecision.IGNORE,
            )
        )
    }
}
