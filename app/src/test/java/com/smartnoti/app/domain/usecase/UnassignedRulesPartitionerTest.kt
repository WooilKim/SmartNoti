package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-26-rule-explicit-draft-flag.md` Task 1.
 *
 * The single "미분류" bucket is splitting into two sub-buckets:
 *
 *  - **작업 필요** (`actionNeeded`) — `draft = true` AND owned by no Category.
 *    The user has not yet expressed an intent for this rule; RulesScreen
 *    surfaces it with the existing accent treatment + "분류에 추가되기 전까지
 *    비활성" banner so it is hard to miss.
 *  - **보류** (`parked`) — `draft = false` AND owned by no Category. The user
 *    has explicitly chosen "분류 없이 보류" or migrated from a legacy row that
 *    was already silently dormant. Rendered with a quieter tone and a
 *    "사용자가 보류함" banner so it doesn't shout at the user every time the
 *    Rules screen mounts.
 *
 * The partitioner is a pure helper so callers (Compose `remember`, tests) can
 * re-derive the split deterministically. Output preserves input rule order
 * inside each bucket so list rendering stays stable as DataStore reorders.
 */
class UnassignedRulesPartitionerTest {

    private val partitioner = UnassignedRulesPartitioner()

    @Test
    fun action_needed_holds_draft_true_unassigned_rules() {
        val draftRule = rule(id = "keyword:draft", draft = true)
        val rules = listOf(draftRule)

        val partition = partitioner.partition(rules, emptyList())

        assertEquals(listOf(draftRule), partition.actionNeeded)
        assertTrue(partition.parked.isEmpty())
    }

    @Test
    fun parked_holds_draft_false_unassigned_rules() {
        val parkedRule = rule(id = "keyword:parked", draft = false)
        val rules = listOf(parkedRule)

        val partition = partitioner.partition(rules, emptyList())

        assertTrue(partition.actionNeeded.isEmpty())
        assertEquals(listOf(parkedRule), partition.parked)
    }

    @Test
    fun claimed_rules_appear_in_neither_bucket() {
        val draftRule = rule(id = "keyword:draft", draft = true)
        val parkedRule = rule(id = "keyword:parked", draft = false)
        val claimedDraft = rule(id = "keyword:claimed-draft", draft = true)
        val claimedParked = rule(id = "keyword:claimed-parked", draft = false)
        val rules = listOf(draftRule, claimedDraft, parkedRule, claimedParked)
        val categories = listOf(
            category(
                id = "cat-priority",
                ruleIds = listOf("keyword:claimed-draft", "keyword:claimed-parked"),
            ),
        )

        val partition = partitioner.partition(rules, categories)

        assertEquals(listOf(draftRule), partition.actionNeeded)
        assertEquals(listOf(parkedRule), partition.parked)
    }

    @Test
    fun preserves_input_order_inside_each_bucket() {
        val a = rule(id = "person:a", draft = true)
        val b = rule(id = "person:b", draft = false)
        val c = rule(id = "person:c", draft = true)
        val d = rule(id = "person:d", draft = false)

        val partition = partitioner.partition(listOf(a, b, c, d), emptyList())

        assertEquals(listOf(a, c), partition.actionNeeded)
        assertEquals(listOf(b, d), partition.parked)
    }

    @Test
    fun empty_rules_input_yields_empty_buckets() {
        val partition = partitioner.partition(emptyList(), emptyList())

        assertTrue(partition.actionNeeded.isEmpty())
        assertTrue(partition.parked.isEmpty())
    }

    @Test
    fun empty_categories_treats_every_rule_as_unassigned() {
        val draftRule = rule(id = "keyword:draft", draft = true)
        val parkedRule = rule(id = "keyword:parked", draft = false)

        val partition = partitioner.partition(listOf(draftRule, parkedRule), emptyList())

        assertEquals(listOf(draftRule), partition.actionNeeded)
        assertEquals(listOf(parkedRule), partition.parked)
    }

    private fun rule(id: String, draft: Boolean) = RuleUiModel(
        id = id,
        title = id,
        subtitle = "",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = id.substringAfter(':'),
        draft = draft,
    )

    private fun category(id: String, ruleIds: List<String>) = Category(
        id = id,
        name = id,
        appPackageName = null,
        ruleIds = ruleIds,
        action = CategoryAction.PRIORITY,
        order = 0,
    )
}
