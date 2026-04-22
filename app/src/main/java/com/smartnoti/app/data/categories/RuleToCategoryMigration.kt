package com.smartnoti.app.data.categories

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Pure computation for the first-launch post-upgrade migration that lifts
 * every existing [RuleUiModel] into a 1:1 [Category].
 *
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1
 * Task 3. The runner-side glue (reading `RulesRepository` / writing
 * `CategoriesRepository` / flipping the `SettingsRepository` flag) lives in
 * [MigrateRulesToCategoriesRunner] so this object stays free of I/O and
 * trivially unit-testable.
 *
 * Contract pinned by [RuleToCategoryMigrationTest]:
 *
 *  - Each Rule → one Category with id `cat-from-rule-<ruleId>` (idempotent
 *    across re-runs and across crashes partway through a previous pass).
 *  - Name = `rule.matchValue` (user-facing phrase they already associate
 *    with the rule — the migration is supposed to be invisible).
 *  - `ruleIds = [rule.id]`, `action` inherited from `rule.action`.
 *  - `appPackageName = rule.matchValue` when `rule.type == APP` so the
 *    Category wins the app-pin specificity bonus in
 *    [com.smartnoti.app.domain.usecase.CategoryConflictResolver]; null for
 *    all other rule types.
 *  - `order` = sequential (0-indexed) starting at the max existing order + 1
 *    so user-made Categories that happened to land first keep their slot.
 *  - Rules that cannot be mapped to a Category action (e.g. the legacy
 *    `CONTEXTUAL` bucket) are SKIPPED rather than shoehorned — the
 *    per-journey tests still enumerate CONTEXTUAL behaviour separately.
 *  - Existing Categories already carrying `id == cat-from-rule-<ruleId>`
 *    are left untouched (idempotent).
 */
object RuleToCategoryMigration {

    const val ID_PREFIX: String = "cat-from-rule-"

    fun migrate(
        rules: List<RuleUiModel>,
        existingCategories: List<Category>,
    ): List<Category> {
        val existingIds = existingCategories.map { it.id }.toSet()
        val nextOrderStart = (existingCategories.maxOfOrNull { it.order } ?: -1) + 1

        val appended = rules
            .asSequence()
            .mapNotNull { rule -> buildCategory(rule) }
            .filter { category -> category.id !in existingIds }
            .toList()

        // Re-number the appended slice so it starts at nextOrderStart, keeping
        // every pre-existing Category's order untouched.
        val reindexedAppended = appended.mapIndexed { index, category ->
            category.copy(order = nextOrderStart + index)
        }

        return existingCategories + reindexedAppended
    }

    private fun buildCategory(rule: RuleUiModel): Category? {
        val action = rule.action.toCategoryActionOrNull() ?: return null
        return Category(
            id = "$ID_PREFIX${rule.id}",
            name = rule.matchValue.ifBlank { rule.title },
            appPackageName = if (rule.type == RuleTypeUi.APP) rule.matchValue else null,
            ruleIds = listOf(rule.id),
            action = action,
            order = 0, // overwritten by `migrate` once we know the final position.
        )
    }

    private fun RuleActionUi.toCategoryActionOrNull(): CategoryAction? = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> CategoryAction.PRIORITY
        RuleActionUi.DIGEST -> CategoryAction.DIGEST
        RuleActionUi.SILENT -> CategoryAction.SILENT
        RuleActionUi.IGNORE -> CategoryAction.IGNORE
        // CONTEXTUAL carries no deterministic Category.action mapping; the
        // classifier previously treated it as SILENT, but producing a SILENT
        // Category silently would capture the rule under an inappropriate
        // bucket forever. Drop it from the migration — the user keeps the
        // Rule, they just won't get a Category for it until they revisit.
        RuleActionUi.CONTEXTUAL -> null
    }
}
