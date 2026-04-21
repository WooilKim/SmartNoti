package com.smartnoti.app.navigation

/**
 * Nav-gating contract for the 무시됨 아카이브 route.
 *
 * Plan `2026-04-21-ignore-tier-fourth-decision` Task 6 hid the archive behind
 * a Settings opt-in by gating **both** the Settings button lambda and the
 * `composable(Routes.IgnoredArchive.route)` registration on the same
 * `showIgnoredArchive` boolean. That introduced a one-frame race: on the
 * composition where the toggle flips OFF→ON, the button lambda becomes
 * non-null, but the NavController's destination table can lag by a frame, so
 * a first-tap within that window crashes with
 * `IllegalArgumentException: Navigation destination ignored_archive cannot be
 * found`.
 *
 * Plan `2026-04-22-ignored-archive-first-tap-nav-race` resolves that by
 * decoupling the two conditions: the route is registered **unconditionally**
 * (Option A), and only the entry-point button is gated by the toggle. This
 * guarantees the invariant the [IgnoredArchiveNavGate.isReachable] unit test
 * pins:
 *
 *   button lambda exposed ⇒ route already registered in graph
 *
 * which is the only property a user-level tap needs. If a deep link arrives
 * while the toggle is OFF, the screen itself is responsible for rendering an
 * empty-state hint pointing back at Settings (tracked as a separate known
 * gap on the ignored-archive journey).
 */
object IgnoredArchiveNavGate {

    /**
     * Whether the Settings "무시됨 아카이브 열기" button should expose a
     * non-null tap handler. The button is the only in-app entry point —
     * deep links are treated as a separate concern.
     */
    fun isButtonVisible(showIgnoredArchive: Boolean): Boolean = showIgnoredArchive

    /**
     * Whether the route must be registered in the nav graph. Must be `true`
     * whenever [isButtonVisible] is `true`, otherwise the first-tap race
     * returns.
     *
     * Current implementation mirrors the pre-fix [AppNavHost] wiring (route
     * registered only while the toggle is ON); Task 2 of plan
     * `2026-04-22-ignored-archive-first-tap-nav-race` switches this to
     * unconditional `true` as part of the Option A fix.
     */
    fun isRouteRegistered(showIgnoredArchive: Boolean): Boolean = showIgnoredArchive
}
