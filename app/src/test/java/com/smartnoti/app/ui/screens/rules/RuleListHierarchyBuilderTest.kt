package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RuleListHierarchyBuilder] — the flat-list → tree converter
 * that drives the Rules tab's hierarchical visualization (plan
 * `rules-ux-v2-inbox-restructure` Phase C Task 3).
 */
class RuleListHierarchyBuilderTest {

    private val builder = RuleListHierarchyBuilder()

    @Test
    fun build_returns_base_rules_as_top_level_nodes_preserving_order() {
        val rules = listOf(
            rule(id = "1"),
            rule(id = "2"),
            rule(id = "3"),
        )

        val nodes = builder.build(rules, allRules = rules)

        assertEquals(listOf("1", "2", "3"), nodes.map { it.rule.id })
        assertTrue(nodes.all { it.overrideState == RuleOverrideState.Base })
        assertTrue(nodes.all { it.children.isEmpty() })
    }

    @Test
    fun build_nests_override_children_under_their_base_rule() {
        val base = rule(id = "payment", action = RuleActionUi.ALWAYS_PRIORITY)
        val override = rule(id = "payment-ad", action = RuleActionUi.SILENT, overrideOf = "payment")
        val unrelated = rule(id = "vip")

        val rules = listOf(base, override, unrelated)
        val nodes = builder.build(rules, allRules = rules)

        assertEquals(listOf("payment", "vip"), nodes.map { it.rule.id })
        val paymentNode = nodes.first { it.rule.id == "payment" }
        assertEquals(1, paymentNode.children.size)
        val child = paymentNode.children.single()
        assertEquals("payment-ad", child.rule.id)
        assertEquals(RuleOverrideState.Override(baseRuleId = "payment"), child.overrideState)
        assertNull(child.brokenReason)
    }

    @Test
    fun build_preserves_override_order_within_a_base() {
        val base = rule(id = "base")
        val first = rule(id = "o1", overrideOf = "base")
        val second = rule(id = "o2", overrideOf = "base")
        val third = rule(id = "o3", overrideOf = "base")

        // Interleaved order in the flat list — resolver should keep file-order.
        val rules = listOf(first, base, third, second)
        val nodes = builder.build(rules, allRules = rules)

        val baseNode = nodes.single { it.rule.id == "base" }
        assertEquals(listOf("o1", "o3", "o2"), baseNode.children.map { it.rule.id })
    }

    @Test
    fun build_surfaces_orphan_override_at_top_level_with_broken_flag() {
        val override = rule(id = "ghost", overrideOf = "deleted-base")
        val other = rule(id = "other")

        val rules = listOf(override, other)
        val nodes = builder.build(rules, allRules = rules)

        assertEquals(listOf("ghost", "other"), nodes.map { it.rule.id })
        val ghost = nodes.first { it.rule.id == "ghost" }
        assertTrue(
            "orphan override should be rendered as a broken override, not a base",
            ghost.overrideState is RuleOverrideState.Override,
        )
        assertEquals(
            RuleOverrideBrokenReason.BaseMissing(baseRuleId = "deleted-base"),
            ghost.brokenReason,
        )
    }

    @Test
    fun build_does_not_recurse_when_override_targets_another_override() {
        // Phase C only supports 1-level override chains (see plan "Open
        // questions" #3). If someone persists B -> A and C -> B, C should be
        // treated as a broken override rather than grandchild nesting.
        val base = rule(id = "a")
        val override = rule(id = "b", overrideOf = "a")
        val nested = rule(id = "c", overrideOf = "b")

        val rules = listOf(base, override, nested)
        val nodes = builder.build(rules, allRules = rules)

        // 'c' is not nested under 'b'; it surfaces at top level as broken.
        assertEquals(listOf("a", "c"), nodes.map { it.rule.id })
        val aNode = nodes.first { it.rule.id == "a" }
        assertEquals(listOf("b"), aNode.children.map { it.rule.id })
        assertTrue(aNode.children.single().children.isEmpty())

        val cNode = nodes.first { it.rule.id == "c" }
        assertEquals(
            RuleOverrideBrokenReason.BaseIsOverride(baseRuleId = "b"),
            cNode.brokenReason,
        )
    }

    @Test
    fun build_only_considers_visible_rules_for_tree_but_resolves_base_against_all_rules() {
        // The Rules tab filter might hide the base rule while the override is
        // still visible (e.g. filter = SILENT, base is PRIORITY). We want the
        // override to keep its "child" framing instead of flipping to broken.
        val base = rule(id = "pay", action = RuleActionUi.ALWAYS_PRIORITY)
        val override = rule(id = "pay-ad", action = RuleActionUi.SILENT, overrideOf = "pay")

        val nodes = builder.build(
            visibleRules = listOf(override),
            allRules = listOf(base, override),
        )

        assertEquals(listOf("pay-ad"), nodes.map { it.rule.id })
        val node = nodes.single()
        assertEquals(RuleOverrideState.Override(baseRuleId = "pay"), node.overrideState)
        assertNull(
            "base exists in allRules; the override should not be flagged broken",
            node.brokenReason,
        )
    }

    @Test
    fun build_hides_override_children_whose_base_is_filtered_out_but_present() {
        // Inverse: base visible, override filtered out. Base should render
        // with no children, not with a dangling orphan.
        val base = rule(id = "pay", action = RuleActionUi.ALWAYS_PRIORITY)
        val override = rule(id = "pay-ad", action = RuleActionUi.SILENT, overrideOf = "pay")

        val nodes = builder.build(
            visibleRules = listOf(base),
            allRules = listOf(base, override),
        )

        val node = nodes.single()
        assertTrue(node.children.isEmpty())
        assertEquals(RuleOverrideState.Base, node.overrideState)
    }

    @Test
    fun build_ignores_self_reference_by_surfacing_as_broken_override() {
        val selfRef = rule(id = "loop", overrideOf = "loop")

        val nodes = builder.build(listOf(selfRef), allRules = listOf(selfRef))

        val node = nodes.single()
        assertEquals(
            RuleOverrideBrokenReason.SelfReference,
            node.brokenReason,
        )
        assertFalse(
            "self-reference must not nest under itself",
            node.children.any { it.rule.id == "loop" },
        )
    }

    private fun rule(
        id: String,
        action: RuleActionUi = RuleActionUi.ALWAYS_PRIORITY,
        overrideOf: String? = null,
    ) = RuleUiModel(
        id = id,
        title = "규칙$id",
        subtitle = "subtitle$id",
        type = RuleTypeUi.KEYWORD,
        action = action,
        enabled = true,
        matchValue = "match$id",
        overrideOf = overrideOf,
    )
}
