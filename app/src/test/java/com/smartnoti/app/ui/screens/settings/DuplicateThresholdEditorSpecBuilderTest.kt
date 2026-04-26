package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 5.
 *
 * Pins the candidate lists, label format, and selected-value mapping for the
 * "중복 알림 묶기" editor row so the Compose renderer stays a thin shell. We
 * deliberately fix the option lists in the spec builder rather than passing
 * them in from outside — product judgment lives in one place, easy to revisit.
 */
class DuplicateThresholdEditorSpecBuilderTest {

    private val builder = DuplicateThresholdEditorSpecBuilder()

    @Test
    fun spec_reflects_default_settings_values() {
        val spec = builder.build(SmartNotiSettings())

        assertEquals(3, spec.selectedThreshold)
        assertEquals(10, spec.selectedWindowMinutes)
    }

    @Test
    fun spec_reflects_custom_settings_values() {
        val spec = builder.build(
            SmartNotiSettings(
                duplicateDigestThreshold = 5,
                duplicateWindowMinutes = 30,
            )
        )

        assertEquals(5, spec.selectedThreshold)
        assertEquals(30, spec.selectedWindowMinutes)
    }

    @Test
    fun threshold_options_are_two_three_four_five_seven_ten_in_order() {
        val spec = builder.build(SmartNotiSettings())

        assertEquals(
            listOf(2, 3, 4, 5, 7, 10),
            spec.thresholdOptions.map { it.value },
        )
    }

    @Test
    fun threshold_option_labels_use_repeat_count_format() {
        val spec = builder.build(SmartNotiSettings())

        assertEquals(
            listOf("반복 2회", "반복 3회", "반복 4회", "반복 5회", "반복 7회", "반복 10회"),
            spec.thresholdOptions.map { it.label },
        )
    }

    @Test
    fun window_options_are_five_ten_fifteen_thirty_sixty_in_order() {
        val spec = builder.build(SmartNotiSettings())

        assertEquals(
            listOf(5, 10, 15, 30, 60),
            spec.windowOptions.map { it.minutes },
        )
    }

    @Test
    fun window_option_labels_use_recent_minutes_format() {
        val spec = builder.build(SmartNotiSettings())

        assertEquals(
            listOf("최근 5분", "최근 10분", "최근 15분", "최근 30분", "최근 60분"),
            spec.windowOptions.map { it.label },
        )
    }

    @Test
    fun threshold_options_minimum_is_two_to_avoid_aggressive_one_value() {
        // Plan rationale: `1` would route every same-content notification
        // straight to DIGEST. The repository's `coerceAtLeast(1)` guard is
        // defense-in-depth; the dropdown intentionally hides `1`.
        val spec = builder.build(SmartNotiSettings())

        assertEquals(2, spec.thresholdOptions.first().value)
    }

    @Test
    fun spec_carries_persisted_value_even_when_off_the_dropdown_list() {
        // Robustness: if a future migration or DataStore corruption left a
        // value that is no longer in the candidate list, the picker still
        // reports it as selected so the user sees the truth (the renderer
        // can format the chip label from the persisted value directly).
        val spec = builder.build(
            SmartNotiSettings(
                duplicateDigestThreshold = 6,
                duplicateWindowMinutes = 45,
            )
        )

        assertEquals(6, spec.selectedThreshold)
        assertEquals(45, spec.selectedWindowMinutes)
    }
}
