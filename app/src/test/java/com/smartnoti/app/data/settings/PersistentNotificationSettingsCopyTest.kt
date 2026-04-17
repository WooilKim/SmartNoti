package com.smartnoti.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentNotificationSettingsCopyTest {

    @Test
    fun enables_critical_persistent_exceptions_by_default() {
        assertTrue(SmartNotiSettings().protectCriticalPersistentNotifications)
    }

    @Test
    fun allows_turning_off_critical_persistent_exceptions() {
        val settings = SmartNotiSettings(
            protectCriticalPersistentNotifications = false,
        )

        assertFalse(settings.protectCriticalPersistentNotifications)
    }

    @Test
    fun delivery_profile_defaults_are_exposed_in_settings_model() {
        val settings = SmartNotiSettings()

        assertEquals("LOUD", settings.priorityAlertLevel)
        assertEquals("STRONG", settings.priorityVibrationMode)
        assertEquals(true, settings.priorityHeadsUpEnabled)
        assertEquals("PRIVATE", settings.priorityLockScreenVisibility)
        assertEquals("SOFT", settings.digestAlertLevel)
        assertEquals("LIGHT", settings.digestVibrationMode)
        assertEquals(false, settings.digestHeadsUpEnabled)
        assertEquals("PRIVATE", settings.digestLockScreenVisibility)
        assertEquals("NONE", settings.silentAlertLevel)
        assertEquals("OFF", settings.silentVibrationMode)
        assertEquals(false, settings.silentHeadsUpEnabled)
        assertEquals("SECRET", settings.silentLockScreenVisibility)
    }
}
