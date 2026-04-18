package com.smartnoti.app.ui.screens.rules

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleEditorRepeatBundleThresholdControllerTest {

    private val controller = RuleEditorRepeatBundleThresholdController()

    @Test
    fun normalize_strips_non_digits_and_leading_zeroes() {
        assertEquals("5", controller.normalize(" 05회 이상 "))
    }

    @Test
    fun decrement_never_goes_below_one() {
        assertEquals("1", controller.decrement("1"))
        assertEquals("1", controller.decrement(""))
    }

    @Test
    fun increment_advances_to_next_threshold() {
        assertEquals("4", controller.increment("3"))
    }

    @Test
    fun presets_expose_operator_friendly_labels() {
        assertEquals(
            listOf("2회 이상", "3회 이상", "5회 이상", "8회 이상"),
            controller.presets.map { it.label },
        )
    }
}
