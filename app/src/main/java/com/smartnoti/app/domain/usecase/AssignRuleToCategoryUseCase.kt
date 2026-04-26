package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.rules.RulesRepository

/**
 * Single ordered entry point for "attach this rule to that Category".
 *
 * Plan `docs/plans/2026-04-26-rule-explicit-draft-flag.md` Task 3. Routing
 * a rule to a Category is a two-step persist that must always run in this
 * order:
 *
 *  1. [CategoriesGateway.appendRuleIdToCategory] — persist the new
 *     ownership on the Category side.
 *  2. [RulesGateway.markRuleAsAssigned] — flip the rule's `draft` flag
 *     to `false` so RulesScreen retires it from the "작업 필요" sub-bucket.
 *
 * The Category-side append goes first so a DataStore failure leaves the
 * rule in its current "draft = true" state — the user sees the rule in
 * the same "작업 필요" sub-bucket on the next mount and can retry from
 * the same affordance instead of being stranded with an
 * orphaned-flip-without-ownership row.
 *
 * Both ops are idempotent on their own — re-routing an already-assigned
 * rule still walks the same path (the Category appender dedupes on
 * `ruleIds`, the rule setter no-ops when the row is already
 * `draft = false`).
 *
 * The use case talks to gateway interfaces (not the concrete
 * repositories) so call sites can inject fakes in unit tests without
 * standing up the DataStore stack.
 */
class AssignRuleToCategoryUseCase(
    private val rules: RulesGateway,
    private val categories: CategoriesGateway,
) {
    suspend fun assign(ruleId: String, categoryId: String) {
        categories.appendRuleIdToCategory(categoryId = categoryId, ruleId = ruleId)
        rules.markRuleAsAssigned(ruleId)
    }

    /** Narrow view of [RulesRepository] that this use case actually touches. */
    interface RulesGateway {
        suspend fun markRuleAsAssigned(ruleId: String)
    }

    /** Narrow view of [CategoriesRepository] that this use case actually touches. */
    interface CategoriesGateway {
        suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String)
    }

    companion object {
        /**
         * Bind the use case to the production repository singletons. Wire
         * sites (RulesScreen, future CategoryAssignBottomSheet) can call
         * this and pass the result into a `remember`.
         */
        fun create(
            rulesRepository: RulesRepository,
            categoriesRepository: CategoriesRepository,
        ): AssignRuleToCategoryUseCase {
            val rulesGateway = object : RulesGateway {
                override suspend fun markRuleAsAssigned(ruleId: String) {
                    rulesRepository.markRuleAsAssigned(ruleId)
                }
            }
            val categoriesGateway = object : CategoriesGateway {
                override suspend fun appendRuleIdToCategory(
                    categoryId: String,
                    ruleId: String,
                ) {
                    categoriesRepository.appendRuleIdToCategory(
                        categoryId = categoryId,
                        ruleId = ruleId,
                    )
                }
            }
            return AssignRuleToCategoryUseCase(rulesGateway, categoriesGateway)
        }
    }
}
