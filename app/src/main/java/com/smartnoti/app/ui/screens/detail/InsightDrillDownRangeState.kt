package com.smartnoti.app.ui.screens.detail

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.smartnoti.app.domain.usecase.InsightDrillDownRange

/**
 * Pure-Kotlin state holder for the insight drill-down range chip selection.
 *
 * Plan `docs/plans/2026-04-26-insight-drilldown-range-state-survival.md` Task 2.
 *
 * The holder exists to fix a regression where keying `rememberSaveable` on
 * `initialRange` (the URL arg) reset user-selected range every time the same
 * Composable re-entered Composition with the same backstack entry — for
 * example coming back from the Detail screen via system back.
 *
 * Semantics:
 * - The constructor seeds `currentRange` from the caller's route value (URL
 *   arg). At this point the user is considered to have not yet overridden.
 * - [select] is the user-action mutator — it flips `userOverridden` so that
 *   subsequent [onRouteArgsChanged] calls cannot clobber the selection.
 * - [onRouteArgsChanged] is the LaunchedEffect-triggered re-application of
 *   the URL arg. While `userOverridden` is false the holder follows the URL
 *   (caller intent); once the user has selected anything, the holder ignores
 *   route arg refreshes and keeps the user's window.
 *
 * Distinct backstack entries (different filterValue / source) produce a new
 * holder instance, so caller intent is honored for "different screen" entries.
 *
 * The holder participates in `rememberSaveable` via [Saver] which preserves
 * both `currentRange` (by `routeValue`, kept stable across enum renames) and
 * `userOverridden` so a process-death restore keeps the user's selection.
 */
@Stable
class InsightDrillDownRangeState internal constructor(
    initialRouteValue: String,
    initialUserOverridden: Boolean,
) {
    constructor(initialRouteValue: String) : this(
        initialRouteValue = initialRouteValue,
        initialUserOverridden = false,
    )

    private var _currentRange by mutableStateOf(
        InsightDrillDownRange.fromRouteValue(initialRouteValue),
    )
    private var _userOverridden: Boolean = initialUserOverridden

    val currentRange: InsightDrillDownRange
        get() = _currentRange

    internal val userOverridden: Boolean
        get() = _userOverridden

    fun select(range: InsightDrillDownRange) {
        _currentRange = range
        _userOverridden = true
    }

    fun onRouteArgsChanged(initialRouteValue: String) {
        if (_userOverridden) return
        _currentRange = InsightDrillDownRange.fromRouteValue(initialRouteValue)
    }

    companion object {
        /**
         * `Saver` for `rememberSaveable`. Persists `routeValue` (string,
         * stable across enum renames) and `userOverridden` (boolean) so a
         * restored holder keeps the user-wins behavior intact.
         */
        val Saver: Saver<InsightDrillDownRangeState, Any> = listSaver(
            save = { state ->
                listOf<Any>(state.currentRange.routeValue, state.userOverridden)
            },
            restore = { saved ->
                val list = saved as List<*>
                val routeValue = list[0] as String
                val userOverridden = list[1] as Boolean
                InsightDrillDownRangeState(
                    initialRouteValue = routeValue,
                    initialUserOverridden = userOverridden,
                )
            },
        )
    }
}
