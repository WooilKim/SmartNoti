package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceNotificationSuppressionStateResolverTest {

    @Test
    fun cancel_attempt_ranks_highest_when_source_hiding_is_routed() {
        val state = SourceNotificationSuppressionStateResolver.resolve(
            decision = NotificationDecision.DIGEST,
            suppressDigestAndSilent = true,
            suppressedApps = setOf("com.coupang.mobile"),
            packageName = "com.coupang.mobile",
            hidePersistentSourceNotifications = false,
            isPersistent = false,
            bypassPersistentHiding = false,
            sourceRouting = SourceNotificationRouting(
                cancelSourceNotification = true,
                notifyReplacementNotification = true,
            ),
        )

        assertEquals(SourceNotificationSuppressionState.CANCEL_ATTEMPTED, state)
    }

    @Test
    fun protected_persistent_notifications_explain_why_source_was_kept() {
        val state = SourceNotificationSuppressionStateResolver.resolve(
            decision = NotificationDecision.DIGEST,
            suppressDigestAndSilent = false,
            suppressedApps = emptySet(),
            packageName = "com.android.systemui",
            hidePersistentSourceNotifications = true,
            isPersistent = true,
            bypassPersistentHiding = true,
            sourceRouting = SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            ),
        )

        assertEquals(SourceNotificationSuppressionState.PERSISTENT_PROTECTED, state)
    }

    @Test
    fun selected_apps_setting_explains_why_digest_source_remains_visible() {
        val state = SourceNotificationSuppressionStateResolver.resolve(
            decision = NotificationDecision.DIGEST,
            suppressDigestAndSilent = true,
            suppressedApps = setOf("com.kakao.talk"),
            packageName = "com.coupang.mobile",
            hidePersistentSourceNotifications = false,
            isPersistent = false,
            bypassPersistentHiding = false,
            sourceRouting = SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            ),
        )

        assertEquals(SourceNotificationSuppressionState.APP_NOT_SELECTED, state)
    }

    @Test
    fun priority_notifications_are_recorded_as_intentionally_kept() {
        val state = SourceNotificationSuppressionStateResolver.resolve(
            decision = NotificationDecision.PRIORITY,
            suppressDigestAndSilent = true,
            suppressedApps = setOf("com.kakao.talk"),
            packageName = "com.kakao.talk",
            hidePersistentSourceNotifications = false,
            isPersistent = false,
            bypassPersistentHiding = false,
            sourceRouting = SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            ),
        )

        assertEquals(SourceNotificationSuppressionState.PRIORITY_KEPT, state)
    }

    @Test
    fun missing_configuration_falls_back_to_not_configured() {
        val state = SourceNotificationSuppressionStateResolver.resolve(
            decision = NotificationDecision.SILENT,
            suppressDigestAndSilent = false,
            suppressedApps = emptySet(),
            packageName = "com.coupang.mobile",
            hidePersistentSourceNotifications = false,
            isPersistent = false,
            bypassPersistentHiding = false,
            sourceRouting = SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            ),
        )

        assertEquals(SourceNotificationSuppressionState.NOT_CONFIGURED, state)
    }

    @Test
    fun replacement_notification_recorded_requires_actual_post() {
        assertTrue(
            SourceNotificationSuppressionStateResolver.replacementNotificationRecorded(
                sourceRouting = SourceNotificationRouting(
                    cancelSourceNotification = true,
                    notifyReplacementNotification = true,
                ),
                replacementNotificationPosted = true,
            )
        )
        assertFalse(
            SourceNotificationSuppressionStateResolver.replacementNotificationRecorded(
                sourceRouting = SourceNotificationRouting(
                    cancelSourceNotification = true,
                    notifyReplacementNotification = true,
                ),
                replacementNotificationPosted = false,
            )
        )
    }
}
