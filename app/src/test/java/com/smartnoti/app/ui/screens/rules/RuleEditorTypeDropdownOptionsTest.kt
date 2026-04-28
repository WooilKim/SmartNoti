package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleTypeUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 6 — pin the rule-editor type dropdown's option list and the
 * SENDER label / match-value label sync.
 *
 * Two regressions this guards against:
 *  1. The dropdown's option list silently dropping SENDER (which would
 *     reproduce the original tester finding — SENDER not selectable in
 *     the editor, only the Detail-CTA shortcut works).
 *  2. SENDER's `typeLabel` / `matchLabelFor` drifting away from each
 *     other so the dropdown shows "발신자" but the OutlinedTextField label
 *     still reads a stale value (or vice versa).
 *
 * Pure unit test — no Compose, no Robolectric. The dropdown call site in
 * `RulesScreen` references `RuleEditorTypeDropdownOptions` so this test
 * pins the exact list users see.
 */
class RuleEditorTypeDropdownOptionsTest {

    @Test
    fun dropdown_options_list_contains_all_six_rule_types_in_stable_order() {
        // Order matters — legacy users built muscle memory around the first
        // five entries. SENDER appended at the tail keeps that intact.
        assertEquals(
            listOf(
                RuleTypeUi.PERSON,
                RuleTypeUi.APP,
                RuleTypeUi.KEYWORD,
                RuleTypeUi.SCHEDULE,
                RuleTypeUi.REPEAT_BUNDLE,
                RuleTypeUi.SENDER,
            ),
            RuleEditorTypeDropdownOptions,
        )
    }

    @Test
    fun dropdown_options_covers_every_RuleTypeUi_value() {
        // Defense against future enum additions — if a new type lands but
        // the dropdown isn't updated, this test fails so the implementer
        // knows the editor surface needs explicit consideration.
        val expected = RuleTypeUi.entries.toSet()
        val actual = RuleEditorTypeDropdownOptions.toSet()
        assertEquals(
            "RuleEditorTypeDropdownOptions must enumerate every RuleTypeUi value",
            expected,
            actual,
        )
    }

    @Test
    fun sender_option_renders_korean_label() {
        // Ensures the dropdown row text reads "발신자" — the user-visible
        // affordance the tester missed pre-PR.
        assertEquals("발신자", typeLabel(RuleTypeUi.SENDER))
    }

    @Test
    fun sender_match_value_label_reads_sender_name() {
        // Ensures the OutlinedTextField label reads "발신자 이름" when SENDER
        // is selected — the second-half of the dropdown wiring.
        assertEquals("발신자 이름", matchLabelFor(RuleTypeUi.SENDER))
    }

    @Test
    fun sender_label_pair_is_distinct_from_other_types() {
        // SENDER vs PERSON look the most similar to a casual reader (both
        // address "who sent this"), so guard that they have distinct
        // dropdown labels — picking the wrong row should be impossible
        // by reading alone.
        val senderLabel = typeLabel(RuleTypeUi.SENDER)
        val personLabel = typeLabel(RuleTypeUi.PERSON)
        assertTrue(
            "SENDER and PERSON must have distinct dropdown labels",
            senderLabel != personLabel,
        )
        val senderMatchLabel = matchLabelFor(RuleTypeUi.SENDER)
        val personMatchLabel = matchLabelFor(RuleTypeUi.PERSON)
        assertTrue(
            "SENDER and PERSON must have distinct match-value labels",
            senderMatchLabel != personMatchLabel,
        )
    }
}
