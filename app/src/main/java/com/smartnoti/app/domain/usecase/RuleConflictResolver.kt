package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Picks the winning rule when more than one user rule matched a notification.
 *
 * Plan `rules-ux-v2-inbox-restructure` Phase C Task 1. The resolver encodes the
 * hierarchical-rules policy:
 *
 *  1. Prefer an override whose base is also in the matched set (override is
 *     strictly more specific than its base when both fired).
 *  2. Break ties by rule order in `allRules` (earlier = higher priority).
 *  3. If only one rule matched, return it unchanged.
 *  4. If none matched, return `null`.
 *
 * This is independent from the classifier's free-form matching loop so that
 * UI previews and test fixtures can share the same decision logic.
 */
class RuleConflictResolver {

    fun resolve(
        matched: List<RuleUiModel>,
        allRules: List<RuleUiModel>,
    ): RuleUiModel? {
        if (matched.isEmpty()) return null
        if (matched.size == 1) return matched[0]

        val matchedIds = matched.mapTo(mutableSetOf()) { it.id }
        val orderIndex = allRules.withIndex().associate { (index, rule) -> rule.id to index }

        // Specificity score:
        //  - base rule (`overrideOf == null`): 1
        //  - override whose base is ALSO matched: 2 (strictly more specific)
        //  - override whose base did not fire or is unknown: 1 (behaves like base)
        return matched
            .asSequence()
            .sortedWith(
                compareByDescending<RuleUiModel> { specificityScore(it, matchedIds) }
                    .thenBy { orderIndex[it.id] ?: Int.MAX_VALUE },
            )
            .first()
    }

    private fun specificityScore(rule: RuleUiModel, matchedIds: Set<String>): Int {
        val base = rule.overrideOf ?: return 1
        return if (base in matchedIds) 2 else 1
    }
}
