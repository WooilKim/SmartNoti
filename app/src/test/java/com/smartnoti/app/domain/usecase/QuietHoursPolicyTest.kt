package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursPolicyTest {

    @Test
    fun time_inside_same_day_range_is_quiet() {
        val policy = QuietHoursPolicy(startHour = 9, endHour = 18)

        assertTrue(policy.isQuietAt(hourOfDay = 10))
        assertFalse(policy.isQuietAt(hourOfDay = 20))
    }

    @Test
    fun overnight_range_wraps_past_midnight() {
        val policy = QuietHoursPolicy(startHour = 23, endHour = 7)

        assertTrue(policy.isQuietAt(hourOfDay = 23))
        assertTrue(policy.isQuietAt(hourOfDay = 2))
        assertFalse(policy.isQuietAt(hourOfDay = 14))
    }
}
