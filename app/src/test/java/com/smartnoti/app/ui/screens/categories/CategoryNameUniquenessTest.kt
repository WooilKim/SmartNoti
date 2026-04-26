package com.smartnoti.app.ui.screens.categories

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-25-category-name-uniqueness.md` Task 1.
 *
 * Pure-Kotlin uniqueness rule: trim + case-insensitive comparison; the
 * row identified by [CategoryNameUniqueness.evaluate]'s `currentCategoryId`
 * is excluded from the collision pool so users can edit other fields of an
 * existing Category without tripping the gate on their own name.
 */
class CategoryNameUniquenessTest {

    @Test
    fun empty_name_is_EMPTY() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "",
            currentCategoryId = null,
            existing = emptyList(),
        )
        assertEquals(CategoryNameStatus.EMPTY, status)
    }

    @Test
    fun whitespace_only_name_is_EMPTY() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "   ",
            currentCategoryId = null,
            existing = emptyList(),
        )
        assertEquals(CategoryNameStatus.EMPTY, status)
    }

    @Test
    fun any_name_with_no_existing_categories_is_OK() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "프로모션",
            currentCategoryId = null,
            existing = emptyList(),
        )
        assertEquals(CategoryNameStatus.OK, status)
    }

    @Test
    fun exact_duplicate_name_is_DUPLICATE() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "프로모션",
            currentCategoryId = null,
            existing = listOf("cat-1" to "프로모션"),
        )
        assertEquals(CategoryNameStatus.DUPLICATE, status)
    }

    @Test
    fun duplicate_name_with_surrounding_whitespace_is_DUPLICATE() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = " 프로모션 ",
            currentCategoryId = null,
            existing = listOf("cat-1" to "프로모션"),
        )
        assertEquals(CategoryNameStatus.DUPLICATE, status)
    }

    @Test
    fun case_insensitive_duplicate_is_DUPLICATE() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "work",
            currentCategoryId = null,
            existing = listOf("cat-1" to "Work"),
        )
        assertEquals(CategoryNameStatus.DUPLICATE, status)
    }

    @Test
    fun substring_overlap_is_not_a_duplicate() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "중요알림",
            currentCategoryId = null,
            existing = listOf("cat-1" to "중요"),
        )
        assertEquals(CategoryNameStatus.OK, status)
    }

    @Test
    fun edit_flow_keeping_own_name_is_OK() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "프로모션",
            currentCategoryId = "cat-promo",
            existing = listOf(
                "cat-promo" to "프로모션",
                "cat-other" to "광고",
            ),
        )
        assertEquals(CategoryNameStatus.OK, status)
    }

    @Test
    fun edit_flow_renaming_into_other_row_is_DUPLICATE() {
        val status = CategoryNameUniqueness.evaluate(
            candidate = "광고",
            currentCategoryId = "cat-promo",
            existing = listOf(
                "cat-promo" to "프로모션",
                "cat-other" to "광고",
            ),
        )
        assertEquals(CategoryNameStatus.DUPLICATE, status)
    }
}
