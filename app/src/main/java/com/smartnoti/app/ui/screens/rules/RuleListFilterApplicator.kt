package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleUiModel

class RuleListFilterApplicator {
    fun apply(
        rules: List<RuleUiModel>,
        action: RuleActionUi?,
    ): List<RuleUiModel> {
        return if (action == null) {
            rules
        } else {
            rules.filter { rule -> rule.action == action }
        }
    }
}
