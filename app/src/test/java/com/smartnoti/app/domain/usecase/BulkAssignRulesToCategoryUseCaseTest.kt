package com.smartnoti.app.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-26-rules-bulk-assign-unassigned.md` Task 1.
 *
 * Pin the contract of [BulkAssignRulesToCategoryUseCase] — a thin wrapper
 * around [AssignRuleToCategoryUseCase] that lets the RulesScreen route N
 * unassigned rules into a single Category in one call. The wrapper
 * delegates the per-rule append-then-flip ordering to the single-rule use
 * case so the two-step contract (Category append first, then rule
 * `draft = false` flip) cannot regress.
 *
 *  1. **Happy path** — calling `assign(ruleIds = [r1, r2, r3], cat-X)` runs
 *     `appendRuleIdToCategory(cat-X, rN) -> markRuleAsAssigned(rN)` for
 *     each ruleId in the input order, with no interleaving.
 *  2. **Idempotent input** — duplicate ids in the input list are
 *     deduplicated before delegation so each id walks the path exactly
 *     once.
 *  3. **Empty list** — short-circuits before any gateway call so the
 *     caller never accidentally invokes a no-op write storm.
 *  4. **Partial failure mid-loop** — when the second ruleId's Category
 *     append throws, the first rule's two ops stay committed, the second
 *     rule's flip never runs, and the throwable propagates so the caller
 *     can show retry UI. The use case has no transaction responsibility —
 *     same policy as the single-rule path.
 */
class BulkAssignRulesToCategoryUseCaseTest {

    @Test
    fun assign_runs_append_then_flip_for_each_rule_in_order() = runBlocking {
        val clock = Clock()
        val rules = RecordingRulesRepositoryFake(clock)
        val categories = RecordingCategoriesRepositoryFake(clock)
        val useCase = BulkAssignRulesToCategoryUseCase(
            AssignRuleToCategoryUseCase(rules, categories),
        )

        useCase.assign(
            ruleIds = listOf("keyword:r1", "keyword:r2", "keyword:r3"),
            categoryId = "cat-priority",
        )

        assertEquals(
            listOf(
                "append:cat-priority+keyword:r1",
                "mark-assigned:keyword:r1",
                "append:cat-priority+keyword:r2",
                "mark-assigned:keyword:r2",
                "append:cat-priority+keyword:r3",
                "mark-assigned:keyword:r3",
            ),
            interleavedOps(rules, categories),
        )
    }

    @Test
    fun assign_dedupes_duplicate_rule_ids_before_delegating() = runBlocking {
        val clock = Clock()
        val rules = RecordingRulesRepositoryFake(clock)
        val categories = RecordingCategoriesRepositoryFake(clock)
        val useCase = BulkAssignRulesToCategoryUseCase(
            AssignRuleToCategoryUseCase(rules, categories),
        )

        useCase.assign(
            ruleIds = listOf("keyword:r1", "keyword:r2", "keyword:r1"),
            categoryId = "cat-priority",
        )

        assertEquals(
            listOf(
                "append:cat-priority+keyword:r1",
                "mark-assigned:keyword:r1",
                "append:cat-priority+keyword:r2",
                "mark-assigned:keyword:r2",
            ),
            interleavedOps(rules, categories),
        )
    }

    @Test
    fun assign_with_empty_list_does_not_touch_either_gateway() = runBlocking {
        val clock = Clock()
        val rules = RecordingRulesRepositoryFake(clock)
        val categories = RecordingCategoriesRepositoryFake(clock)
        val useCase = BulkAssignRulesToCategoryUseCase(
            AssignRuleToCategoryUseCase(rules, categories),
        )

        useCase.assign(ruleIds = emptyList(), categoryId = "cat-priority")

        assertTrue(categories.appendOps.isEmpty())
        assertTrue(rules.markAssignedOps.isEmpty())
    }

    @Test
    fun assign_propagates_mid_loop_failure_and_skips_remaining_flips() = runBlocking {
        val clock = Clock()
        val rules = RecordingRulesRepositoryFake(clock)
        val categories = RecordingCategoriesRepositoryFake(
            clock,
            throwOnAppendFor = setOf("keyword:r2"),
        )
        val useCase = BulkAssignRulesToCategoryUseCase(
            AssignRuleToCategoryUseCase(rules, categories),
        )

        try {
            useCase.assign(
                ruleIds = listOf("keyword:r1", "keyword:r2", "keyword:r3"),
                categoryId = "cat-priority",
            )
            fail("expected exception to propagate")
        } catch (expected: IllegalStateException) {
            // intentional — bubble up so the caller can show retry UI.
        }

        assertEquals(
            listOf(
                "append:cat-priority+keyword:r1",
                "mark-assigned:keyword:r1",
                "append:cat-priority+keyword:r2",
            ),
            interleavedOps(rules, categories),
        )
        // Third rule never reached.
        assertTrue(rules.markAssignedOps.none { it.second == "keyword:r3" })
        assertTrue(categories.appendOps.none { it.second.endsWith("keyword:r3") })
    }

    private fun interleavedOps(
        rules: RecordingRulesRepositoryFake,
        categories: RecordingCategoriesRepositoryFake,
    ): List<String> {
        val ops = mutableListOf<Pair<Int, String>>()
        categories.appendOps.forEach { (tick, label) -> ops += tick to "append:$label" }
        rules.markAssignedOps.forEach { (tick, label) -> ops += tick to "mark-assigned:$label" }
        return ops.sortedBy { it.first }.map { it.second }
    }

    private class Clock {
        private var tick = 0
        fun next(): Int = ++tick
    }

    private class RecordingRulesRepositoryFake(
        private val clock: Clock,
    ) : AssignRuleToCategoryUseCase.RulesGateway {
        val markAssignedOps = mutableListOf<Pair<Int, String>>()

        override suspend fun markRuleAsAssigned(ruleId: String) {
            markAssignedOps += clock.next() to ruleId
        }
    }

    private class RecordingCategoriesRepositoryFake(
        private val clock: Clock,
        private val throwOnAppendFor: Set<String> = emptySet(),
    ) : AssignRuleToCategoryUseCase.CategoriesGateway {
        val appendOps = mutableListOf<Pair<Int, String>>()

        override suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String) {
            appendOps += clock.next() to "$categoryId+$ruleId"
            if (ruleId in throwOnAppendFor) error("simulated DataStore failure for $ruleId")
        }
    }
}
