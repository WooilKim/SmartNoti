package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleUiModel

data class RuleListGroup(
    val title: String,
    val subtitle: String,
    val action: RuleActionUi,
    val rules: List<RuleUiModel>,
)

class RuleListGroupingBuilder {
    fun build(rules: List<RuleUiModel>): List<RuleListGroup> {
        return orderedActions.mapNotNull { action ->
            val groupedRules = rules.filter { rule -> rule.action == action }
            if (groupedRules.isEmpty()) {
                null
            } else {
                RuleListGroup(
                    title = action.toGroupTitle(),
                    subtitle = "규칙 ${groupedRules.size}개",
                    action = action,
                    rules = groupedRules,
                )
            }
        }
    }

    private fun RuleActionUi.toGroupTitle(): String = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> "즉시 전달"
        RuleActionUi.DIGEST -> "Digest"
        RuleActionUi.SILENT -> "조용히"
        RuleActionUi.CONTEXTUAL -> "상황별"
    }

    companion object {
        private val orderedActions = listOf(
            RuleActionUi.ALWAYS_PRIORITY,
            RuleActionUi.DIGEST,
            RuleActionUi.SILENT,
            RuleActionUi.CONTEXTUAL,
        )
    }
}
