package com.smartnoti.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic counterpart of the inline "전체 보기" CTA wired into
 * [DigestGroupCard]. The Composable itself is exercised manually via the
 * verification recipe in plan
 * `docs/plans/2026-04-26-inbox-bundle-preview-see-all.md` Task 3
 * (ComposeRule UI tests are not yet wired into the unit-test classpath in
 * this codebase — the project pattern is to extract Composable view-model
 * helpers and test them directly, mirroring `RuleRowDescriptionBuilder` and
 * `HomePassthroughReviewCardState`).
 *
 * Contract:
 * - `items.size <= PREVIEW_LIMIT` → all items render, CTA absent.
 * - `items.size > PREVIEW_LIMIT` and `showAll == false` → first PREVIEW_LIMIT
 *   render, CTA reads "전체 보기 · ${remaining}건 더".
 * - `items.size > PREVIEW_LIMIT` and `showAll == true` → all items render,
 *   CTA reads "최근만 보기".
 */
class DigestGroupCardSeeAllTest {

    @Test
    fun two_items_fit_within_preview_limit_so_no_cta() {
        val state = digestGroupCardPreviewState(itemsSize = 2, showAll = false)

        assertEquals(2, state.visibleCount)
        assertNull(state.ctaCopy)
    }

    @Test
    fun three_items_exactly_at_preview_limit_so_no_cta() {
        val state = digestGroupCardPreviewState(itemsSize = 3, showAll = false)

        assertEquals(3, state.visibleCount)
        assertNull(state.ctaCopy)
    }

    @Test
    fun five_items_collapsed_show_three_with_see_all_cta() {
        val state = digestGroupCardPreviewState(itemsSize = 5, showAll = false)

        assertEquals(3, state.visibleCount)
        assertEquals("전체 보기 · 2건 더", state.ctaCopy)
    }

    @Test
    fun five_items_expanded_show_all_with_collapse_cta() {
        val state = digestGroupCardPreviewState(itemsSize = 5, showAll = true)

        assertEquals(5, state.visibleCount)
        assertEquals("최근만 보기", state.ctaCopy)
    }

    @Test
    fun ten_items_collapsed_remaining_count_in_cta() {
        val state = digestGroupCardPreviewState(itemsSize = 10, showAll = false)

        assertEquals(3, state.visibleCount)
        assertEquals("전체 보기 · 7건 더", state.ctaCopy)
    }

    @Test
    fun ten_items_expanded_show_all_ten() {
        val state = digestGroupCardPreviewState(itemsSize = 10, showAll = true)

        assertEquals(10, state.visibleCount)
        assertEquals("최근만 보기", state.ctaCopy)
    }

    @Test
    fun show_all_flag_is_ignored_when_count_within_limit() {
        // showAll = true should still render no CTA when there is nothing to
        // expand — defensive against a stale rememberSaveable value if a
        // group's items shrink between recompositions.
        val state = digestGroupCardPreviewState(itemsSize = 2, showAll = true)

        assertEquals(2, state.visibleCount)
        assertNull(state.ctaCopy)
    }

    @Test
    fun zero_items_renders_nothing_and_no_cta() {
        val state = digestGroupCardPreviewState(itemsSize = 0, showAll = false)

        assertEquals(0, state.visibleCount)
        assertNull(state.ctaCopy)
    }
}
