package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Builds the list of rules that are eligible to be chosen as the *base* of a
 * new or edited override rule in [RulesScreen]'s editor dialog.
 *
 * Plan `rules-ux-v2-inbox-restructure` Phase C Task 4. The "어느 규칙의 예외인가요?"
 * dropdown must hide entries that would produce a broken graph:
 *   - A rule cannot override itself (would become a self-reference).
 *   - A rule cannot override another override (plan open question #3 limits
 *     chain depth to one level).
 *
 * The builder is a pure function so the selection policy is unit-tested apart
 * from the Compose dialog.
 */
class RuleEditorOverrideOptionsBuilder {

    fun build(
        allRules: List<RuleUiModel>,
        editingRuleId: String?,
    ): List<RuleUiModel> {
        return allRules.filter { candidate ->
            candidate.overrideOf == null && candidate.id != editingRuleId
        }
    }
}
