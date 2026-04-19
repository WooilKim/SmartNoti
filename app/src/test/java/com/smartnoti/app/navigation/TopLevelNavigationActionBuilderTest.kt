package com.smartnoti.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelNavigationActionBuilderTest {

    private val builder = TopLevelNavigationActionBuilder()

    @Test
    fun returns_no_op_when_target_route_is_already_current() {
        val action = builder.build(
            currentRoute = Routes.Home.route,
            targetRoute = Routes.Home.route,
        )

        assertEquals(TopLevelNavigationAction.NoOp, action)
    }

    @Test
    fun navigates_to_home_when_user_taps_home_from_another_tab() {
        val action = builder.build(
            currentRoute = Routes.Digest.route,
            targetRoute = Routes.Home.route,
        )

        assertEquals(TopLevelNavigationAction.Navigate(Routes.Home.route), action)
    }

    @Test
    fun navigates_normally_for_non_start_top_level_routes() {
        val action = builder.build(
            currentRoute = Routes.Home.route,
            targetRoute = Routes.Settings.route,
        )

        assertEquals(TopLevelNavigationAction.Navigate(Routes.Settings.route), action)
    }
}
