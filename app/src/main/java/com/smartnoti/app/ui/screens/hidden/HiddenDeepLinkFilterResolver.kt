package com.smartnoti.app.ui.screens.hidden

import com.smartnoti.app.domain.usecase.SilentGroupKey

/**
 * Turns the Hidden 화면 deep-link query params (`sender`, `packageName`) into the
 * [SilentGroupKey] the screen uses as `initialFilter`, or `null` when no filter is
 * requested.
 *
 * Extracted into a pure object (instead of inlined into [com.smartnoti.app.navigation.AppNavHost])
 * so the precedence contract is testable without Compose/NavHost infrastructure. The tray
 * group summary's `contentIntent` on the listener side writes exactly one of these two
 * params; this resolver is the app-side symmetry.
 *
 * Precedence: sender beats packageName. Blank values are treated as absent so URL-encoded
 * whitespace cannot accidentally form a `Sender("  ")` group the screen would never match.
 *
 * See `docs/plans/2026-04-21-silent-tray-sender-grouping.md` Task 4.
 */
object HiddenDeepLinkFilterResolver {

    fun resolve(sender: String?, packageName: String?): SilentGroupKey? {
        val trimmedSender = sender?.trim().orEmpty()
        if (trimmedSender.isNotEmpty()) {
            return SilentGroupKey.Sender(trimmedSender)
        }
        val trimmedPackage = packageName?.trim().orEmpty()
        if (trimmedPackage.isNotEmpty()) {
            return SilentGroupKey.App(trimmedPackage)
        }
        return null
    }

    /**
     * Decide whether a Hidden inbox group (which is keyed by packageName because
     * `toHiddenGroups` clusters by app) should be the highlight target for [filter].
     *
     * - [SilentGroupKey.App] ⇒ direct packageName compare.
     * - [SilentGroupKey.Sender] ⇒ group matches if any of its [senders] equals the
     *   filter's normalized name (trim, exact compare — same contract
     *   [com.smartnoti.app.domain.usecase.SilentNotificationGroupingPolicy] uses).
     * - `null` filter ⇒ no match (caller should skip highlighting entirely).
     */
    fun matchesGroup(
        filter: SilentGroupKey?,
        groupPackageName: String,
        senders: List<String?>,
    ): Boolean {
        return when (filter) {
            null -> false
            is SilentGroupKey.App -> filter.packageName == groupPackageName
            is SilentGroupKey.Sender -> senders.any { raw ->
                raw?.trim().orEmpty() == filter.normalizedName
            }
        }
    }
}
