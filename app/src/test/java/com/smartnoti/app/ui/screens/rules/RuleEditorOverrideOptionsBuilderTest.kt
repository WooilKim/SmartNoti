package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Phase C Task 4 override-editor dropdown options builder.
 *
 * The builder feeds the `"어느 규칙의 예외인가요?"` dropdown in [RulesScreen]. It
 * must hide rules that the user cannot legally override (self, another override,
 * rules the user is editing) so the dropdown never surfaces a broken choice.
 */
class RuleEditorOverrideOptionsBuilderTest {

    private val builder = RuleEditorOverrideOptionsBuilder()

    private fun rule(
        id: String,
        title: String = id,
        type: RuleTypeUi = RuleTypeUi.KEYWORD,
        action: RuleActionUi = RuleActionUi.ALWAYS_PRIORITY,
        matchValue: String = "v-$id",
        overrideOf: String? = null,
    ) = RuleUiModel(
        id = id,
        title = title,
        subtitle = "",
        type = type,
        action = action,
        enabled = true,
        matchValue = matchValue,
        overrideOf = overrideOf,
    )

    @Test
    fun excludes_overrides_from_eligible_base_list() {
        // Plan Phase C Open question #3: 1-level override chains only. So a
        // rule whose `overrideOf != null` cannot itself be chosen as a base.
        val base = rule("base-1")
        val override = rule("override-1", overrideOf = "base-1")

        val options = builder.build(allRules = listOf(base, override), editingRuleId = null)

        assertEquals(listOf("base-1"), options.map { it.id })
    }

    @Test
    fun excludes_rule_currently_being_edited() {
        // When editing a rule, the dropdown must not let the user pick that
        // same rule as its own base (would become a self-reference, rejected
        // by the storage validator anyway but we hide it earlier for UX).
        val first = rule("first")
        val second = rule("second")

        val options = builder.build(allRules = listOf(first, second), editingRuleId = "first")

        assertEquals(listOf("second"), options.map { it.id })
    }

    @Test
    fun returns_empty_when_no_base_rules_exist() {
        val onlyOverride = rule("override-only", overrideOf = "missing-base")

        val options = builder.build(allRules = listOf(onlyOverride), editingRuleId = null)

        assertTrue(options.isEmpty())
    }

    @Test
    fun preserves_list_order_of_input_rules() {
        // Order matters — the dropdown surfaces rules in priority order so the
        // user picks the right one visually.
        val a = rule("a", title = "알파")
        val b = rule("b", title = "베타")
        val c = rule("c", title = "감마")

        val options = builder.build(allRules = listOf(a, b, c), editingRuleId = null)

        assertEquals(listOf("a", "b", "c"), options.map { it.id })
    }
}
