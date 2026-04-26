package com.smartnoti.app.ui.screens.detail

import com.smartnoti.app.domain.usecase.InsightDrillDownRange
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip survival tests for [InsightDrillDownRangeState].
 *
 * Plan `docs/plans/2026-04-26-insight-drilldown-range-state-survival.md` Task 3.
 *
 * Task 3 originally called for a Compose-level test using `ComposeTestRule` to
 * prove the chip selection survives a Detail back navigation cycle. The unit
 * test classpath (see `app/build.gradle.kts`) does not include
 * `androidx.compose.ui:ui-test-junit4`, and `InsightDrillDownScreen` itself
 * directly resolves `NotificationRepository.getInstance(context)` /
 * `SettingsRepository.getInstance(context)` so a Robolectric-mounted Composable
 * test would require non-trivial fake-injection plumbing outside this plan's
 * scope.
 *
 * Instead this test exercises the same round-trip semantics directly against
 * the holder + Saver, which is the actual surface that `rememberSaveable`
 * delegates to during a backstack restore. The end-to-end NavController +
 * `popBackStack()` round-trip is covered by the ADB Verification recipe in
 * Task 5 (`docs/journeys/insight-drilldown.md`).
 *
 * Scenarios:
 *
 * 1. Same-instance recomposition with the same `initialRange` arg — the
 *    `LaunchedEffect(initialRange)` re-fire must not clobber a user override.
 * 2. Same-instance recomposition with a different `initialRange` arg after the
 *    user has selected — user-wins policy keeps the chosen range.
 * 3. Saver round-trip (process death simulation) — restored holder keeps both
 *    `currentRange` and `userOverridden`, so a follow-up route-arg refresh
 *    does not reset the selection.
 * 4. Distinct holder instance (different backstack entry) honors caller
 *    intent because that's a "new screen" by definition.
 */
class InsightDrillDownRangeRoundTripTest {

    @Test
    fun `same instance same arg recomposition keeps user selection`() {
        val state = InsightDrillDownRangeState(initialRouteValue = "recent_24_hours")
        // User picks "최근 3시간" via the FilterChip onClick → rangeState.select(...).
        state.select(InsightDrillDownRange.RECENT_3_HOURS)

        // Detail push + back: the same composable returns to RESUMED with the
        // identical URL `range=recent_24_hours` arg, and the LaunchedEffect
        // refires with that arg. The holder must keep the user's choice.
        repeat(3) { state.onRouteArgsChanged(initialRouteValue = "recent_24_hours") }

        assertEquals(InsightDrillDownRange.RECENT_3_HOURS, state.currentRange)
    }

    @Test
    fun `same instance different arg recomposition keeps user selection`() {
        val state = InsightDrillDownRangeState(initialRouteValue = "recent_24_hours")
        state.select(InsightDrillDownRange.RECENT_3_HOURS)

        // Hypothetical: the URL arg is rewritten to "all" (e.g. some external
        // refresh path). User-wins policy still keeps "최근 3시간".
        state.onRouteArgsChanged(initialRouteValue = "all")

        assertEquals(InsightDrillDownRange.RECENT_3_HOURS, state.currentRange)
    }

    @Test
    fun `saver round trip preserves user selection across process death`() {
        val original = InsightDrillDownRangeState(initialRouteValue = "recent_24_hours")
        original.select(InsightDrillDownRange.RECENT_3_HOURS)

        val saver = InsightDrillDownRangeState.Saver
        val saved = with(saver) { FakeSaverScopeForRoundTrip.save(original) }
        requireNotNull(saved) { "Saver returned null for non-default state" }
        val restored = saver.restore(saved)
        requireNotNull(restored) { "Saver failed to restore" }

        // Restored holder still treats the user as having overridden, so the
        // first LaunchedEffect refire after restore cannot clobber selection.
        restored.onRouteArgsChanged(initialRouteValue = "recent_24_hours")

        assertEquals(InsightDrillDownRange.RECENT_3_HOURS, restored.currentRange)
    }

    @Test
    fun `distinct holder instance honors new initial arg`() {
        // The user selects 최근 3시간 in the first insight (e.g. Shell app).
        val first = InsightDrillDownRangeState(initialRouteValue = "recent_24_hours")
        first.select(InsightDrillDownRange.RECENT_3_HOURS)

        // Reason-navigation push to a *different* insight pushes a brand-new
        // backstack entry → new holder instance. Caller intent (the URL arg
        // built from `currentRange.routeValue`) is honored as the seed.
        val second = InsightDrillDownRangeState(initialRouteValue = first.currentRange.routeValue)

        assertEquals(InsightDrillDownRange.RECENT_3_HOURS, second.currentRange)
    }
}

private object FakeSaverScopeForRoundTrip : androidx.compose.runtime.saveable.SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
}
