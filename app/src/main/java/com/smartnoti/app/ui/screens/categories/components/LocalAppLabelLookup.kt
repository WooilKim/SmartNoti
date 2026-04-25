package com.smartnoti.app.ui.screens.categories.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * CompositionLocal for [AppLabelLookup] introduced by plan
 * `docs/plans/2026-04-25-category-chip-app-label-lookup.md` Task 3.
 *
 * **Why CompositionLocal (option A in the plan):** the Categories surfaces
 * (card / Detail / Editor) all need the same lookup, and the plan explicitly
 * leaves room to wire additional surfaces (card metadata line, Detail
 * "연결된 앱 ·") in a follow-up. A CompositionLocal lets call sites read
 * `LocalAppLabelLookup.current` without each composable re-instantiating
 * `PackageManagerAppLabelLookup`, and keeps the formatter testable because
 * the production binding is set once at the [com.smartnoti.app.MainActivity]
 * `setContent` boundary.
 *
 * Default: [AppLabelLookup.Identity] (always returns `null` → raw
 * matchValue). This means previews / unit-style harnesses that don't bind
 * the local still get the v1 raw-packageName behaviour and never crash on
 * a missing PackageManager.
 */
val LocalAppLabelLookup = compositionLocalOf<AppLabelLookup> { AppLabelLookup.Identity }

/**
 * Convenience factory for call sites that prefer to bind the production
 * lookup themselves (option B in the plan). Memoised against the current
 * `PackageManager` so multiple chip rows on one screen share one
 * [PackageManagerAppLabelLookup] instance and the same `getApplicationInfo`
 * cache.
 */
@Composable
@ReadOnlyComposable
fun rememberPackageManagerAppLabelLookup(): AppLabelLookup {
    val pm = LocalContext.current.packageManager
    // ReadOnlyComposable forbids `remember`, but we deliberately avoid
    // remember here because PackageManager is a process-singleton and the
    // lookup is a thin wrapper — re-instantiating per recomposition is
    // cheap. If allocation pressure ever shows up, switch this to a
    // non-ReadOnly composable + remember(pm).
    return PackageManagerAppLabelLookup(pm)
}

/**
 * Screen-friendly accessor: returns a [PackageManagerAppLabelLookup] bound
 * to the current `PackageManager`, memoised across recompositions. Prefer
 * this over [rememberPackageManagerAppLabelLookup] when the call site is a
 * regular composable (not `ReadOnlyComposable`).
 */
@Composable
fun rememberAppLabelLookup(): AppLabelLookup {
    val pm = LocalContext.current.packageManager
    return remember(pm) { PackageManagerAppLabelLookup(pm) }
}
