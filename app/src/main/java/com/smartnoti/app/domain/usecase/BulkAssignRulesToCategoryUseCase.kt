package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.rules.RulesRepository

/**
 * Single ordered entry point for "attach this list of rules to that
 * Category" — used by the RulesScreen multi-select flow.
 *
 * Plan `docs/plans/2026-04-26-rules-bulk-assign-unassigned.md` Task 2.
 *
 * Thin wrapper that delegates each rule to [AssignRuleToCategoryUseCase]
 * so the underlying append-then-flip ordering contract (Category-side
 * append first, rule `draft = false` flip second) is reused verbatim and
 * cannot regress for the bulk path.
 *
 * Semantics:
 *  - **Empty list** short-circuits before any gateway call (`isEmpty()`
 *    early return).
 *  - **Duplicate ids** in the input are deduplicated via `distinct()`
 *    before delegation so each rule walks the path exactly once.
 *  - **Failure mid-loop** — if a per-rule call throws, the throwable
 *    propagates immediately. Already-committed rules stay assigned;
 *    the remaining rules are not touched. The wrapper has no
 *    transaction responsibility — same policy as the single-rule path
 *    (the caller can show retry UI from the same affordance).
 */
class BulkAssignRulesToCategoryUseCase(
    private val single: AssignRuleToCategoryUseCase,
) {
    suspend fun assign(ruleIds: List<String>, categoryId: String) {
        if (ruleIds.isEmpty()) return
        ruleIds.distinct().forEach { ruleId ->
            single.assign(ruleId = ruleId, categoryId = categoryId)
        }
    }

    companion object {
        /**
         * Bind the wrapper to the production repository singletons. Wire
         * sites (RulesScreen multi-select call site) call this and pass
         * the result into a `remember`.
         */
        fun create(
            rulesRepository: RulesRepository,
            categoriesRepository: CategoriesRepository,
        ): BulkAssignRulesToCategoryUseCase {
            return BulkAssignRulesToCategoryUseCase(
                AssignRuleToCategoryUseCase.create(
                    rulesRepository = rulesRepository,
                    categoriesRepository = categoriesRepository,
                ),
            )
        }
    }
}
