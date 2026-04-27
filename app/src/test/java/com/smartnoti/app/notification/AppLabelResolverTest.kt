package com.smartnoti.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Failing tests for plan
 * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
 * Task 1 (Issue #503).
 *
 * Pins the explicit fallback chain that the new
 * [com.smartnoti.app.notification.AppLabelResolver] (Task 2) MUST honour:
 *
 *   1. `applicationInfo.loadLabel(pm)` returns a non-blank, non-packageName
 *      string → use it.
 *   2. else `pm.getApplicationLabel(applicationInfo)` (some OEMs return a
 *      different string from `loadLabel` — H1 in plan).
 *   3. else `pm.getNameForUid(applicationInfo.uid)` (H3: Listener may run
 *      with a system context whose PackageManager mis-resolves user-installed
 *      app labels via the per-ApplicationInfo path).
 *   4. else (final, never throws): `packageName` itself, so the UI never
 *      renders an empty string but degrades gracefully to the raw id.
 *
 * The resolver MUST NOT swallow `Exception` whole — only
 * [android.content.pm.PackageManager.NameNotFoundException] /
 * [android.content.res.Resources.NotFoundException] / `RuntimeException` for
 * permission edge-cases are caught, and each catch advances to the NEXT
 * chain step rather than short-circuiting straight to `packageName`.
 *
 * These tests are RED on `main` because [AppLabelResolver] and its companion
 * source-port type [PackageLabelSource] do not exist yet — Task 2 introduces
 * them. Compile failure on those symbols IS the intended RED signal — see
 * the plan "Files (new)" list and the "compile errors are RED" carve-out
 * in the user-facing Task 1 instructions.
 *
 * Tests construct [AppLabelResolver] against a hand-rolled
 * [FakePackageLabelSource] port so each step of the chain can be made to
 * return blank, return a packageName-shaped string, or throw independently.
 * This keeps Task 1 a pure-JVM test (no Robolectric) and lets Task 2 ship
 * the production [PackageManager]-backed adapter without coupling test
 * fakery to Android's abstract-class surface area.
 */
class AppLabelResolverTest {

    @Test
    fun resolves_Gmail_label_when_ApplicationInfo_present() {
        val source = FakePackageLabelSource(
            loadLabelReturns = mapOf(GMAIL_PACKAGE to "Gmail"),
        )

        val resolver = AppLabelResolver(source)

        assertEquals("Gmail", resolver.resolve(GMAIL_PACKAGE))
    }

    @Test
    fun falls_back_to_getApplicationLabel_when_loadLabel_returns_blank() {
        // OEM divergence: `applicationInfo.loadLabel(pm)` returns "" but the
        // sibling API `pm.getApplicationLabel(applicationInfo)` returns the
        // real label. Resolver must try both before falling back further.
        val source = FakePackageLabelSource(
            loadLabelReturns = mapOf(COUPANG_PACKAGE to ""),
            applicationLabelReturns = mapOf(COUPANG_PACKAGE to "쿠팡"),
        )

        val resolver = AppLabelResolver(source)

        assertEquals("쿠팡", resolver.resolve(COUPANG_PACKAGE))
    }

    @Test
    fun falls_back_to_getNameForUid_when_loadLabel_returns_packageName() {
        // Known degraded behaviour: `loadLabel` echoes the packageName itself
        // (the bug at the heart of Issue #503). Resolver must detect the
        // packageName==label collision and try `getNameForUid` next.
        val source = FakePackageLabelSource(
            loadLabelReturns = mapOf(HANA_PACKAGE to HANA_PACKAGE),
            applicationLabelReturns = mapOf(HANA_PACKAGE to HANA_PACKAGE),
            nameForUidReturns = mapOf(HANA_PACKAGE to "하나카드페이"),
        )

        val resolver = AppLabelResolver(source)

        assertEquals("하나카드페이", resolver.resolve(HANA_PACKAGE))
    }

    @Test
    fun returns_packageName_when_NameNotFoundException_and_getNameForUid_null() {
        // Final fallback: ApplicationInfo lookup throws (uninstalled
        // mid-flight, hidden, or PackageManager visibility blocked) AND
        // `getNameForUid` also has nothing to return. Resolver must NEVER
        // throw — UI gets the raw packageName so it degrades gracefully.
        val source = FakePackageLabelSource(
            throwNameNotFoundFor = setOf(YOUTUBE_PACKAGE),
            // nameForUidReturns intentionally omits YOUTUBE_PACKAGE → null
        )

        val resolver = AppLabelResolver(source)

        assertEquals(YOUTUBE_PACKAGE, resolver.resolve(YOUTUBE_PACKAGE))
    }

    @Test
    fun caught_ResourcesNotFoundException_in_loadLabel_advances_to_next_step() {
        // `applicationInfo.loadLabel(pm)` can throw ResourcesNotFoundException
        // when the app's APK resources are mid-update. Resolver must catch
        // that specific exception, advance to `pm.getApplicationLabel`, and
        // return its result.
        val source = FakePackageLabelSource(
            loadLabelThrowsResourcesNotFoundFor = setOf(LINKEDIN_PACKAGE),
            applicationLabelReturns = mapOf(LINKEDIN_PACKAGE to "LinkedIn"),
        )

        val resolver = AppLabelResolver(source)

        assertEquals("LinkedIn", resolver.resolve(LINKEDIN_PACKAGE))
    }

    private companion object {
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val COUPANG_PACKAGE = "com.coupang.mobile"
        const val HANA_PACKAGE = "com.hanacard.fr"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val LINKEDIN_PACKAGE = "com.linkedin.android"
    }
}
