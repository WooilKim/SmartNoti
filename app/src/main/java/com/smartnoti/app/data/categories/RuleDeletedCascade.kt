package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category

/**
 * RED-phase skeleton for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 5
 * (Drift #3 — rule delete cascade).
 *
 * Pure helper that strips [ruleId] from every Category's `ruleIds`. Empty
 * `ruleIds` Categories are preserved so the user sees a "rule-less"
 * Category they can edit, not silently orphaned state. Task 5 replaces
 * the body with the real implementation; today it throws
 * [NotImplementedError] so Task 1's cascade tests RED.
 */
@Suppress("UNUSED_PARAMETER")
fun applyRuleDeletedCascade(
    categories: List<Category>,
    ruleId: String,
): List<Category> {
    TODO("Plan task 5: strip ruleId from every Category.ruleIds; preserve empty lists")
}
