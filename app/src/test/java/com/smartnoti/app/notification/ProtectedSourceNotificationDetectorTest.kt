package com.smartnoti.app.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedSourceNotificationDetectorTest {

    @Test
    fun plain_notification_is_not_protected() {
        assertFalse(ProtectedSourceNotificationDetector.isProtected(plain()))
    }

    @Test
    fun media_transport_category_is_protected() {
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(plain().copy(category = "transport")),
        )
    }

    @Test
    fun call_category_is_protected() {
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(plain().copy(category = "call")),
        )
    }

    @Test
    fun navigation_category_is_protected() {
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(plain().copy(category = "navigation")),
        )
    }

    @Test
    fun alarm_and_progress_categories_are_protected() {
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(plain().copy(category = "alarm")),
        )
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(plain().copy(category = "progress")),
        )
    }

    @Test
    fun media_session_extra_is_protected() {
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(
                plain().copy(hasMediaSessionExtra = true),
            ),
        )
    }

    @Test
    fun media_style_template_is_protected() {
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(
                plain().copy(templateName = "android.app.Notification\$MediaStyle"),
            ),
        )
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(
                plain().copy(templateName = "androidx.media.app.NotificationCompat\$DecoratedMediaCustomViewStyle"),
            ),
        )
    }

    @Test
    fun foreground_service_notification_is_protected() {
        assertTrue(
            ProtectedSourceNotificationDetector.isProtected(
                plain().copy(isForegroundService = true),
            ),
        )
    }

    @Test
    fun unrelated_template_is_not_protected() {
        assertFalse(
            ProtectedSourceNotificationDetector.isProtected(
                plain().copy(templateName = "android.app.Notification\$MessagingStyle"),
            ),
        )
    }

    @Test
    fun unrelated_category_is_not_protected() {
        assertFalse(
            ProtectedSourceNotificationDetector.isProtected(plain().copy(category = "promo")),
        )
        assertFalse(
            ProtectedSourceNotificationDetector.isProtected(plain().copy(category = "msg")),
        )
    }

    private fun plain() = ProtectedSourceNotificationSignals(
        category = null,
        templateName = null,
        hasMediaSessionExtra = false,
        isForegroundService = false,
    )
}
