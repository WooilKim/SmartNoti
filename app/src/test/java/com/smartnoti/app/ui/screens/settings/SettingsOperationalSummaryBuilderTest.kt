package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsOperationalSummaryBuilderTest {

    private val builder = SettingsOperationalSummaryBuilder()

    @Test
    fun builds_summary_when_quiet_hours_enabled() {
        val summary = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursStartHour = 23,
                quietHoursEndHour = 7,
                digestHours = listOf(12, 18, 21),
            )
        )

        assertEquals("조용한 시간 자동 적용", summary.modeTitle)
        assertEquals("지정한 시간대에는 덜 급한 알림을 정리함 중심으로 다뤄요.", summary.modeDetail)
        assertEquals("23:00 ~ 07:00", summary.quietHoursWindow)
        assertEquals("자동 완화 사용 중", summary.quietHoursState)
        assertEquals("12:00 · 18:00 · 21:00", summary.digestSchedule)
        assertEquals("정리 시점 3개", summary.digestDetail)
        assertEquals(true, summary.quietHoursEnabled)
    }

    @Test
    fun builds_summary_when_quiet_hours_disabled() {
        val summary = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = false,
                quietHoursStartHour = 22,
                quietHoursEndHour = 6,
                digestHours = listOf(9),
            )
        )

        assertEquals("항상 즉시 분류", summary.modeTitle)
        assertEquals("모든 시간대에 동일한 기준으로 바로 분류해요.", summary.modeDetail)
        assertEquals("22:00 ~ 06:00", summary.quietHoursWindow)
        assertEquals("꺼짐", summary.quietHoursState)
        assertEquals("09:00", summary.digestSchedule)
        assertEquals("정리 시점 1개", summary.digestDetail)
        assertEquals(false, summary.quietHoursEnabled)
    }

    @Test
    fun pads_single_digit_hours_with_leading_zero_for_alignment() {
        val summary = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursStartHour = 9,
                quietHoursEndHour = 5,
                digestHours = listOf(8, 20),
            )
        )

        assertEquals("09:00 ~ 05:00", summary.quietHoursWindow)
        assertEquals("08:00 · 20:00", summary.digestSchedule)
    }

    @Test
    fun digest_detail_handles_empty_schedule() {
        val summary = builder.build(
            SmartNotiSettings(
                digestHours = emptyList(),
            )
        )

        assertEquals("예약된 정리 시점이 없어요", summary.digestSchedule)
        assertEquals("정리 시점 없음", summary.digestDetail)
    }
}
