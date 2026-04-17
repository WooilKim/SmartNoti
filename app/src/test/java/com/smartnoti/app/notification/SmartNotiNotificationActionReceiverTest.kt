package com.smartnoti.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartNotiNotificationActionReceiverTest {

    @Test
    fun promote_request_code_is_stable_for_same_notification() {
        val first = SmartNotiNotifier.promoteRequestCodeForTest("notification-1")
        val second = SmartNotiNotifier.promoteRequestCodeForTest("notification-1")

        assertEquals(first, second)
    }

    @Test
    fun promote_request_code_differs_for_different_notifications() {
        val first = SmartNotiNotifier.promoteRequestCodeForTest("notification-1")
        val second = SmartNotiNotifier.promoteRequestCodeForTest("notification-2")

        org.junit.Assert.assertNotEquals(first, second)
    }
}
