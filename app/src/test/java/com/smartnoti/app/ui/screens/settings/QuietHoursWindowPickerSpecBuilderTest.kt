package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-26-settings-quiet-hours-window-editor.md` Task 3.
 *
 * Pins the visibility / enabled / same-value-warning decision for the new
 * Quiet Hours start/end pickers so the call-site `OperationalSummaryCard`
 * stays a thin renderer. Compose UI tests aren't available in this module,
 * so the visibility rule is extracted into a pure builder we can unit-test.
 *
 * 1차 안 (per the plan's Product intent / assumptions):
 *   - Pickers are visible **only when** `quietHoursEnabled = true` —
 *     OFF state hides them entirely so the user doesn't fiddle with a
 *     no-op control.
 *   - `start == end` is permitted (the underlying `QuietHoursPolicy` has
 *     ambiguous semantics for that case) but the spec carries a warning
 *     copy so the UI can render an inline hint next to the pickers.
 *   - Hour labels follow the existing `formatHour` convention (`%02d:00`)
 *     so the picker labels and the surrounding summary row stay in sync.
 */
class QuietHoursWindowPickerSpecBuilderTest {

    private val builder = QuietHoursWindowPickerSpecBuilder()

    @Test
    fun spec_is_visible_when_quiet_hours_enabled() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursStartHour = 23,
                quietHoursEndHour = 7,
            )
        )

        assertTrue(spec.visible)
        assertEquals(23, spec.startHour)
        assertEquals(7, spec.endHour)
    }

    @Test
    fun spec_is_hidden_when_quiet_hours_disabled() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = false,
                quietHoursStartHour = 23,
                quietHoursEndHour = 7,
            )
        )

        assertFalse(spec.visible)
    }

    @Test
    fun spec_exposes_full_zero_to_twentythree_hour_options_with_padded_labels() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursStartHour = 0,
                quietHoursEndHour = 23,
            )
        )

        assertEquals(24, spec.hourOptions.size)
        assertEquals(QuietHoursWindowPickerSpec.HourOption(hour = 0, label = "00:00"), spec.hourOptions.first())
        assertEquals(QuietHoursWindowPickerSpec.HourOption(hour = 9, label = "09:00"), spec.hourOptions[9])
        assertEquals(QuietHoursWindowPickerSpec.HourOption(hour = 23, label = "23:00"), spec.hourOptions.last())
    }

    @Test
    fun spec_warns_when_start_equals_end_and_picker_is_visible() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursStartHour = 12,
                quietHoursEndHour = 12,
            )
        )

        assertTrue(spec.visible)
        assertNotNull(
            "start == end while ON must surface a warning copy so the user knows the window is ambiguous",
            spec.sameValueWarning,
        )
    }

    @Test
    fun spec_does_not_warn_when_start_and_end_differ() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = true,
                quietHoursStartHour = 23,
                quietHoursEndHour = 7,
            )
        )

        assertNull(spec.sameValueWarning)
    }

    @Test
    fun spec_does_not_warn_when_picker_is_hidden_even_if_values_collide() {
        val spec = builder.build(
            SmartNotiSettings(
                quietHoursEnabled = false,
                quietHoursStartHour = 12,
                quietHoursEndHour = 12,
            )
        )

        assertFalse(spec.visible)
        // No warning while hidden — the hint would be invisible noise.
        assertNull(spec.sameValueWarning)
    }
}
