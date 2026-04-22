package com.smartnoti.app.ui.screens.categories

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract: the 분류 탭 empty-state CTA and the normal-state FAB both route to
 * the same "새 분류 만들기" action, and both render with the exact same
 * user-facing label. This test pins the copy to a single source of truth
 * (`CategoriesEmptyStateAction.LABEL`) so that a future edit to one entry
 * point cannot silently drift from the other.
 *
 * Plan: `docs/plans/2026-04-22-categories-empty-state-inline-cta.md`
 * Task 1.
 */
class CategoriesEmptyStateContractTest {

    @Test
    fun `CTA label is the canonical unified copy`() {
        assertEquals("새 분류 만들기", CategoriesEmptyStateAction.LABEL)
    }
}
