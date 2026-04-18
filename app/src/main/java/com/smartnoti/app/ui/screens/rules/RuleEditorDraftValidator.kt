package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleTypeUi

class RuleEditorDraftValidator {
    fun canSave(
        title: String,
        matchValue: String,
        type: RuleTypeUi,
        scheduleStartHour: String,
        scheduleEndHour: String,
    ): Boolean {
        if (title.trim().isBlank()) return false

        return when (type) {
            RuleTypeUi.SCHEDULE -> scheduleStartHour.isNotBlank() && scheduleEndHour.isNotBlank()
            RuleTypeUi.REPEAT_BUNDLE -> matchValue.any(Char::isDigit)
            else -> matchValue.trim().isNotBlank()
        }
    }
}
