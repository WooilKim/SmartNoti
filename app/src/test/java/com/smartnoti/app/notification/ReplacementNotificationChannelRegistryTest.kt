package com.smartnoti.app.notification

import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.VibrationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplacementNotificationChannelRegistryTest {

    @Test
    fun priority_channel_id_reflects_heads_up_vibration_and_visibility() {
        val spec = ReplacementNotificationChannelRegistry.resolve(
            decision = NotificationDecision.PRIORITY,
            deliveryProfile = DeliveryProfile.priorityDefaults().copy(
                vibrationMode = VibrationMode.LIGHT,
                headsUpEnabled = false,
                lockScreenVisibilityMode = LockScreenVisibilityMode.SECRET,
                channelKey = "totally-untrusted-channel",
            ),
        )

        assertEquals(
            "smartnoti_replacement_priority_default_light_secret_noheadsup",
            spec.id,
        )
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, spec.importance)
        assertEquals(NotificationCompat.PRIORITY_DEFAULT, spec.compatPriority)
        assertEquals(Notification.VISIBILITY_SECRET, spec.notificationVisibility)
        assertEquals(listOf(0L, 120L), spec.vibrationPattern)
        assertTrue(spec.soundEnabled)
    }

    @Test
    fun changed_digest_behavior_maps_to_different_channel_id() {
        val digestQuiet = ReplacementNotificationChannelRegistry.resolve(
            decision = NotificationDecision.DIGEST,
            deliveryProfile = DeliveryProfile.digestDefaults(),
        )
        val digestNone = ReplacementNotificationChannelRegistry.resolve(
            decision = NotificationDecision.DIGEST,
            deliveryProfile = DeliveryProfile.digestDefaults().copy(
                alertLevel = AlertLevel.NONE,
                vibrationMode = VibrationMode.OFF,
                channelKey = "another-untrusted-channel",
            ),
        )

        assertNotEquals(digestQuiet.id, digestNone.id)
        assertTrue(digestQuiet.id.startsWith("smartnoti_replacement_digest_"))
        assertTrue(digestNone.id.startsWith("smartnoti_replacement_digest_"))
    }

    @Test
    fun digest_none_stays_digest_specific_instead_of_silent() {
        val spec = ReplacementNotificationChannelRegistry.resolve(
            decision = NotificationDecision.DIGEST,
            deliveryProfile = DeliveryProfile.digestDefaults().copy(
                alertLevel = AlertLevel.NONE,
                vibrationMode = VibrationMode.OFF,
                lockScreenVisibilityMode = LockScreenVisibilityMode.SECRET,
                channelKey = "ignored-channel-key",
            ),
        )

        assertEquals(
            "smartnoti_replacement_digest_min_off_secret_noheadsup",
            spec.id,
        )
        assertEquals(NotificationManager.IMPORTANCE_MIN, spec.importance)
        assertEquals(NotificationCompat.PRIORITY_MIN, spec.compatPriority)
        assertEquals(Notification.VISIBILITY_SECRET, spec.notificationVisibility)
        assertEquals(emptyList<Long>(), spec.vibrationPattern)
        assertEquals(false, spec.soundEnabled)
    }

    @Test
    fun silent_profile_sanitizes_unsupported_behavior_before_channel_generation() {
        val spec = ReplacementNotificationChannelRegistry.resolve(
            decision = NotificationDecision.SILENT,
            deliveryProfile = DeliveryProfile.silentDefaults().copy(
                alertLevel = AlertLevel.LOUD,
                vibrationMode = VibrationMode.STRONG,
                headsUpEnabled = true,
                lockScreenVisibilityMode = LockScreenVisibilityMode.PUBLIC,
                channelKey = "another-untrusted-key",
            ),
        )

        assertEquals(
            "smartnoti_replacement_silent_low_off_private_noheadsup",
            spec.id,
        )
        assertEquals(NotificationManager.IMPORTANCE_LOW, spec.importance)
        assertEquals(Notification.VISIBILITY_PRIVATE, spec.notificationVisibility)
        assertEquals(emptyList<Long>(), spec.vibrationPattern)
        assertEquals(false, spec.soundEnabled)
    }
}
