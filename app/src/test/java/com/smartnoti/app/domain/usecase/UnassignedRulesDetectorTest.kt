package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md` Task 6.
 *
 * After the editor stops auto-creating a 1:1 Category, a freshly saved Rule
 * lives in a "미분류" draft state until the user picks a Category in the
 * post-save sheet. Surfaces (RulesScreen "미분류" chip + CategoriesScreen
 * 안내 카드) need a pure helper that resolves the unassigned set from the
 * live Rules + Categories snapshot without re-deriving the ownership
 * predicate at every call site.
 */
class UnassignedRulesDetectorTest {

    private val detector = UnassignedRulesDetector()

    @Test
    fun returns_empty_when_every_rule_is_owned_by_some_category() {
        val rules = listOf(
            rule(id = "keyword:결제"),
            rule(id = "person:엄마"),
        )
        val categories = listOf(
            category(id = "cat-priority", ruleIds = listOf("keyword:결제", "person:엄마")),
        )

        assertTrue(detector.detect(rules, categories).isEmpty())
    }

    @Test
    fun returns_rule_not_referenced_by_any_category() {
        val unassigned = rule(id = "keyword:광고")
        val rules = listOf(rule(id = "keyword:결제"), unassigned)
        val categories = listOf(
            category(id = "cat-priority", ruleIds = listOf("keyword:결제")),
        )

        assertEquals(listOf(unassigned), detector.detect(rules, categories))
    }

    @Test
    fun every_rule_is_unassigned_when_categories_is_empty() {
        val rules = listOf(rule(id = "keyword:결제"), rule(id = "person:엄마"))

        assertEquals(rules, detector.detect(rules, emptyList()))
    }

    @Test
    fun preserves_input_rule_order_in_output() {
        val a = rule(id = "person:a")
        val b = rule(id = "person:b")
        val c = rule(id = "person:c")
        val categories = listOf(
            category(id = "cat-x", ruleIds = listOf("person:b")),
        )

        assertEquals(listOf(a, c), detector.detect(listOf(a, b, c), categories))
    }

    @Test
    fun rule_claimed_by_at_least_one_category_is_not_unassigned() {
        val shared = rule(id = "keyword:공지")
        val rules = listOf(shared)
        val categories = listOf(
            category(id = "cat-a", ruleIds = listOf("keyword:공지")),
            category(id = "cat-b", ruleIds = listOf("keyword:공지")),
        )

        assertTrue(detector.detect(rules, categories).isEmpty())
    }

    private fun rule(id: String) = RuleUiModel(
        id = id,
        title = id,
        subtitle = "",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = id.substringAfter(':'),
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
