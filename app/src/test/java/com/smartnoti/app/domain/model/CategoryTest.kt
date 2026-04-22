package com.smartnoti.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 1.
 *
 * These tests intentionally fail to compile until Task 2 introduces the
 * `Category` domain model and the `CategoryAction` enum. They pin the shape
 * of the new contract so Task 2's implementation is forced to match:
 *
 *  - `Category` carries `id`, `name`, optional `appPackageName`, a
 *    `ruleIds: List<String>` membership list, a `CategoryAction`, and an
 *    `order: Int` drag-reorder index.
 *  - `CategoryAction` is exactly `PRIORITY | DIGEST | SILENT | IGNORE` —
 *    four values, no more, no fewer. IGNORE is first-class so the existing
 *    IGNORE archive toggle can re-bind onto `Category.action == IGNORE`.
 *  - The Rule-without-action contract: once Task 4 lands, the Rule domain
 *    model is a pure condition matcher with `id`, `type`, `matchValue`, and
 *    `overrideOf?`. No `action` field. The last test in this file pins that
 *    shape via the new `Rule` type alias the plan calls for — it will fail
 *    to compile today (because the symbol `Rule` does not exist yet and
 *    `RuleUiModel.action` still does) and will only go green after Task 4.
 */
class CategoryTest {

    @Test
    fun category_exposes_required_fields() {
        val category = Category(
            id = "cat-1",
            name = "분류",
            appPackageName = null,
            ruleIds = listOf("rule-a"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )

        assertEquals("cat-1", category.id)
        assertEquals("분류", category.name)
        assertNull(category.appPackageName)
        assertEquals(listOf("rule-a"), category.ruleIds)
        assertEquals(CategoryAction.PRIORITY, category.action)
        assertEquals(0, category.order)
    }

    @Test
    fun category_accepts_optional_app_package_name() {
        val category = Category(
            id = "cat-kakao",
            name = "카카오톡",
            appPackageName = "com.kakao.talk",
            ruleIds = listOf("rule-kakao"),
            action = CategoryAction.DIGEST,
            order = 1,
        )

        assertEquals("com.kakao.talk", category.appPackageName)
    }

    @Test
    fun category_action_enum_has_exactly_four_values() {
        // Plan contract: PRIORITY / DIGEST / SILENT / IGNORE. If someone adds
        // a fifth value later, the classifier hot path must handle it — this
        // test is the canary.
        val values = CategoryAction.values().toSet()

        assertEquals(4, values.size)
        assertTrue(CategoryAction.PRIORITY in values)
        assertTrue(CategoryAction.DIGEST in values)
        assertTrue(CategoryAction.SILENT in values)
        assertTrue(CategoryAction.IGNORE in values)
    }

    @Test
    fun category_supports_multiple_rule_ids() {
        // A Category wraps a set of Rule conditions. Same rule id may legally
        // appear in multiple categories (plan Phase P1 Task 1 step 2) so this
        // model test only asserts the list contract, not uniqueness.
        val category = Category(
            id = "cat-multi",
            name = "알림 묶음",
            appPackageName = null,
            ruleIds = listOf("rule-a", "rule-b", "rule-c"),
            action = CategoryAction.SILENT,
            order = 2,
        )

        assertEquals(3, category.ruleIds.size)
        assertEquals(listOf("rule-a", "rule-b", "rule-c"), category.ruleIds)
    }

    @Test
    fun rule_domain_model_has_no_action_field() {
        // RED until Task 4 lands: the plan removes `action` from the Rule
        // domain model so Rule is a pure condition matcher. This test pins
        // that by constructing a Rule with only the four allowed fields —
        // today the symbol `Rule` does not exist (the codebase still uses
        // `RuleUiModel` with an `action` parameter), so this test fails to
        // compile. When Task 4 introduces `Rule(id, type, matchValue,
        // overrideOf?)`, the test passes without further changes.
        val rule = Rule(
            id = "rule-person-mom",
            type = RuleTypeUi.PERSON,
            matchValue = "엄마",
            overrideOf = null,
        )

        assertEquals("rule-person-mom", rule.id)
        assertEquals(RuleTypeUi.PERSON, rule.type)
        assertEquals("엄마", rule.matchValue)
        assertNull(rule.overrideOf)
    }
}
