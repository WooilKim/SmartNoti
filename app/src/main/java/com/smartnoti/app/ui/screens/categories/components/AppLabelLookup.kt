package com.smartnoti.app.ui.screens.categories.components

import android.content.pm.PackageManager

/**
 * Single-method abstraction introduced by plan
 * `docs/plans/2026-04-25-category-chip-app-label-lookup.md` Task 2.
 *
 * Resolves an Android `packageName` (the raw form rules persist as their
 * matchValue, e.g. `com.kakao.talk`) to the user-facing application label
 * (e.g. `카카오톡`). Returning `null` means "label unknown" — callers
 * (currently [CategoryConditionChipFormatter]) must gracefully fall back to
 * the raw packageName so a chip never renders an empty string.
 *
 * The interface stays Compose- and Android-agnostic so the formatter unit
 * tests can substitute a deterministic in-memory fake without spinning up a
 * `PackageManager`. Production wiring lives in [PackageManagerAppLabelLookup]
 * below.
 */
fun interface AppLabelLookup {
    fun labelFor(packageName: String): String?

    companion object {
        /**
         * Default no-op lookup: every call returns `null`, which the
         * formatter treats as "label unknown" and renders the raw
         * matchValue. Used as the default formatter parameter so existing
         * call sites that don't inject a real lookup keep their v1
         * raw-packageName behaviour.
         */
        val Identity: AppLabelLookup = AppLabelLookup { null }
    }
}

/**
 * Production [AppLabelLookup] backed by Android's [PackageManager]. Mirrors
 * the listener-side label resolution at
 * `notification/SmartNotiNotificationListenerService.kt:197-202`: looks the
 * `ApplicationInfo` up by package name, calls
 * `PackageManager.getApplicationLabel`, and swallows
 * [PackageManager.NameNotFoundException] (returning `null`) so an uninstalled
 * or hidden app doesn't crash the chip render.
 *
 * The `getApplicationLabel(...)` call is a synchronous lookup against the
 * `PackageManager` cache and is generally fast enough for the small N of
 * APP-typed rules we render per Categories screen. If the editor's preview
 * recomposes per keystroke and lag becomes visible, a `remember(packageName)`
 * cache around the call site is the cheapest mitigation — see plan Risks.
 */
class PackageManagerAppLabelLookup(
    private val packageManager: PackageManager,
) : AppLabelLookup {
    override fun labelFor(packageName: String): String? = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Exception) {
        // Defensive: PackageManager can throw RuntimeException variants on
        // certain devices for restricted packages. Treat those as "label
        // unknown" rather than letting the chip render crash.
        null
    }
}
