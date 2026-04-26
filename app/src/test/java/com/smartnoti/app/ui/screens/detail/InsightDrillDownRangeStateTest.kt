package com.smartnoti.app.ui.screens.detail

import com.smartnoti.app.domain.usecase.InsightDrillDownRange
import com.smartnoti.app.navigation.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InsightDrillDownRangeState].
 *
 * Plan `docs/plans/2026-04-26-insight-drilldown-range-state-survival.md` Task 1.
 *
 * Test A — pure state holder contract: 4 cases pin the user-selection-wins
 * semantics so a future regression of `rememberSaveable(initialRange)` keying
 * cannot quietly come back.
 *
 * - Initial entry: route value seeds `currentRange`.
 * - User selection mutates `currentRange`.
 * - Same holder receiving a new `initialRouteValue` (e.g. composition restart
 *   with the same backstack entry) MUST NOT clobber a user override.
 * - Distinct holder instance (separate backstack entry) honors caller intent.
 *
 * Test B — `routeValue` propagation contract: holder's `currentRange.routeValue`
 * feeds `Routes.Insight.createForReason`'s `range` parameter so the URL built
 * for the next reason insight always reflects the last user-selected window.
 */
class InsightDrillDownRangeStateTest {

    @Test
    fun `initial entry seeds currentRange from route value`() {
        val state = InsightDrillDownRangeState(initialRouteValue = "recent_3_hours")

        assertEquals(InsightDrillDownRange.RECENT_3_HOURS, state.currentRange)
    }

    @Test
    fun `select mutates currentRange`() {
        val state = InsightDrillDownRangeState(initialRouteValue = "recent_3_hours")

        state.select(InsightDrillDownRange.RECENT_24_HOURS)

        assertEquals(InsightDrillDownRange.RECENT_24_HOURS, state.currentRange)
    }

    @Test
    fun `onRouteArgsChanged does not clobber user selection`() {
        val state = InsightDrillDownRangeState(initialRouteValue = "recent_3_hours")
        state.select(InsightDrillDownRange.RECENT_24_HOURS)

        // Same backstack entry resumes with route arg "all" — but user override wins.
        state.onRouteArgsChanged(initialRouteValue = "all")

        assertEquals(InsightDrillDownRange.RECENT_24_HOURS, state.currentRange)
    }

    @Test
    fun `onRouteArgsChanged updates currentRange when user has not overridden`() {
        // No prior `select` call → idempotent re-application of the same arg
        // is safe; a different arg is honored as caller intent.
        val state = InsightDrillDownRangeState(initialRouteValue = "recent_3_hours")

        state.onRouteArgsChanged(initialRouteValue = "all")

        assertEquals(InsightDrillDownRange.ALL, state.currentRange)
    }

    @Test
    fun `distinct holder instance honors its own initialRouteValue`() {
        // Different backstack entry (different filterValue) → caller intent
        // wins because that's a *new* screen.
        val first = InsightDrillDownRangeState(initialRouteValue = "recent_3_hours")
        first.select(InsightDrillDownRange.RECENT_24_HOURS)

        val second = InsightDrillDownRangeState(initialRouteValue = "all")

        assertEquals(InsightDrillDownRange.RECENT_24_HOURS, first.currentRange)
        assertEquals(InsightDrillDownRange.ALL, second.currentRange)
    }

    @Test
    fun `selected route value flows into createForReason URL for all range source combos`() {
        val sources = listOf(null, "suppression")
        for (range in InsightDrillDownRange.entries) {
            for (source in sources) {
                val state = InsightDrillDownRangeState(initialRouteValue = "recent_24_hours")
                state.select(range)

                val url = Routes.Insight.createForReason(
                    reasonTag = "promo",
                    range = state.currentRange.routeValue,
                    source = source,
                )

                assertTrue(
                    "Expected url to carry range=${range.routeValue} (source=$source) " +
                        "but was: $url",
                    url.contains("range=${range.routeValue}"),
                )
            }
        }
    }

    @Test
    fun `Saver round-trips currentRange and userOverridden`() {
        val original = InsightDrillDownRangeState(initialRouteValue = "recent_3_hours")
        original.select(InsightDrillDownRange.ALL)

        val saved = with(InsightDrillDownRangeState.Saver) {
            FakeSaverScope.save(original)
        }
        requireNotNull(saved) { "Saver returned null for non-default state" }
        val restored = InsightDrillDownRangeState.Saver.restore(saved)
        requireNotNull(restored) { "Saver failed to restore" }

        assertEquals(InsightDrillDownRange.ALL, restored.currentRange)

        // Restored holder must still treat user as having overridden, so a
        // subsequent route-arg change does not clobber the restored selection.
        restored.onRouteArgsChanged(initialRouteValue = "recent_3_hours")
        assertEquals(InsightDrillDownRange.ALL, restored.currentRange)
    }
}

/**
 * `androidx.compose.runtime.saveable.SaverScope` is a `fun interface` that just
 * returns whether a value can be saved. The unit test runs outside Compose so
 * we provide a permissive scope that approves everything.
 */
private object FakeSaverScope : androidx.compose.runtime.saveable.SaverScope {
    override fun canBeSaved(value: Any): Boolean = true

    fun <Original, Saveable : Any> save(
        value: Original,
    ): Saveable? {
        @Suppress("UNCHECKED_CAST")
        val saver = InsightDrillDownRangeState.Saver as androidx.compose.runtime.saveable.Saver<Original, Saveable>
        return with(saver) { save(value) }
    }
}
