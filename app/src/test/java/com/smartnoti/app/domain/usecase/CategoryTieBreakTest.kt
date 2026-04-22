package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-phase tie-break test for plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 1.
 *
 * Pins the product decision recorded in the plan's Risks section on
 * 2026-04-22: when two Categories have the **same specificity**, the one
 * the user placed higher in the 분류 tab (lower `order` index) wins. This
 * is the drag-reorder tie-break contract, reusing Phase C's tier-aware
 * `moveRule` infra at the Category level via `order`.
 *
 * The resolver itself (`CategoryConflictResolver`) is introduced in Phase
 * P2 Task 5/6 — this test compiles against its eventual API so Task 6
 * cannot ship without preserving this behavior. Until then, the test
 * fails to compile (symbol `CategoryConflictResolver` does not exist).
 *
 * Specificity in scope for Task 1: we use the plan's app-pin bonus rule
 * — two categories with and without `appPackageName` have different
 * specificity, so they are not tied. The tie-break only fires when both
 * categories have the same pin-state. This test therefore uses two
 * non-app-pinned Categories with the same underlying match shape so
 * specificity is equal and `order` is the only discriminator.
 */
class CategoryTieBreakTest {

    @Test
    fun lower_order_category_wins_on_specificity_tie() {
        val higher = Category(
            id = "cat-top",
            name = "상단",
            appPackageName = null,
            ruleIds = listOf("rule-keyword-shared"),
            action = CategoryAction.PRIORITY,
            order = 0,
        )
        val lower = Category(
            id = "cat-bottom",
            name = "하단",
            appPackageName = null,
            ruleIds = listOf("rule-keyword-shared"),
            action = CategoryAction.SILENT,
            order = 1,
        )

        val winner = CategoryConflictResolver().resolve(
            matched = listOf(lower, higher),
            allCategories = listOf(higher, lower),
        )

        // Even though `lower` appeared first in the `matched` list, the
        // winner is the Category the user dragged higher (order = 0).
        assertEquals("cat-top", winner?.id)
        assertEquals(CategoryAction.PRIORITY, winner?.action)
    }

    @Test
    fun tie_break_flips_when_user_drags_category_up() {
        // Simulates the user reordering: the previously-lower Category is
        // now `order = 0`. The winning action must flip to that Category's.
        val reorderedTop = Category(
            id = "cat-was-bottom",
            name = "방금 위로 올린",
            appPackageName = null,
            ruleIds = listOf("rule-keyword-shared"),
            action = CategoryAction.SILENT,
            order = 0,
        )
        val reorderedBottom = Category(
            id = "cat-was-top",
            name = "아래로 밀린",
            appPackageName = null,
            ruleIds = listOf("rule-keyword-shared"),
            action = CategoryAction.PRIORITY,
            order = 1,
        )

        val winner = CategoryConflictResolver().resolve(
            matched = listOf(reorderedBottom, reorderedTop),
            allCategories = listOf(reorderedTop, reorderedBottom),
        )

        assertEquals("cat-was-bottom", winner?.id)
        assertEquals(CategoryAction.SILENT, winner?.action)
    }

    @Test
    fun ignore_vs_silent_tie_break_defers_to_order() {
        // IGNORE and SILENT are both "don't show to user" buckets but they
        // behave differently downstream (IGNORE archives, SILENT surfaces
        // later in digest). When both fire with the same specificity, the
        // user's explicit drag order decides — there is no hard-coded
        // precedence between the two.
        val ignoreFirst = Category(
            id = "cat-ignore",
            name = "무시",
            appPackageName = null,
            ruleIds = listOf("rule-shared"),
            action = CategoryAction.IGNORE,
            order = 0,
        )
        val silentSecond = Category(
            id = "cat-silent",
            name = "조용",
            appPackageName = null,
            ruleIds = listOf("rule-shared"),
            action = CategoryAction.SILENT,
            order = 1,
        )

        val winner = CategoryConflictResolver().resolve(
            matched = listOf(silentSecond, ignoreFirst),
            allCategories = listOf(ignoreFirst, silentSecond),
        )

        assertEquals(CategoryAction.IGNORE, winner?.action)
    }

    @Test
    fun app_pinned_category_beats_keyword_only_category_regardless_of_order() {
        // Control case — verifies the tie-break only fires on equal
        // specificity. An app-pinned Category is strictly more specific
        // than a keyword-only one, so its `order` value should not matter.
        val appPinned = Category(
            id = "cat-app",
            name = "카카오톡",
            appPackageName = "com.kakao.talk",
            ruleIds = listOf("rule-app-kakao"),
            action = CategoryAction.PRIORITY,
            order = 5, // intentionally lower priority in drag order
        )
        val keywordOnly = Category(
            id = "cat-keyword",
            name = "광고 키워드",
            appPackageName = null,
            ruleIds = listOf("rule-keyword-ad"),
            action = CategoryAction.IGNORE,
            order = 0, // intentionally higher priority in drag order
        )

        val winner = CategoryConflictResolver().resolve(
            matched = listOf(keywordOnly, appPinned),
            allCategories = listOf(keywordOnly, appPinned),
        )

        assertEquals("cat-app", winner?.id)
        assertEquals(CategoryAction.PRIORITY, winner?.action)
    }

    @Test
    fun single_match_returns_that_category() {
        val only = Category(
            id = "cat-solo",
            name = "단독",
            appPackageName = null,
            ruleIds = listOf("rule-solo"),
            action = CategoryAction.DIGEST,
            order = 0,
        )

        val winner = CategoryConflictResolver().resolve(
            matched = listOf(only),
            allCategories = listOf(only),
        )

        assertEquals("cat-solo", winner?.id)
    }

    @Test
    fun empty_match_returns_null() {
        val winner = CategoryConflictResolver().resolve(
            matched = emptyList(),
            allCategories = emptyList(),
        )

        assertEquals(null, winner)
    }
}
