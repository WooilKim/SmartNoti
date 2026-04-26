package com.smartnoti.app.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-26-rule-explicit-draft-flag.md` Task 3.
 *
 * Pin the order/atomicity contract of the new
 * `AssignRuleToCategoryUseCase` — the only entry point that must be used
 * for routing a rule into a Category so the `draft = true → false` flip
 * can never be skipped.
 *
 *  1. The Category-side append runs FIRST. If that throws, the rule's
 *     `draft` flag stays untouched so the user can retry the assignment
 *     from the same "작업 필요" sub-bucket.
 *  2. The rule-side `markRuleAsAssigned` runs SECOND, only when the
 *     Category append succeeds.
 *  3. Re-routing an already-assigned rule (one that another Category
 *     already claims) still walks the same two-step path so the flip
 *     is idempotent.
 */
class AssignRuleToCategoryUseCaseTest {

    @Test
    fun assign_calls_category_append_then_marks_rule_assigned() = runBlocking {
        val clock = Clock()
        val rules = RecordingRulesRepositoryFake(clock)
        val categories = RecordingCategoriesRepositoryFake(clock)
        val useCase = AssignRuleToCategoryUseCase(rules, categories)

        useCase.assign(ruleId = "keyword:인증번호", categoryId = "cat-priority")

        assertEquals(
            listOf("append:cat-priority+keyword:인증번호", "mark-assigned:keyword:인증번호"),
            interleavedOps(rules, categories),
        )
    }

    @Test
    fun assign_skips_mark_when_category_append_throws() = runBlocking {
        val clock = Clock()
        val rules = RecordingRulesRepositoryFake(clock)
        val categories = RecordingCategoriesRepositoryFake(clock, throwOnAppend = true)
        val useCase = AssignRuleToCategoryUseCase(rules, categories)

        try {
            useCase.assign(ruleId = "keyword:인증번호", categoryId = "cat-priority")
            fail("expected exception to propagate")
        } catch (expected: IllegalStateException) {
            // intentional — bubble up so the caller can show retry UI.
        }

        assertEquals(
            listOf("append:cat-priority+keyword:인증번호"),
            interleavedOps(rules, categories),
        )
        assertTrue(rules.markedAssignedIds.isEmpty())
    }

    @Test
    fun assign_to_a_second_category_still_runs_both_ops_in_order() = runBlocking {
        val clock = Clock()
        val rules = RecordingRulesRepositoryFake(clock)
        val categories = RecordingCategoriesRepositoryFake(clock)
        val useCase = AssignRuleToCategoryUseCase(rules, categories)

        useCase.assign(ruleId = "keyword:공지", categoryId = "cat-priority")
        useCase.assign(ruleId = "keyword:공지", categoryId = "cat-digest")

        assertEquals(
            listOf(
                "append:cat-priority+keyword:공지",
                "mark-assigned:keyword:공지",
                "append:cat-digest+keyword:공지",
                "mark-assigned:keyword:공지",
            ),
            interleavedOps(rules, categories),
        )
    }

    private fun interleavedOps(
        rules: RecordingRulesRepositoryFake,
        categories: RecordingCategoriesRepositoryFake,
    ): List<String> {
        // Both fakes share a monotonic counter via `clock` so the order
        // across them is observable from the test.
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
        val markedAssignedIds: List<String> get() = markAssignedOps.map { it.second }

        override suspend fun markRuleAsAssigned(ruleId: String) {
            markAssignedOps += clock.next() to ruleId
        }
    }

    private class RecordingCategoriesRepositoryFake(
        private val clock: Clock,
        private val throwOnAppend: Boolean = false,
    ) : AssignRuleToCategoryUseCase.CategoriesGateway {
        val appendOps = mutableListOf<Pair<Int, String>>()

        override suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String) {
            appendOps += clock.next() to "$categoryId+$ruleId"
            if (throwOnAppend) error("simulated DataStore failure")
        }
    }
}
