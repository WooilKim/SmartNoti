package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.CapturedNotificationInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureProcessorPersistentTest {

    private val processor = NotificationCaptureProcessor(
        classifier = NotificationClassifier(
            vipSenders = emptySet(),
            priorityKeywords = emptySet(),
            shoppingPackages = emptySet(),
        ),
        deliveryProfilePolicy = DeliveryProfilePolicy(),
    )

    @Test
    fun adds_persistent_reason_tag_when_input_is_persistent() {
        val notification = processor.process(
            input = CapturedNotificationInput(
                packageName = "android",
                appName = "시스템",
                sender = null,
                title = "충전 중",
                body = "배터리 보호",
                postedAtMillis = 1_700_000_000_000L,
                quietHours = false,
                duplicateCountInWindow = 1,
                isPersistent = true,
            ),
            rules = emptyList(),
            settings = SmartNotiSettings(),
        )

        assertTrue("지속 알림" in notification.reasonTags)
    }

    @Test
    fun marks_persistent_field_on_notification_ui_model_and_uses_safe_delivery_metadata() {
        val notification = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.android.camera",
                appName = "카메라",
                sender = null,
                title = "정리 중",
                body = "사진을 정리하고 있어요",
                postedAtMillis = 1_700_000_000_000L,
                quietHours = false,
                duplicateCountInWindow = 1,
                isPersistent = true,
            ),
            rules = emptyList(),
            settings = SmartNotiSettings(),
        )

        assertTrue(notification.isPersistent)
        assertEquals("smartnoti_silent", notification.deliveryChannelKey)
        assertEquals(AlertLevel.NONE, notification.alertLevel)
        assertFalse(notification.headsUpEnabled)
    }

    @Test
    fun does_not_tag_call_related_persistent_notifications_as_generic_persistent() {
        val notification = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.android.dialer",
                appName = "전화",
                sender = null,
                title = "통화 중",
                body = "00:31",
                postedAtMillis = 1_700_000_000_000L,
                quietHours = false,
                duplicateCountInWindow = 1,
                isPersistent = false,
            ),
            rules = emptyList(),
            settings = SmartNotiSettings(),
        )

        assertFalse("지속 알림" in notification.reasonTags)
    }
}
