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

    // Plan 2026-04-21-ignore-tier-fourth-decision Task 6a — the Detail screen's
    // "무시" button wires into a broadcast path alongside the existing three
    // feedback actions. Replacement alerts don't expose an IGNORE button (alerts
    // only appear for DIGEST/SILENT), but the constant is still part of the
    // broadcast vocabulary and must be namespaced + stable-hashed like its
    // siblings so the receiver can resolve it.
    @Test
    fun ignore_action_constant_is_namespaced() {
        assertEquals(
            "com.smartnoti.app.action.IGNORE",
            SmartNotiNotifier.ACTION_IGNORE,
        )
    }

    @Test
    fun feedback_request_code_differs_for_ignore_vs_other_actions() {
        val ignore = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-1",
            action = SmartNotiNotifier.ACTION_IGNORE,
        )
        val silent = SmartNotiNotifier.feedbackRequestCodeForTest(
            notificationId = "notification-1",
            action = SmartNotiNotifier.ACTION_KEEP_SILENT,
        )

        org.junit.Assert.assertNotEquals(ignore, silent)
    }
}
