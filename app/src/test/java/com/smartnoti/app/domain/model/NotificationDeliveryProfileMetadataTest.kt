package com.smartnoti.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationDeliveryProfileMetadataTest {

    @Test
    fun reconstructs_delivery_profile_from_notification_metadata() {
        val model = NotificationUiModel(
            id = "id-1",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "제목",
            body = "본문",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.DIGEST,
            reasonTags = emptyList(),
            deliveryChannelKey = DeliveryProfile.CHANNEL_DIGEST,
            alertLevel = AlertLevel.QUIET,
            vibrationMode = VibrationMode.OFF,
            headsUpEnabled = true,
            lockScreenVisibility = LockScreenVisibilityMode.PUBLIC,
        )

        val profile = model.toDeliveryProfileOrDefault()

        assertEquals(AlertLevel.QUIET, profile.alertLevel)
        assertEquals(VibrationMode.OFF, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.PRIVATE, profile.lockScreenVisibilityMode)
        assertEquals(DeliveryMode.BATCHED, profile.deliveryMode)
        assertEquals(DeliveryProfile.CHANNEL_DIGEST, profile.channelKey)
    }

    @Test
    fun digest_none_keeps_digest_delivery_mode_and_default_channel_key() {
        val model = NotificationUiModel(
            id = "id-2",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "제목",
            body = "본문",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.DIGEST,
            reasonTags = emptyList(),
            deliveryChannelKey = "evil-channel",
            alertLevel = AlertLevel.NONE,
            vibrationMode = VibrationMode.OFF,
            headsUpEnabled = true,
            lockScreenVisibility = LockScreenVisibilityMode.SECRET,
        )

        val profile = model.toDeliveryProfileOrDefault()

        assertEquals(AlertLevel.NONE, profile.alertLevel)
        assertEquals(VibrationMode.OFF, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.SECRET, profile.lockScreenVisibilityMode)
        assertEquals(DeliveryMode.BATCHED, profile.deliveryMode)
        assertEquals(DeliveryProfile.CHANNEL_DIGEST, profile.channelKey)
    }

    @Test
    fun invalid_channel_key_is_replaced_with_decision_default_channel() {
        val model = NotificationUiModel(
            id = "id-3",
            appName = "앱",
            packageName = "com.example.app",
            sender = null,
            title = "제목",
            body = "본문",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.SILENT,
            reasonTags = emptyList(),
            deliveryChannelKey = "evil-channel",
            alertLevel = AlertLevel.LOUD,
            vibrationMode = VibrationMode.STRONG,
            headsUpEnabled = true,
            lockScreenVisibility = LockScreenVisibilityMode.PUBLIC,
        )

        val profile = model.toDeliveryProfileOrDefault()

        assertEquals(AlertLevel.QUIET, profile.alertLevel)
        assertEquals(VibrationMode.OFF, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.PRIVATE, profile.lockScreenVisibilityMode)
        assertEquals(DeliveryMode.SUMMARY_ONLY, profile.deliveryMode)
        assertEquals(DeliveryProfile.CHANNEL_SILENT, profile.channelKey)
    }
}
