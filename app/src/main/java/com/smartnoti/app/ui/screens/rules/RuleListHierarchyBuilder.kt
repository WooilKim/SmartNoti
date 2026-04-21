package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Converts the flat rules list into the Rules-tab tree: each base rule keeps
 * its override children nested one level deep (plan
 * `rules-ux-v2-inbox-restructure` Phase C Task 3 & Open question #3 — 1-level
 * chains only for now). Broken overrides (base missing, base itself an
 * override, self-reference) surface at the top level with [brokenReason] set
 * so the UI can render a visual warning without dropping the rule.
 */
class RuleListHierarchyBuilder {

    /**
     * @param visibleRules rules after the tab's filter chips are applied — what
     *   the user is scoped to see. Only these become tree nodes.
     * @param allRules the unfiltered set used to validate override pointers.
     *   We separate the two so a filter that hides the base rule does not
     *   falsely mark the still-visible override as broken.
     */
    fun build(
        visibleRules: List<RuleUiModel>,
        allRules: List<RuleUiModel>,
    ): List<RuleListNode> {
        val allIndex = allRules.associateBy { it.id }
        val visibleIds = visibleRules.mapTo(mutableSetOf()) { it.id }

        val result = mutableListOf<RuleListNode>()
        val childBuckets = mutableMapOf<String, MutableList<RuleListNode>>()

        // First pass: classify every visible rule. Well-formed override
        // children get staged into childBuckets so we can attach them to the
        // base in the second pass while preserving the visible-list order.
        for (rule in visibleRules) {
            val overrideOf = rule.overrideOf
            if (overrideOf == null) {
                result.add(RuleListNode(rule = rule, overrideState = RuleOverrideState.Base))
                continue
            }

            val broken = classifyOverride(
                overrideOf = overrideOf,
                ruleId = rule.id,
                allIndex = allIndex,
            )
            if (broken != null) {
                // Surface orphans/cycles at the top level with a warning; the
                // UI renders the warning chip and does not indent the row.
                result.add(
                    RuleListNode(
                        rule = rule,
                        overrideState = RuleOverrideState.Override(baseRuleId = overrideOf),
                        brokenReason = broken,
                    )
                )
                continue
            }

            val childNode = RuleListNode(
                rule = rule,
                overrideState = RuleOverrideState.Override(baseRuleId = overrideOf),
            )
            if (overrideOf in visibleIds) {
                childBuckets.getOrPut(overrideOf) { mutableListOf() }.add(childNode)
            } else {
                // Base exists but is currently filtered out — still render the
                // override at top level but keep the "child" framing (indent +
                // label) so the user knows it's an exception.
                result.add(childNode)
            }
        }

        if (childBuckets.isEmpty()) {
            return result
        }

        // Second pass: attach children to their visible base nodes.
        return result.map { node ->
            val children = childBuckets[node.rule.id].orEmpty()
            if (children.isEmpty()) node else node.copy(children = children)
        }
    }

    private fun classifyOverride(
        overrideOf: String,
        ruleId: String,
        allIndex: Map<String, RuleUiModel>,
    ): RuleOverrideBrokenReason? {
        if (overrideOf == ruleId) return RuleOverrideBrokenReason.SelfReference
        val base = allIndex[overrideOf] ?: return RuleOverrideBrokenReason.BaseMissing(baseRuleId = overrideOf)
        if (base.overrideOf != null) {
            // 1-level chain limit: B → A is OK, but C → B (where B is itself
            // an override) is not supported yet. Flag it so the user can fix.
            return RuleOverrideBrokenReason.BaseIsOverride(baseRuleId = overrideOf)
        }
        return null
    }
}

/**
 * A single tree node. Base rules have `overrideState == Base` and may carry
 * children; override rules have `overrideState == Override` and render
 * indented under their base (or at top level with [brokenReason] set if the
 * base is unreachable).
 */
data class RuleListNode(
    val rule: RuleUiModel,
    val overrideState: RuleOverrideState,
    val children: List<RuleListNode> = emptyList(),
    val brokenReason: RuleOverrideBrokenReason? = null,
)

sealed interface RuleOverrideState {
    data object Base : RuleOverrideState
    data class Override(val baseRuleId: String) : RuleOverrideState
}

sealed interface RuleOverrideBrokenReason {
    data object SelfReference : RuleOverrideBrokenReason
    data class BaseMissing(val baseRuleId: String) : RuleOverrideBrokenReason
    data class BaseIsOverride(val baseRuleId: String) : RuleOverrideBrokenReason
}
