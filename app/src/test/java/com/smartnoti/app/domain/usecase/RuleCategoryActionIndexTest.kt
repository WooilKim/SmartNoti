package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleActionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md` Task 1.
 *
 * The "미분류" Rule state is **derived**: a Rule that no Category claims via
 * `ruleIds` is treated as unassigned by every UI surface (filter / grouping /
 * row chip) and as no-op by the classifier. Pin that the index returns null
 * for those rule ids so the contract doesn't silently regress.
 */
class RuleCategoryActionIndexTest {

    @Test
    fun lookup_returns_null_for_rule_id_owned_by_no_category() {
        val index = RuleCategoryActionIndex(
            categories = listOf(
                Category(
                    id = "cat-priority",
                    name = "중요",
                    appPackageName = null,
                    ruleIds = listOf("keyword:결제"),
                    action = CategoryAction.PRIORITY,
                    order = 0,
                ),
            ),
        )

        assertNull(index.actionFor("keyword:광고"))
    }

    @Test
    fun lookup_returns_action_for_rule_owned_by_a_category() {
        val index = RuleCategoryActionIndex(
            categories = listOf(
                Category(
                    id = "cat-digest",
                    name = "Digest",
                    appPackageName = null,
                    ruleIds = listOf("keyword:광고"),
                    action = CategoryAction.DIGEST,
                    order = 0,
                ),
            ),
        )

        assertEquals(RuleActionUi.DIGEST, index.actionFor("keyword:광고"))
    }

    @Test
    fun lookup_returns_lowest_order_category_when_rule_is_claimed_by_multiple() {
        val index = RuleCategoryActionIndex(
            categories = listOf(
                Category(
                    id = "cat-silent",
                    name = "조용히",
                    appPackageName = null,
                    ruleIds = listOf("keyword:공지"),
                    action = CategoryAction.SILENT,
                    order = 1,
                ),
                Category(
                    id = "cat-priority",
                    name = "중요",
                    appPackageName = null,
                    ruleIds = listOf("keyword:공지"),
                    action = CategoryAction.PRIORITY,
                    order = 0,
                ),
            ),
        )

        assertEquals(RuleActionUi.ALWAYS_PRIORITY, index.actionFor("keyword:공지"))
    }

    @Test
    fun empty_categories_yield_null_for_every_lookup() {
        val index = RuleCategoryActionIndex(categories = emptyList())

        assertNull(index.actionFor("keyword:인증번호"))
        assertNull(index.actionFor("person:엄마"))
    }
}
