package com.smartnoti.app.data.rules

import com.smartnoti.app.data.categories.applyRuleDeletedCascade
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-phase tests for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 1 step 5
 * (Drift #3 — rule delete cascade).
 *
 * Contract: when `RulesRepository.deleteRule(ruleId)` persists a rule
 * removal, the cascade hook `CategoriesRepository.onRuleDeleted(ruleId)`
 * rewrites every affected Category with the rule id stripped. Tested
 * here via the pure helper `applyRuleDeletedCascade(categories, ruleId)`
 * that the repository method delegates to.
 *
 * Invariants:
 *   - every Category listing `ruleId` in `ruleIds` has it removed
 *   - Categories that did not reference the id are untouched
 *   - a Category whose `ruleIds` drops to empty is **preserved** (user can
 *     open the editor and re-add rules — losing the Category wordlessly
 *     would surprise them)
 *   - idempotent: running the cascade a second time is a no-op
 *   - other fields (`name`, `action`, `order`, `appPackageName`) are
 *     untouched
 *
 * Symbol `applyRuleDeletedCascade` lives under
 * `com.smartnoti.app.data.categories` and does not exist yet. Task 5
 * turns this green.
 */
class RulesRepositoryDeleteCascadeTest {

    @Test
    fun cascade_strips_rule_id_from_every_category_that_references_it() {
        val categories = listOf(
            Category(
                id = "cat-a",
                name = "A",
                appPackageName = null,
                ruleIds = listOf("r1"),
                action = CategoryAction.PRIORITY,
                order = 0,
            ),
            Category(
                id = "cat-b",
                name = "B",
                appPackageName = null,
                ruleIds = listOf("r1", "r2"),
                action = CategoryAction.DIGEST,
                order = 1,
            ),
        )

        val cascaded = applyRuleDeletedCascade(categories, ruleId = "r1")

        assertEquals(2, cascaded.size)
        val catA = cascaded.first { it.id == "cat-a" }
        val catB = cascaded.first { it.id == "cat-b" }
        assertTrue(catA.ruleIds.isEmpty())
        assertEquals(listOf("r2"), catB.ruleIds)
    }

    @Test
    fun cascade_preserves_category_even_when_rule_ids_become_empty() {
        // Plan Task 5 step 1: an empty ruleIds Category is preserved so the
        // user sees a "rule-less" Category they can edit, not silently
        // orphaned.
        val categories = listOf(
            Category(
                id = "cat-lone",
                name = "외톨이",
                appPackageName = null,
                ruleIds = listOf("r1"),
                action = CategoryAction.PRIORITY,
                order = 0,
            ),
        )

        val cascaded = applyRuleDeletedCascade(categories, ruleId = "r1")

        assertEquals(1, cascaded.size)
        val only = cascaded.single()
        assertTrue(only.ruleIds.isEmpty())
        assertEquals("외톨이", only.name)
        assertEquals(CategoryAction.PRIORITY, only.action)
        assertEquals(0, only.order)
    }

    @Test
    fun cascade_leaves_categories_untouched_when_id_is_not_referenced() {
        val categories = listOf(
            Category(
                id = "cat-a",
                name = "A",
                appPackageName = null,
                ruleIds = listOf("r2", "r3"),
                action = CategoryAction.DIGEST,
                order = 0,
            ),
        )

        val cascaded = applyRuleDeletedCascade(categories, ruleId = "r1")

        assertEquals(categories, cascaded)
    }

    @Test
    fun cascade_is_idempotent_when_run_twice() {
        val categories = listOf(
            Category(
                id = "cat-a",
                name = "A",
                appPackageName = null,
                ruleIds = listOf("r1", "r2"),
                action = CategoryAction.PRIORITY,
                order = 0,
            ),
        )

        val once = applyRuleDeletedCascade(categories, ruleId = "r1")
        val twice = applyRuleDeletedCascade(once, ruleId = "r1")

        assertEquals(once, twice)
        assertEquals(listOf("r2"), twice.single().ruleIds)
    }

    @Test
    fun cascade_preserves_order_app_pin_and_action_fields() {
        val categories = listOf(
            Category(
                id = "cat-a",
                name = "A",
                appPackageName = "com.foo",
                ruleIds = listOf("r1"),
                action = CategoryAction.IGNORE,
                order = 7,
            ),
        )

        val cascaded = applyRuleDeletedCascade(categories, ruleId = "r1")

        val catA = cascaded.single()
        assertEquals("com.foo", catA.appPackageName)
        assertEquals(CategoryAction.IGNORE, catA.action)
        assertEquals(7, catA.order)
    }
}
