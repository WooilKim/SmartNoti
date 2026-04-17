package com.smartnoti.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartNotiNotificationActionReceiverTest {

    @Test
    fun feedback_request_code_is_stable_for_same_notification_and_action() {
        val first = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-1",
            action = SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY,
        )
        val second = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-1",
            action = SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY,
        )

        assertEquals(first, second)
    }

    @Test
    fun feedback_request_code_differs_for_different_notifications() {
        val first = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-1",
            action = SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY,
        )
        val second = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-2",
            action = SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY,
        )

        org.junit.Assert.assertNotEquals(first, second)
    }

    @Test
    fun feedback_request_code_differs_for_different_actions() {
        val promote = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-1",
            action = SmartNotiNotifier.ACTION_PROMOTE_TO_PRIORITY,
        )
        val keepDigest = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-1",
            action = SmartNotiNotifier.ACTION_KEEP_DIGEST,
        )

        org.junit.Assert.assertNotEquals(promote, keepDigest)
    }
}
