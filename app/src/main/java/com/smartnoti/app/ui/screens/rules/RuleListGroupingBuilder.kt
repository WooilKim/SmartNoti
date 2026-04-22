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
    fun build(
        rules: List<RuleUiModel>,
        ruleActions: Map<String, RuleActionUi> = emptyMap(),
    ): List<RuleListGroup> {
        return orderedActions.mapNotNull { action ->
            val groupedRules = rules.filter { rule -> ruleActions[rule.id] == action }
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
        // Final group ordering + copy are finalized in Task 5 of plan
        // `2026-04-21-ignore-tier-fourth-decision`.
        RuleActionUi.IGNORE -> "무시"
    }

    companion object {
        private val orderedActions = listOf(
            RuleActionUi.ALWAYS_PRIORITY,
            RuleActionUi.DIGEST,
            RuleActionUi.SILENT,
            RuleActionUi.CONTEXTUAL,
            // IGNORE group is registered here so existing IGNORE rules surface
            // in the list; the editor dropdown + rule-creation entry point
            // arrive in Task 5 of plan
            // `2026-04-21-ignore-tier-fourth-decision`.
            RuleActionUi.IGNORE,
        )
    }
}
