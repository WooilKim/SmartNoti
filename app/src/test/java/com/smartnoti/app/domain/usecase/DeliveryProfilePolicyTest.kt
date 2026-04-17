package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.DeliveryMode
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.VibrationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryProfilePolicyTest {

    private val policy = DeliveryProfilePolicy()

    @Test
    fun priority_decision_uses_priority_settings_when_context_is_normal() {
        val profile = policy.resolve(
            decision = NotificationDecision.PRIORITY,
            settings = SmartNotiSettings(
                priorityAlertLevel = "LOUD",
                priorityVibrationMode = "STRONG",
                priorityHeadsUpEnabled = true,
                priorityLockScreenVisibility = "PUBLIC",
            ),
            context = notificationContext(),
            isPersistent = false,
        )

        assertEquals(DeliveryMode.IMMEDIATE, profile.deliveryMode)
        assertEquals("smartnoti_priority", profile.channelKey)
        assertEquals(AlertLevel.LOUD, profile.alertLevel)
        assertEquals(VibrationMode.STRONG, profile.vibrationMode)
        assertTrue(profile.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.PUBLIC, profile.lockScreenVisibilityMode)
    }

    @Test
    fun quiet_hours_disable_heads_up_for_priority_without_replacing_other_settings() {
        val profile = policy.resolve(
            decision = NotificationDecision.PRIORITY,
            settings = SmartNotiSettings(
                priorityAlertLevel = "LOUD",
                priorityVibrationMode = "STRONG",
                priorityHeadsUpEnabled = true,
                priorityLockScreenVisibility = "PRIVATE",
            ),
            context = notificationContext(quietHoursEnabled = true, currentHour = 23),
            isPersistent = false,
        )

        assertEquals(DeliveryMode.IMMEDIATE, profile.deliveryMode)
        assertEquals(AlertLevel.LOUD, profile.alertLevel)
        assertEquals(VibrationMode.STRONG, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
    }

    @Test
    fun duplicates_soften_alerts_and_disable_heads_up() {
        val profile = policy.resolve(
            decision = NotificationDecision.PRIORITY,
            settings = SmartNotiSettings(
                priorityAlertLevel = "LOUD",
                priorityVibrationMode = "STRONG",
                priorityHeadsUpEnabled = true,
            ),
            context = notificationContext(duplicateCount = 3),
            isPersistent = false,
        )

        assertEquals(DeliveryMode.IMMEDIATE, profile.deliveryMode)
        assertEquals(AlertLevel.SOFT, profile.alertLevel)
        assertEquals(VibrationMode.LIGHT, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
    }

    @Test
    fun persistent_notifications_become_safe_log_only_profiles() {
        val profile = policy.resolve(
            decision = NotificationDecision.PRIORITY,
            settings = SmartNotiSettings(
                priorityAlertLevel = "LOUD",
                priorityVibrationMode = "STRONG",
                priorityHeadsUpEnabled = true,
                priorityLockScreenVisibility = "PUBLIC",
            ),
            context = notificationContext(),
            isPersistent = true,
        )

        assertEquals(DeliveryMode.LOG_ONLY, profile.deliveryMode)
        assertEquals("smartnoti_silent", profile.channelKey)
        assertEquals(AlertLevel.NONE, profile.alertLevel)
        assertEquals(VibrationMode.OFF, profile.vibrationMode)
        assertFalse(profile.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.PRIVATE, profile.lockScreenVisibilityMode)
    }

    @Test
    fun invalid_setting_strings_fall_back_safely_to_decision_defaults() {
        val priorityProfile = policy.resolve(
            decision = NotificationDecision.PRIORITY,
            settings = SmartNotiSettings(
                priorityAlertLevel = "loudest",
                priorityVibrationMode = "default",
                priorityLockScreenVisibility = "friends-only",
            ),
            context = notificationContext(),
            isPersistent = false,
        )
        val digestProfile = policy.resolve(
            decision = NotificationDecision.DIGEST,
            settings = SmartNotiSettings(
                digestAlertLevel = "low",
                digestVibrationMode = "off",
                digestLockScreenVisibility = "private",
            ),
            context = notificationContext(),
            isPersistent = false,
        )
        val silentProfile = policy.resolve(
            decision = NotificationDecision.SILENT,
            settings = SmartNotiSettings(
                silentAlertLevel = "minimal",
                silentVibrationMode = "off",
                silentLockScreenVisibility = "secret",
            ),
            context = notificationContext(),
            isPersistent = false,
        )

        assertEquals(AlertLevel.LOUD, priorityProfile.alertLevel)
        assertEquals(VibrationMode.STRONG, priorityProfile.vibrationMode)
        assertEquals(LockScreenVisibilityMode.PRIVATE, priorityProfile.lockScreenVisibilityMode)
        assertEquals(AlertLevel.SOFT, digestProfile.alertLevel)
        assertEquals(VibrationMode.OFF, digestProfile.vibrationMode)
        assertEquals(LockScreenVisibilityMode.PRIVATE, digestProfile.lockScreenVisibilityMode)
        assertEquals(AlertLevel.NONE, silentProfile.alertLevel)
        assertEquals(VibrationMode.OFF, silentProfile.vibrationMode)
        assertEquals(LockScreenVisibilityMode.SECRET, silentProfile.lockScreenVisibilityMode)
    }

    private fun notificationContext(
        quietHoursEnabled: Boolean = false,
        currentHour: Int = 12,
        duplicateCount: Int = 0,
    ) = NotificationContext(
        quietHoursEnabled = quietHoursEnabled,
        quietHoursPolicy = QuietHoursPolicy(startHour = 22, endHour = 7),
        currentHourOfDay = currentHour,
        duplicateCountInWindow = duplicateCount,
    )
}
