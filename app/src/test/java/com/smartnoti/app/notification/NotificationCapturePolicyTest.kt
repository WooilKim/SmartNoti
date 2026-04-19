package com.smartnoti.app.notification

import android.app.Notification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCapturePolicyTest {

    @Test
    fun blank_group_summary_is_ignored_when_flags_mark_summary() {
        assertTrue(
            NotificationCapturePolicy.shouldIgnoreCapture(
                title = "",
                body = "",
                notificationFlags = Notification.FLAG_GROUP_SUMMARY,
            )
        )
    }

    @Test
    fun blank_non_summary_notification_is_not_ignored() {
        assertFalse(
            NotificationCapturePolicy.shouldIgnoreCapture(
                title = "",
                body = "",
                notificationFlags = 0,
            )
        )
    }

    @Test
    fun non_blank_group_summary_is_not_ignored() {
        assertFalse(
            NotificationCapturePolicy.shouldIgnoreCapture(
                title = "업데이트 3건",
                body = "새 메시지가 도착했어요",
                notificationFlags = Notification.FLAG_GROUP_SUMMARY,
            )
        )
    }
}
