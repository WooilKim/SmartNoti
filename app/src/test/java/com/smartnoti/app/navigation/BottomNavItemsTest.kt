package com.smartnoti.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the BottomNav contract after Rules UX v2 Phase A Task 4.
 *
 * The Priority (중요) tab was removed from the bottom navigation bar — users reach the
 * Priority review surface via the Home "검토 대기" passthrough card instead. The
 * Priority composable itself must remain registered in [AppNavHost] so the Home card
 * tap, and PRIORITY-decision replacement notifications, still resolve.
 *
 * If this test fails because Priority was re-added to the nav, revisit
 * docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md first — the whole Phase A
 * restructure assumes the tab is gone.
 */
class BottomNavItemsTest {

    @Test
    fun bottom_nav_contains_four_entries_after_priority_removal() {
        assertEquals(4, bottomNavItems.size)
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
    fun bottom_nav_exposes_home_digest_rules_settings_in_order() {
        // Rules' bottom-nav entry navigates to the bare "rules" URL (not the
        // pattern with `{highlightRuleId}`) so that nav-compose resolves it to
        // the unfiltered list via the query param's `null` default. Selection
        // matching in AppBottomBar compares route prefixes to handle the
        // pattern/URL mismatch.
        assertEquals(
            listOf(
                Routes.Home.route,
                Routes.Digest.route,
                Routes.Rules.create(),
                Routes.Settings.route,
            ),
            bottomNavItems.map { it.route },
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
