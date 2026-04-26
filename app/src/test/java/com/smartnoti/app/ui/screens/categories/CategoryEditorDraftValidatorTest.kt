package com.smartnoti.app.ui.screens.categories

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for plan `docs/plans/2026-04-22-categories-split-rules-actions.md`
 * Phase P3 Task 9 — Category editor save gate.
 */
class CategoryEditorDraftValidatorTest {

    private val validator = CategoryEditorDraftValidator()

    @Test
    fun blank_name_cannot_save() {
        val canSave = validator.canSave(
            name = "   ",
            selectedRuleIds = listOf("rule-1"),
        )
        assertFalse(canSave)
    }

    @Test
    fun empty_rule_selection_cannot_save_even_with_name() {
        val canSave = validator.canSave(
            name = "중요 연락",
            selectedRuleIds = emptyList(),
        )
        assertFalse(canSave)
    }

    @Test
    fun populated_name_and_at_least_one_rule_can_save() {
        val canSave = validator.canSave(
            name = "중요 연락",
            selectedRuleIds = listOf("rule-mom"),
        )
        assertTrue(canSave)
    }

    @Test
    fun name_with_leading_trailing_whitespace_is_trimmed_when_checking() {
        val canSave = validator.canSave(
            name = "  엄마  ",
            selectedRuleIds = listOf("rule-mom"),
        )
        assertTrue(canSave)
    }

    // Plan `docs/plans/2026-04-25-category-name-uniqueness.md` Task 3
    // — uniqueness branch added via separate [nameAvailable] predicate
    // (option B). Editor combines the two gates with `&&`.

    @Test
    fun nameAvailable_new_flow_with_duplicate_name_is_false() {
        val available = validator.nameAvailable(
            name = "프로모션",
            existing = listOf("cat-existing" to "프로모션"),
            currentCategoryId = null,
        )
        assertFalse(available)
    }

    @Test
    fun nameAvailable_new_flow_with_unique_name_is_true() {
        val available = validator.nameAvailable(
            name = "신규",
            existing = listOf("cat-existing" to "프로모션"),
            currentCategoryId = null,
        )
        assertTrue(available)
    }

    @Test
    fun nameAvailable_edit_flow_keeping_own_name_is_true() {
        val available = validator.nameAvailable(
            name = "프로모션",
            existing = listOf(
                "cat-promo" to "프로모션",
                "cat-other" to "광고",
            ),
            currentCategoryId = "cat-promo",
        )
        assertTrue(available)
    }

    @Test
    fun nameAvailable_edit_flow_renaming_into_other_row_is_false() {
        val available = validator.nameAvailable(
            name = "광고",
            existing = listOf(
                "cat-promo" to "프로모션",
                "cat-other" to "광고",
            ),
            currentCategoryId = "cat-promo",
        )
        assertFalse(available)
    }
}
