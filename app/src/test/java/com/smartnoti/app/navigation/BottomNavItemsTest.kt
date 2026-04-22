package com.smartnoti.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the BottomNav contract.
 *
 * The Priority (중요) tab was removed in Rules UX v2 Phase A Task 4 — users reach the
 * Priority review surface via the Home "검토 대기" passthrough card instead. The
 * Priority composable itself must remain registered in [AppNavHost] so the Home card
 * tap, and PRIORITY-decision replacement notifications, still resolve.
 *
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 8
 * adds the "분류" primary tab between Digest (정리함) and Rules (규칙). The 4-tab
 * reshape that drops Rules in favour of the Settings sub-menu is Task 11 — until
 * that ships the bar has five entries.
 */
class BottomNavItemsTest {

    @Test
    fun bottom_nav_contains_five_entries_during_categories_rollout() {
        // Phase P3 Task 8 adds 분류 alongside the existing four. Task 11 will
        // demote Rules to Settings and bring the count back to four.
        assertEquals(5, bottomNavItems.size)
    }

    @Test
    fun bottom_nav_does_not_expose_priority_route() {
        val routes = bottomNavItems.map { it.route }
        assertFalse(
            "Priority tab must stay out of BottomNav (see Phase A Task 4).",
            routes.contains(Routes.Priority.route),
        )
    }

    @Test
    fun bottom_nav_exposes_home_digest_categories_rules_settings_in_order() {
        // Rules' bottom-nav entry navigates to the bare "rules" URL (not the
        // pattern with `{highlightRuleId}`) so that nav-compose resolves it to
        // the unfiltered list via the query param's `null` default. Selection
        // matching in AppBottomBar compares route prefixes to handle the
        // pattern/URL mismatch.
        assertEquals(
            listOf(
                Routes.Home.route,
                Routes.Digest.route,
                Routes.Categories.route,
                Routes.Rules.create(),
                Routes.Settings.route,
            ),
            bottomNavItems.map { it.route },
        )
    }

    @Test
    fun bottom_nav_exposes_categories_route() {
        val routes = bottomNavItems.map { it.route }
        assertTrue(
            "분류 tab must be registered (plan Phase P3 Task 8).",
            routes.contains(Routes.Categories.route),
        )
    }

    @Test
    fun priority_route_still_survives_for_home_card_entry_point() {
        // Sanity guard: even though the tab is gone, the route string must stay stable
        // so AppNavHost can register the Priority composable and HomeScreen can navigate
        // to it via onPriorityClick.
        assertTrue(Routes.Priority.route.isNotBlank())
        assertEquals("priority", Routes.Priority.route)
    }
}
