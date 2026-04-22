package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleOrderingTest {

    @Test
    fun move_up_swaps_rule_with_previous_position() {
        val reordered = moveRule(
            rules = sampleRules(),
            ruleId = "r2",
            direction = RuleMoveDirection.UP,
        )

        assertEquals(listOf("r2", "r1", "r3"), reordered.map { it.id })
    }

    @Test
    fun move_down_at_bottom_keeps_order_unchanged() {
        val original = sampleRules()

        val reordered = moveRule(
            rules = original,
            ruleId = "r3",
            direction = RuleMoveDirection.DOWN,
        )

        assertEquals(original.map { it.id }, reordered.map { it.id })
    }

    // Plan rules-ux-v2-inbox-restructure Phase C Task 5: moves must respect tier
    // — a base rule can only swap with the nearest base-rule neighbor, and an
    // override can only swap with a sibling sharing the same `overrideOf`.
    // Cross-tier swaps are no-ops so drag reordering never corrupts the
    // hierarchy.

    @Test
    fun move_up_skips_over_override_siblings_to_reach_previous_base() {
        // Layout: [base r1] [override r2→r1] [base r3]
        // Moving r3 up should land before r1 because r2 is an override of r1,
        // not a base-tier peer.
        val rules = listOf(
            baseRule("r1"),
            overrideRule("r2", of = "r1"),
            baseRule("r3"),
        )

        val reordered = moveRule(rules, ruleId = "r3", direction = RuleMoveDirection.UP)

        assertEquals(listOf("r3", "r1", "r2"), reordered.map { it.id })
    }

    @Test
    fun move_down_of_base_swaps_with_next_base_ignoring_overrides_in_between() {
        // Layout: [base r1] [override r2→r1] [base r3] [override r4→r3]
        // Moving r1 down should land just before the next base (r3) — the
        // intervening override (r2) isn't a same-tier neighbor, so it stays
        // where it was at the raw list level. The UI's hierarchy builder keeps
        // r2 grouped with r1 visually regardless of raw order.
        val rules = listOf(
            baseRule("r1"),
            overrideRule("r2", of = "r1"),
            baseRule("r3"),
            overrideRule("r4", of = "r3"),
        )

        val reordered = moveRule(rules, ruleId = "r1", direction = RuleMoveDirection.DOWN)

        val indexOfR1 = reordered.indexOfFirst { it.id == "r1" }
        val indexOfR3 = reordered.indexOfFirst { it.id == "r3" }
        // r1 lands after r3 at the base tier.
        assert(indexOfR1 > indexOfR3) {
            "expected r1 after r3 at base tier; got ${reordered.map { it.id }}"
        }
        // Every rule still present exactly once.
        assertEquals(rules.map { it.id }.toSet(), reordered.map { it.id }.toSet())
        assertEquals(rules.size, reordered.size)
    }

    @Test
    fun override_moves_only_among_siblings_sharing_same_base() {
        // Layout: [base r1] [override r2→r1] [override r3→r1] [base r4]
        //                                     [override r5→r4]
        // Moving r3 up should swap with r2 (same base). Moving r3 down should
        // be a no-op because the next row (r4) is not a sibling.
        val rules = listOf(
            baseRule("r1"),
            overrideRule("r2", of = "r1"),
            overrideRule("r3", of = "r1"),
            baseRule("r4"),
            overrideRule("r5", of = "r4"),
        )

        val movedUp = moveRule(rules, ruleId = "r3", direction = RuleMoveDirection.UP)
        assertEquals(listOf("r1", "r3", "r2", "r4", "r5"), movedUp.map { it.id })

        val movedDown = moveRule(rules, ruleId = "r3", direction = RuleMoveDirection.DOWN)
        // r3 has no sibling below it — r4 is a different tier, r5 belongs to r4.
        assertEquals(rules.map { it.id }, movedDown.map { it.id })
    }

    @Test
    fun override_cannot_move_across_base_groups() {
        // An override of r1 can never move into r4's override group.
        val rules = listOf(
            baseRule("r1"),
            overrideRule("r2", of = "r1"),
            baseRule("r4"),
            overrideRule("r5", of = "r4"),
        )

        // r2 trying to move down past r4 is a no-op at its own tier.
        val movedDown = moveRule(rules, ruleId = "r2", direction = RuleMoveDirection.DOWN)
        assertEquals(rules.map { it.id }, movedDown.map { it.id })
    }

    @Test
    fun base_move_is_noop_when_already_topmost_base() {
        // r2 is the top base even though r1 sits above it as an override of
        // another (non-existent) rule — but in practice bases always precede
        // their own overrides. Simpler: first base moving up = no-op.
        val rules = listOf(
            baseRule("r1"),
            overrideRule("r2", of = "r1"),
            baseRule("r3"),
        )

        val reordered = moveRule(rules, ruleId = "r1", direction = RuleMoveDirection.UP)
        assertEquals(rules.map { it.id }, reordered.map { it.id })
    }

    @Test
    fun missing_rule_id_returns_original_list() {
        val rules = sampleRules()
        val reordered = moveRule(rules, ruleId = "does-not-exist", direction = RuleMoveDirection.UP)
        assertEquals(rules.map { it.id }, reordered.map { it.id })
    }

    private fun sampleRules(): List<RuleUiModel> = listOf(
        RuleUiModel("r1", "엄마", "항상 바로 보기", RuleTypeUi.PERSON, true, "엄마"),
        RuleUiModel("r2", "쿠팡", "Digest로 묶기", RuleTypeUi.APP, true, "com.coupang.mobile"),
        RuleUiModel("r3", "인증번호", "즉시 전달", RuleTypeUi.KEYWORD, true, "인증번호"),
    )

    private fun baseRule(id: String): RuleUiModel = RuleUiModel(
        id = id,
        title = id,
        subtitle = "base",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = id,
        overrideOf = null,
    )

    private fun overrideRule(id: String, of: String): RuleUiModel = RuleUiModel(
        id = id,
        title = id,
        subtitle = "override of $of",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "$id-match",
        overrideOf = of,
    )
}
