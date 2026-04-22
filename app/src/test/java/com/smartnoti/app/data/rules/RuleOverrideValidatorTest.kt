package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RuleOverrideValidator].
 *
 * Plan `rules-ux-v2-inbox-restructure` Phase C Task 1 requires the repository
 * to detect and reject circular override references when upserting rules (e.g.
 * A → B → A must not be persisted). The validator is extracted as a pure
 * function so the policy is testable without a DataStore / Android Context.
 */
class RuleOverrideValidatorTest {

    private val validator = RuleOverrideValidator()

    @Test
    fun rule_with_no_override_is_always_accepted() {
        val existing = listOf(ruleOf("r1"))
        val incoming = ruleOf("r2")

        val result = validator.validate(incoming, existing)

        assertTrue(result is RuleOverrideValidator.Result.Accepted)
    }

    @Test
    fun override_pointing_at_existing_base_is_accepted() {
        val base = ruleOf("base")
        val override = ruleOf(id = "override", overrideOf = "base")

        val result = validator.validate(override, listOf(base))

        assertTrue(result is RuleOverrideValidator.Result.Accepted)
    }

    @Test
    fun override_referencing_self_is_rejected() {
        // A → A is the simplest cycle.
        val self = ruleOf(id = "self", overrideOf = "self")

        val result = validator.validate(self, emptyList())

        assertTrue(result is RuleOverrideValidator.Result.Rejected)
        assertEquals(
            RuleOverrideValidator.Reason.SELF_REFERENCE,
            (result as RuleOverrideValidator.Result.Rejected).reason,
        )
    }

    @Test
    fun two_node_cycle_A_to_B_to_A_is_rejected() {
        // Existing repository already has B → A. Trying to upsert A → B would
        // create the cycle A → B → A.
        val b = ruleOf(id = "B", overrideOf = "A")
        val incomingA = ruleOf(id = "A", overrideOf = "B")

        val result = validator.validate(incomingA, listOf(b))

        assertTrue(result is RuleOverrideValidator.Result.Rejected)
        assertEquals(
            RuleOverrideValidator.Reason.CYCLE_DETECTED,
            (result as RuleOverrideValidator.Result.Rejected).reason,
        )
    }

    @Test
    fun long_chain_cycle_A_to_B_to_C_to_A_is_rejected() {
        val c = ruleOf(id = "C", overrideOf = "B")
        val b = ruleOf(id = "B", overrideOf = "A")
        val incomingA = ruleOf(id = "A", overrideOf = "C")

        val result = validator.validate(incomingA, listOf(b, c))

        assertTrue(result is RuleOverrideValidator.Result.Rejected)
    }

    @Test
    fun override_pointing_at_nonexistent_id_is_accepted_but_orphaned() {
        // The base might still be typed in the editor; the repository shouldn't
        // hard-reject just because the base hasn't been persisted yet. Classifier
        // treats it as a plain (base-less) override.
        val orphaned = ruleOf(id = "o1", overrideOf = "not-yet-saved")

        val result = validator.validate(orphaned, emptyList())

        assertTrue(result is RuleOverrideValidator.Result.Accepted)
    }

    @Test
    fun upserting_an_existing_rule_replaces_it_before_cycle_check() {
        // Simulates the repository updating an existing rule: the existing copy
        // with the same id should be excluded from the cycle graph so that a
        // rule can legally change its `overrideOf` target.
        val previousA = ruleOf(id = "A", overrideOf = "B")
        val b = ruleOf(id = "B") // B used to be a base; no cycle when A's target flips.
        val updatedA = ruleOf(id = "A", overrideOf = null)

        val result = validator.validate(updatedA, listOf(previousA, b))

        assertTrue(result is RuleOverrideValidator.Result.Accepted)
    }

    @Test
    fun accepted_result_exposes_the_incoming_rule_for_chaining() {
        val incoming = ruleOf("r1")

        val result = validator.validate(incoming, emptyList())

        assertTrue(result is RuleOverrideValidator.Result.Accepted)
        assertSame(incoming, (result as RuleOverrideValidator.Result.Accepted).rule)
    }

    @Test
    fun rejected_result_suppresses_persistence() {
        val self = ruleOf(id = "self", overrideOf = "self")

        val result = validator.validate(self, emptyList())

        assertFalse(result is RuleOverrideValidator.Result.Accepted)
    }

    private fun ruleOf(id: String, overrideOf: String? = null): RuleUiModel = RuleUiModel(
        id = id,
        title = id,
        subtitle = "",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "k",
        overrideOf = overrideOf,
    )
}
