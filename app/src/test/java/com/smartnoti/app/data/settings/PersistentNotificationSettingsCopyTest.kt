package com.smartnoti.app.data.settings

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
}
