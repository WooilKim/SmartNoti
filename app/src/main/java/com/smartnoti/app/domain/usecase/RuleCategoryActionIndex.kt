package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleActionUi

/**
 * Reverse-index helper for plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 4.
 *
 * Given the current `List<Category>` snapshot, answers "what action (if any)
 * does this rule resolve to?" by looking up the owning Category (lowest
 * `order` wins when a rule is claimed by multiple Categories — matches
 * [CategoryConflictResolver]'s ordering contract). Used by:
 *
 *   - Classifier: lift matched Rule → owning Category → apply action.
 *   - Rule list UI (RuleListPresentationBuilder / Filter / Grouping): decide
 *     which action bucket a rule sits in for the overview / filter chips.
 *   - Rule editor: prefill the `draftAction` dropdown when a rule is edited.
 *
 * The helper is a pure function of the Category snapshot — it does no I/O
 * and the caller is expected to rebuild the index whenever the Category
 * graph changes. A rule not claimed by any Category returns null; UI
 * callers treat null as "no action chosen yet" (usually shown as SILENT
 * default in the list, blank in the editor).
 */
class RuleCategoryActionIndex(categories: List<Category>) {

    private val ruleIdToAction: Map<String, RuleActionUi> = buildMap {
        categories
            .asSequence()
            .sortedBy { it.order }
            .forEach { category ->
                category.ruleIds.forEach { ruleId ->
                    // `putIfAbsent` semantics — first (lowest-order) Category
                    // that claims the rule wins, so the derived action
                    // matches the tie-break used by CategoryConflictResolver.
                    if (!containsKey(ruleId)) {
                        put(ruleId, category.action.toRuleActionUi())
                    }
                }
            }
    }

    fun actionFor(ruleId: String): RuleActionUi? = ruleIdToAction[ruleId]

    fun actionForOrDefault(ruleId: String, default: RuleActionUi = RuleActionUi.SILENT): RuleActionUi {
        return ruleIdToAction[ruleId] ?: default
    }

    companion object {
        fun CategoryAction.toRuleActionUi(): RuleActionUi = when (this) {
            CategoryAction.PRIORITY -> RuleActionUi.ALWAYS_PRIORITY
            CategoryAction.DIGEST -> RuleActionUi.DIGEST
            CategoryAction.SILENT -> RuleActionUi.SILENT
            CategoryAction.IGNORE -> RuleActionUi.IGNORE
        }

        fun RuleActionUi.toCategoryActionOrNull(): CategoryAction? = when (this) {
            RuleActionUi.ALWAYS_PRIORITY -> CategoryAction.PRIORITY
            RuleActionUi.DIGEST -> CategoryAction.DIGEST
            RuleActionUi.SILENT -> CategoryAction.SILENT
            RuleActionUi.IGNORE -> CategoryAction.IGNORE
            // Legacy CONTEXTUAL has no Category analogue; callers treat it as
            // "leave Category unset".
            RuleActionUi.CONTEXTUAL -> null
        }
    }
}
