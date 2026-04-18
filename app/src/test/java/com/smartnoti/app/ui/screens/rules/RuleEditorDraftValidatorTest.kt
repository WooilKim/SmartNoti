package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleTypeUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEditorDraftValidatorTest {

    private val validator = RuleEditorDraftValidator()

    @Test
    fun schedule_rule_can_save_with_hours_even_when_match_value_field_is_empty() {
        val canSave = validator.canSave(
            title = "야간 시간대",
            matchValue = "",
            type = RuleTypeUi.SCHEDULE,
            scheduleStartHour = "23",
            scheduleEndHour = "7",
        )

        assertTrue(canSave)
    }

    @Test
    fun repeat_bundle_rule_requires_numeric_threshold() {
        val canSave = validator.canSave(
            title = "반복 알림",
            matchValue = "회 이상",
            type = RuleTypeUi.REPEAT_BUNDLE,
            scheduleStartHour = "",
            scheduleEndHour = "",
        )

        assertFalse(canSave)
    }
}
