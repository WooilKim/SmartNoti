package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureProcessorPersistentTest {

    private val processor = NotificationCaptureProcessor(
        classifier = NotificationClassifier(
            vipSenders = emptySet(),
            priorityKeywords = emptySet(),
            shoppingPackages = emptySet(),
        )
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
        )

        assertTrue("지속 알림" in notification.reasonTags)
    }

    @Test
    fun marks_persistent_field_on_notification_ui_model() {
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
        )

        assertTrue(notification.isPersistent)
    }
}
