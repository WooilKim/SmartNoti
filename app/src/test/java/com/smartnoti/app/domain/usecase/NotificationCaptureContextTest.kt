package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureContextTest {

    @Test
    fun shopping_package_respects_quiet_hours_from_context() {
        val context = NotificationContext(
            quietHoursEnabled = true,
            quietHoursPolicy = QuietHoursPolicy(startHour = 23, endHour = 7),
            currentHourOfDay = 23,
            duplicateCountInWindow = 0,
        )

        assertTrue(context.isQuietHoursActive())
    }

    @Test
    fun disabled_quiet_hours_never_activate() {
        val context = NotificationContext(
            quietHoursEnabled = false,
            quietHoursPolicy = QuietHoursPolicy(startHour = 23, endHour = 7),
            currentHourOfDay = 1,
            duplicateCountInWindow = 0,
        )

        assertFalse(context.isQuietHoursActive())
    }
}
