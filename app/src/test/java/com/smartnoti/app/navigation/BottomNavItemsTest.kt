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
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11
 * collapses the bar to four entries — Home / 정리함 (Inbox) / 분류 / Settings —
 * demoting the legacy 규칙 tab to a Settings sub-menu ("고급 규칙 편집") while
 * keeping the underlying route registered for deep links.
 */
class BottomNavItemsTest {

    @Test
    fun bottom_nav_contains_four_entries_after_rules_demotion() {
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
    fun bottom_nav_does_not_expose_rules_route() {
        val routes = bottomNavItems.map { it.route }
        assertFalse(
            "Rules must be reachable only via Settings > 고급 규칙 편집 (plan Phase P3 Task 11).",
            routes.contains(Routes.Rules.create()),
        )
        assertFalse(
            "Rules must be reachable only via Settings > 고급 규칙 편집 (plan Phase P3 Task 11).",
            routes.contains(Routes.Rules.route),
        )
    }

    @Test
    fun bottom_nav_exposes_home_inbox_categories_settings_in_order() {
        assertEquals(
            listOf(
                Routes.Home.route,
                Routes.Inbox.route,
                Routes.Categories.route,
                Routes.Settings.route,
            ),
            bottomNavItems.map { it.route },
        )
    }

    @Test
    fun bottom_nav_exposes_inbox_route() {
        val routes = bottomNavItems.map { it.route }
        assertTrue(
            "정리함 tab must be registered (plan Phase P3 Task 11).",
            routes.contains(Routes.Inbox.route),
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

    @Test
    fun rules_route_still_survives_for_deep_link_and_settings_entry_point() {
        // Sanity guard: deep link from NotificationDetail's "적용된 규칙" chip
        // and the Settings "고급 규칙 편집" entry both rely on this staying stable.
        assertTrue(Routes.Rules.route.isNotBlank())
        assertEquals("rules", Routes.Rules.create())
    }

    @Test
    fun inbox_route_is_stable() {
        assertEquals("inbox", Routes.Inbox.route)
    }
}
