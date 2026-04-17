package com.smartnoti.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryProfileTest {

    @Test
    fun priority_defaults_are_high_attention_and_immediate() {
        val profile = DeliveryProfile.priorityDefaults()

        assertEquals("smartnoti_priority", profile.channelKey)
        assertEquals(DeliveryMode.IMMEDIATE, profile.deliveryMode)
        assertEquals(AlertLevel.LOUD, profile.alertLevel)
        assertEquals(VibrationMode.STRONG, profile.vibrationMode)
        assertTrue(profile.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.PRIVATE, profile.lockScreenVisibilityMode)
    }

    @Test
    fun silent_defaults_are_non_intrusive_and_summary_only() {
        val profile = DeliveryProfile.silentDefaults()

        assertEquals("smartnoti_silent", profile.channelKey)
        assertEquals(DeliveryMode.SUMMARY_ONLY, profile.deliveryMode)
        assertEquals(AlertLevel.NONE, profile.alertLevel)
        assertEquals(VibrationMode.OFF, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.SECRET, profile.lockScreenVisibilityMode)
    }

    @Test
    fun softened_profile_reduces_attention_without_changing_channel_or_delivery_mode() {
        val profile = DeliveryProfile.priorityDefaults().softened()

        assertEquals("smartnoti_priority", profile.channelKey)
        assertEquals(DeliveryMode.IMMEDIATE, profile.deliveryMode)
        assertEquals(AlertLevel.SOFT, profile.alertLevel)
        assertEquals(VibrationMode.LIGHT, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
    }
}
