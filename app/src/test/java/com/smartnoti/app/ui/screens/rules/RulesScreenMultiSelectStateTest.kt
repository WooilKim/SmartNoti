package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.ui.screens.rules.RulesScreenMultiSelectState.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-26-rules-bulk-assign-unassigned.md` Task 3 — pure-state tests
 * for the multi-select model that drives the bulk-assign flow on
 * [RulesScreen]. Keeps the multi-select decisions outside Compose so the
 * regression cost stays low.
 */
class RulesScreenMultiSelectStateTest {

    @Test
    fun initial_state_is_empty_and_inactive() {
        val state = RulesScreenMultiSelectState()

        assertNull(state.activeBucket)
        assertEquals(emptySet<String>(), state.selectedRuleIds)
        assertFalse(state.isInSelectionMode(Bucket.ACTION_NEEDED))
        assertFalse(state.isInSelectionMode(Bucket.PARKED))
    }

    @Test
    fun enter_selection_seeds_active_bucket_with_single_rule() {
        val state = RulesScreenMultiSelectState()

        val next = state.enterSelection(Bucket.ACTION_NEEDED, "rule-a")

        assertEquals(Bucket.ACTION_NEEDED, next.activeBucket)
        assertEquals(setOf("rule-a"), next.selectedRuleIds)
        assertTrue(next.isInSelectionMode(Bucket.ACTION_NEEDED))
        assertFalse(next.isInSelectionMode(Bucket.PARKED))
    }

    @Test
    fun toggle_within_active_bucket_adds_then_removes_ruleIds() {
        val state = RulesScreenMultiSelectState()
            .enterSelection(Bucket.ACTION_NEEDED, "rule-a")

        val withTwo = state.toggle(Bucket.ACTION_NEEDED, "rule-b")
        assertEquals(setOf("rule-a", "rule-b"), withTwo.selectedRuleIds)
        assertEquals(Bucket.ACTION_NEEDED, withTwo.activeBucket)

        val backToOne = withTwo.toggle(Bucket.ACTION_NEEDED, "rule-b")
        assertEquals(setOf("rule-a"), backToOne.selectedRuleIds)
        assertEquals(Bucket.ACTION_NEEDED, backToOne.activeBucket)
    }

    @Test
    fun toggle_removing_last_rule_auto_cancels_selection_mode() {
        val state = RulesScreenMultiSelectState()
            .enterSelection(Bucket.PARKED, "rule-a")

        val cleared = state.toggle(Bucket.PARKED, "rule-a")

        assertNull(cleared.activeBucket)
        assertEquals(emptySet<String>(), cleared.selectedRuleIds)
    }

    @Test
    fun toggle_in_other_bucket_is_ignored_while_first_bucket_is_active() {
        val state = RulesScreenMultiSelectState()
            .enterSelection(Bucket.ACTION_NEEDED, "rule-a")

        val attempted = state.toggle(Bucket.PARKED, "rule-z")

        assertEquals(Bucket.ACTION_NEEDED, attempted.activeBucket)
        assertEquals(setOf("rule-a"), attempted.selectedRuleIds)
    }

    @Test
    fun toggle_when_no_bucket_is_active_is_ignored() {
        val state = RulesScreenMultiSelectState()

        val attempted = state.toggle(Bucket.ACTION_NEEDED, "rule-a")

        assertNull(attempted.activeBucket)
        assertEquals(emptySet<String>(), attempted.selectedRuleIds)
    }

    @Test
    fun cancel_resets_to_initial_state() {
        val state = RulesScreenMultiSelectState()
            .enterSelection(Bucket.ACTION_NEEDED, "rule-a")
            .toggle(Bucket.ACTION_NEEDED, "rule-b")

        val cleared = state.cancel()

        assertNull(cleared.activeBucket)
        assertEquals(emptySet<String>(), cleared.selectedRuleIds)
    }

    @Test
    fun enter_selection_when_already_in_other_bucket_replaces_active_bucket() {
        // Defensive — Task 5 wires long-press only when activeBucket == null,
        // but the state itself should remain predictable if invoked twice.
        val state = RulesScreenMultiSelectState()
            .enterSelection(Bucket.ACTION_NEEDED, "rule-a")

        val replaced = state.enterSelection(Bucket.PARKED, "rule-z")

        assertEquals(Bucket.PARKED, replaced.activeBucket)
        assertEquals(setOf("rule-z"), replaced.selectedRuleIds)
    }
}
