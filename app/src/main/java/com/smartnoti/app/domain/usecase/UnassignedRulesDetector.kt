package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Pure helper that resolves which Rules are not claimed by any Category.
 *
 * Plan `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md` Task 6:
 * after the rule editor stops auto-creating a 1:1 Category, a Rule lives in a
 * "미분류" draft state until the user picks a Category in the post-save sheet.
 * Surfaces (RulesScreen "미분류" chip + CategoriesScreen 안내 카드) ask this
 * detector "which rule rows belong in the 미분류 bucket?".
 *
 * The detector is intentionally a pure function so callers (Compose `remember`,
 * tests) can re-derive the set without owning any state. Output preserves the
 * input rule order so list rendering stays deterministic.
 */
class UnassignedRulesDetector {
    fun detect(
        rules: List<RuleUiModel>,
        categories: List<Category>,
    ): List<RuleUiModel> {
        if (rules.isEmpty()) return emptyList()
        val claimedRuleIds: Set<String> = if (categories.isEmpty()) {
            emptySet()
        } else {
            buildSet {
                categories.forEach { category ->
                    category.ruleIds.forEach { add(it) }
                }
            }
        }
        return rules.filter { rule -> rule.id !in claimedRuleIds }
    }
}
