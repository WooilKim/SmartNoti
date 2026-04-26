package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 3.
 *
 * Pins the contract `SettingsScreen` relies on when rendering the auto-dismiss
 * picker — the option list shape, the label format (with hour rollover at
 * 60-minute multiples), and the "current settings -> spec" projection.
 */
class ReplacementAutoDismissPickerSpecTest {

    private val builder = ReplacementAutoDismissPickerSpecBuilder()

    @Test
    fun spec_mirrors_settings_enabled_and_minutes() {
        val spec = builder.build(
            SmartNotiSettings(
                replacementAutoDismissEnabled = true,
                replacementAutoDismissMinutes = 30,
            ),
        )

        assertTrue(spec.enabled)
        assertEquals(30, spec.selectedMinutes)
    }

    @Test
    fun spec_mirrors_disabled_state() {
        val spec = builder.build(
            SmartNotiSettings(
                replacementAutoDismissEnabled = false,
                replacementAutoDismissMinutes = 60,
            ),
        )

        assertFalse(spec.enabled)
        assertEquals(60, spec.selectedMinutes)
    }

    @Test
    fun option_minute_values_match_plan_fixed_preset() {
        assertEquals(
            listOf(5, 15, 30, 60, 180),
            ReplacementAutoDismissPickerSpecBuilder.MINUTE_VALUES,
        )
        assertEquals(
            listOf(5, 15, 30, 60, 180),
            ReplacementAutoDismissPickerSpecBuilder.OPTIONS.map { it.minutes },
        )
    }

    @Test
    fun option_labels_render_minutes_under_an_hour_and_hours_at_multiples() {
        val labels = ReplacementAutoDismissPickerSpecBuilder.OPTIONS.map { it.label }
        assertEquals(
            listOf("5분", "15분", "30분", "1시간", "3시간"),
            labels,
        )
    }

    @Test
    fun label_helper_handles_arbitrary_minute_inputs() {
        assertEquals(
            "1분",
            ReplacementAutoDismissPickerSpecBuilder.labelFor(1),
        )
        assertEquals(
            "45분",
            ReplacementAutoDismissPickerSpecBuilder.labelFor(45),
        )
        assertEquals(
            "1시간",
            ReplacementAutoDismissPickerSpecBuilder.labelFor(60),
        )
        // 90 minutes is not a clean hour boundary -> stays in minutes.
        assertEquals(
            "90분",
            ReplacementAutoDismissPickerSpecBuilder.labelFor(90),
        )
        assertEquals(
            "2시간",
            ReplacementAutoDismissPickerSpecBuilder.labelFor(120),
        )
    }
}
