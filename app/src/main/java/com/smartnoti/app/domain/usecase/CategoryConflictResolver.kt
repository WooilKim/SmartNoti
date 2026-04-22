package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.RuleTypeUi

/**
 * Picks the winning [Category] from a set of matches, honoring the tie-break
 * contract pinned by Phase P1 [CategoryTieBreakTest] and extended by Phase P2
 * Task 6 of `docs/plans/2026-04-22-categories-split-rules-actions.md`.
 *
 * Specificity, from highest to lowest:
 *  1. **App-pin bonus.** A Category with `appPackageName != null` gets a
 *     constant bonus on top of whatever rule-type rank it already has. This
 *     matches [CategoryTieBreakTest.app_pinned_category_beats_keyword_only_category_regardless_of_order].
 *  2. **Rule-type ladder** (APP > KEYWORD > PERSON > SCHEDULE > REPEAT_BUNDLE).
 *     A Category's rule-type rank is the **max** rank among its member rules
 *     that actually fired (i.e. appear in the `matchedRuleTypes` map). Rules
 *     a Category lists but which did not fire do not contribute — this makes
 *     "what matched now" drive the pick rather than "what the Category
 *     aspires to match".
 *  3. **Drag order.** Within the same specificity tier, the Category with
 *     the lowest `order` wins. The user drags the 분류 tab to re-rank ties
 *     explicitly; there is no hard-coded action precedence (IGNORE vs
 *     SILENT both follow this rule — see [CategoryTieBreakTest.ignore_vs_silent_tie_break_defers_to_order]).
 *
 * If no Category matched, the resolver returns `null`. Callers (the classifier
 * in particular) treat `null` as "fall back to the heuristic chain".
 *
 * `allCategories` is accepted so future tie-break logic (e.g. global drag
 * order across non-matching Categories) can slot in without churning the
 * callsite again. Task 6 itself only consults the matched subset.
 */
class CategoryConflictResolver {

    fun resolve(
        matched: List<Category>,
        @Suppress("UNUSED_PARAMETER") allCategories: List<Category>,
        matchedRuleTypes: Map<String, RuleTypeUi> = emptyMap(),
    ): Category? {
        if (matched.isEmpty()) return null

        return matched
            .asSequence()
            .sortedWith(
                compareByDescending<Category> { specificityScore(it, matchedRuleTypes) }
                    .thenBy { it.order },
            )
            .first()
    }

    /**
     * Returns a single integer encoding both tiers so we can sort once:
     *  - bit 4 (`+16`) for the app-pin bonus,
     *  - bits 0..3 for the rule-type ladder (0 = no matched rule, 1 =
     *    REPEAT_BUNDLE, …, 5 = APP).
     *
     * A Category with no entry in `matchedRuleTypes` still scores 0 for rule
     * type — this happens when the caller passes an empty map (pre-Task 6
     * callers) so behaviour degrades gracefully to drag-order-only.
     */
    private fun specificityScore(
        category: Category,
        matchedRuleTypes: Map<String, RuleTypeUi>,
    ): Int {
        val appPinBonus = if (category.appPackageName != null) APP_PIN_BONUS else 0
        val bestRuleTypeRank = category.ruleIds
            .asSequence()
            .mapNotNull { ruleId -> matchedRuleTypes[ruleId] }
            .map { type -> ruleTypeRank(type) }
            .maxOrNull()
            ?: 0
        return appPinBonus + bestRuleTypeRank
    }

    /**
     * Rule-type rank on the ladder APP > KEYWORD > PERSON > SCHEDULE >
     * REPEAT_BUNDLE. Higher == more specific.
     */
    private fun ruleTypeRank(type: RuleTypeUi): Int = when (type) {
        RuleTypeUi.APP -> 5
        RuleTypeUi.KEYWORD -> 4
        RuleTypeUi.PERSON -> 3
        RuleTypeUi.SCHEDULE -> 2
        RuleTypeUi.REPEAT_BUNDLE -> 1
    }

    companion object {
        private const val APP_PIN_BONUS = 16
    }
}
