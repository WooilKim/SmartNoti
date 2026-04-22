package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category

/**
 * Plan `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 5
 * (Drift #3 — rule delete cascade).
 *
 * Pure helper that strips [ruleId] from every Category's `ruleIds`.
 * Categories whose `ruleIds` become empty are **preserved** — the user
 * sees a "rule-less" Category they can edit in the 분류 tab. Silently
 * dropping empty Categories would feel like lost user configuration.
 *
 * The cascade is idempotent: running it twice produces the same list the
 * first run returned (no element referenced the id on the second pass).
 * Other Category fields (`name`, `action`, `order`, `appPackageName`)
 * are untouched.
 */
fun applyRuleDeletedCascade(
    categories: List<Category>,
    ruleId: String,
): List<Category> {
    var changed = false
    val result = categories.map { category ->
        if (ruleId !in category.ruleIds) {
            category
        } else {
            changed = true
            category.copy(ruleIds = category.ruleIds.filterNot { it == ruleId })
        }
    }
    // Return the original list when no Category referenced the id so
    // reference equality still holds for callers short-circuiting on
    // "did anything change?" (see `CategoriesRepository.onRuleDeleted`).
    return if (changed) result else categories
}
