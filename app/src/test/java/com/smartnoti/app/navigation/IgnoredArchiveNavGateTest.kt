package com.smartnoti.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [IgnoredArchiveNavGate] — the gating that decides
 * whether the Settings button exposes a tap handler and whether the
 * `ignored_archive` route is registered in the nav graph.
 *
 * The production crash these tests guard against:
 *
 *   `IllegalArgumentException: Navigation destination ignored_archive cannot
 *    be found`
 *
 * observed in the 2026-04-21 verification run on the `ignored-archive`
 * journey: flipping the Settings toggle OFF→ON and tapping the "무시됨
 * 아카이브 열기" button within the same composition can race the nav graph
 * rebuild. The invariant that makes that race impossible is:
 *
 *   [isRouteRegistered] must return `true` whenever [isButtonVisible]
 *   returns `true`, and it must do so **independently of the toggle state**
 *   so that the button lambda and the route are never gated by two
 *   independently-observed copies of the same boolean.
 *
 * Plan: `docs/plans/2026-04-22-ignored-archive-first-tap-nav-race.md`
 * Option A — the route is always registered; the toggle only controls the
 * UX entry point (Settings button visibility).
 */
class IgnoredArchiveNavGateTest {

    @Test
    fun button_is_hidden_when_toggle_is_off() {
        assertEquals(false, IgnoredArchiveNavGate.isButtonVisible(showIgnoredArchive = false))
    }

    @Test
    fun button_is_visible_when_toggle_is_on() {
        assertEquals(true, IgnoredArchiveNavGate.isButtonVisible(showIgnoredArchive = true))
    }

    @Test
    fun route_is_registered_when_toggle_is_on() {
        assertEquals(true, IgnoredArchiveNavGate.isRouteRegistered(showIgnoredArchive = true))
    }

    /**
     * Core anti-race contract: the route must be present in the nav graph
     * even when the toggle is OFF. Without this, the Settings button and the
     * nav graph end up gated by two independently-observed reads of the same
     * flag, and the NavController's destination table can lag by a frame
     * behind the button's recomposition — which is exactly the race the
     * 2026-04-21 verification hit.
     *
     * Expected to fail until plan
     * `2026-04-22-ignored-archive-first-tap-nav-race` Task 2 lands.
     */
    @Test
    fun route_is_registered_even_when_toggle_is_off_to_eliminate_first_tap_race() {
        assertEquals(
            "route must be registered unconditionally so the OFF→ON transitional " +
                "frame can never tap into a missing destination",
            true,
            IgnoredArchiveNavGate.isRouteRegistered(showIgnoredArchive = false),
        )
    }

    /**
     * Defensive pairing: no matter what the toggle value is, whenever the
     * button is tappable the route must be present. This is the invariant
     * that actually protects the user; the test above pins the particular
     * implementation choice (Option A) that satisfies it cheaply.
     */
    @Test
    fun route_registration_covers_button_visibility_for_every_toggle_value() {
        for (toggle in listOf(false, true)) {
            val buttonVisible = IgnoredArchiveNavGate.isButtonVisible(toggle)
            val routeRegistered = IgnoredArchiveNavGate.isRouteRegistered(toggle)
            if (buttonVisible) {
                assertTrue(
                    "button is visible for toggle=$toggle but route is not registered — " +
                        "first-tap race would crash the app",
                    routeRegistered,
                )
            }
        }
    }
}
