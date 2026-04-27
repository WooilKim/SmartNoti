package com.smartnoti.app.notification

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Failing tests for plan
 * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
 * Task 1 (Issue #510).
 *
 * Pins the contract that the new [AppIconResolver] (Task 2) MUST honour:
 *
 *   1. `pm.getApplicationInfo(packageName, 0).loadIcon(pm)` returns a
 *      non-null Bitmap → use it.
 *   2. else `pm.getApplicationIcon(packageName)` (some OEMs return a
 *      different result from `loadIcon`).
 *   3. else `null` — caller (notifier builder) treats null as "source
 *      has no displayable launcher icon" and omits `setLargeIcon`. The
 *      resolver does NOT degrade to a SmartNoti brand bitmap (Product
 *      intent: an empty large slot is more honest than mis-branding the
 *      tray row, and the action-specific small icon still identifies
 *      what SmartNoti did).
 *
 * Per-package memoization via an LRU-style cache:
 *   - First resolve runs the chain → PackageManager hit.
 *   - Subsequent resolves return the cached Bitmap → 0 PackageManager
 *     hits.
 *   - `invalidate(pkg)` evicts one entry; the next `resolve` re-runs
 *     the chain. Wired from the listener service's package-broadcast
 *     receiver (Task 4) so an app upgrade that ships a new launcher
 *     icon is reflected on the next replacement notification.
 *
 * Resolver MUST NOT swallow `Exception` whole — only
 * [android.content.pm.PackageManager.NameNotFoundException] /
 * [android.content.res.Resources.NotFoundException] / `RuntimeException`
 * (system-context permission edge cases) are caught, mirroring the
 * [AppLabelResolver] catch-granularity established by Issue #503 / PR #507.
 *
 * These tests are RED on `main` because [AppIconResolver] and its
 * companion source-port type `AppIconSource` do not exist yet — Task 2
 * introduces them. Compile failure on those symbols IS the intended RED
 * signal — see the plan "Files (new)" list (Task 2) and the plan
 * Task 1 carve-out that compile errors count as RED for the
 * failing-test gate.
 *
 * Tests construct [AppIconResolver] against a hand-rolled
 * [FakeAppIconSource] port so each step of the chain can be made to
 * return null, return a bitmap, or throw independently — keeping
 * Task 1 a pure-JVM test (no Robolectric) and letting Task 2 ship the
 * production [android.content.pm.PackageManager]-backed adapter without
 * coupling test fakery to Android's abstract-class surface area.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppIconResolverTest {

    @Test
    fun resolves_source_icon_when_loadIcon_returns_bitmap() {
        val coupangIcon = bitmap()
        val source = FakeAppIconSource(
            loadIconReturns = mapOf(COUPANG_PACKAGE to coupangIcon),
        )

        val resolver = AppIconResolver(source)

        assertSame(coupangIcon, resolver.resolve(COUPANG_PACKAGE))
    }

    @Test
    fun falls_back_to_getApplicationIcon_when_loadIcon_returns_null() {
        // OEM divergence: `applicationInfo.loadIcon(pm)` returns null but
        // the sibling API `pm.getApplicationIcon(packageName)` returns
        // the real launcher icon. Resolver must try both before giving
        // up.
        val gmailIcon = bitmap()
        val source = FakeAppIconSource(
            loadIconReturns = mapOf(GMAIL_PACKAGE to null),
            applicationIconReturns = mapOf(GMAIL_PACKAGE to gmailIcon),
        )

        val resolver = AppIconResolver(source)

        assertSame(gmailIcon, resolver.resolve(GMAIL_PACKAGE))
    }

    @Test
    fun returns_null_when_both_chain_steps_have_nothing() {
        // Genuine no-launcher-icon case (system service / plugin /
        // disabled). Resolver returns null so the notifier omits
        // `setLargeIcon` rather than falsely branding the tray row with
        // a SmartNoti default icon (Product intent decision in the plan).
        val source = FakeAppIconSource(
            loadIconReturns = mapOf(SYSTEM_PACKAGE to null),
            applicationIconReturns = mapOf(SYSTEM_PACKAGE to null),
        )

        val resolver = AppIconResolver(source)

        assertNull(resolver.resolve(SYSTEM_PACKAGE))
    }

    @Test
    fun returns_null_when_NameNotFoundException_thrown() {
        // Uninstalled mid-flight, hidden, or PackageManager visibility
        // blocked. Resolver MUST NOT throw — UI gets null so it
        // gracefully omits the large icon and the action small icon
        // still identifies what SmartNoti did.
        val source = FakeAppIconSource(
            throwNameNotFoundFor = setOf(YOUTUBE_PACKAGE),
        )

        val resolver = AppIconResolver(source)

        assertNull(resolver.resolve(YOUTUBE_PACKAGE))
    }

    @Test
    fun caught_ResourcesNotFoundException_in_loadIcon_advances_to_next_step() {
        // `applicationInfo.loadIcon(pm)` can throw
        // ResourcesNotFoundException when the app's APK resources are
        // mid-update. Resolver must catch that specific exception,
        // advance to `pm.getApplicationIcon`, and return its result.
        val linkedinIcon = bitmap()
        val source = FakeAppIconSource(
            loadIconThrowsResourcesNotFoundFor = setOf(LINKEDIN_PACKAGE),
            applicationIconReturns = mapOf(LINKEDIN_PACKAGE to linkedinIcon),
        )

        val resolver = AppIconResolver(source)

        assertSame(linkedinIcon, resolver.resolve(LINKEDIN_PACKAGE))
    }

    @Test
    fun second_resolve_for_same_package_hits_cache_without_calling_source() {
        // Cache contract: first resolve runs the chain, subsequent
        // resolves return the cached Bitmap without touching the
        // PackageManager port. Cuts the per-notification IPC cost on
        // the hot path.
        val coupangIcon = bitmap()
        val source = FakeAppIconSource(
            loadIconReturns = mapOf(COUPANG_PACKAGE to coupangIcon),
        )
        val resolver = AppIconResolver(source)

        val first = resolver.resolve(COUPANG_PACKAGE)
        val second = resolver.resolve(COUPANG_PACKAGE)

        assertSame(coupangIcon, first)
        assertSame("Cache hit must return the exact same Bitmap instance", first, second)
        assertEquals(
            "PackageManager port should be called once for two resolves of the same package",
            1,
            source.loadIconCallCounts[COUPANG_PACKAGE] ?: 0,
        )
    }

    @Test
    fun invalidate_evicts_one_entry_and_next_resolve_reruns_chain() {
        // Wired from the listener service's package-broadcast receiver
        // (Task 4) so an app upgrade that ships a new launcher icon is
        // reflected on the next replacement notification.
        val firstIcon = bitmap()
        val secondIcon = bitmap()
        val rigged = MutableLoadIconReturns(mapOf(GMAIL_PACKAGE to firstIcon))
        val source = TrackingAppIconSource(rigged)
        val resolver = AppIconResolver(source)

        assertSame(firstIcon, resolver.resolve(GMAIL_PACKAGE))
        rigged.set(GMAIL_PACKAGE, secondIcon)
        // Without invalidate the resolver would still return the cached
        // first icon — sanity-check that assumption first so the
        // invalidate assertion below is meaningful.
        assertSame(
            "Without invalidate, cache must still return the original icon",
            firstIcon,
            resolver.resolve(GMAIL_PACKAGE),
        )

        resolver.invalidate(GMAIL_PACKAGE)

        val refreshed = resolver.resolve(GMAIL_PACKAGE)
        assertSame("After invalidate, resolver must re-fetch from the source", secondIcon, refreshed)
        assertNotSame("Refreshed icon must not be the original cached instance", firstIcon, refreshed)
        assertEquals(
            "Source should have been called twice — once before, once after invalidate",
            2,
            source.callCounts[GMAIL_PACKAGE] ?: 0,
        )
    }

    @Test
    fun clearAll_evicts_every_entry() {
        // Used when a package broadcast does not carry a packageName
        // payload (rare) or when tests want a clean slate.
        val source = FakeAppIconSource(
            loadIconReturns = mapOf(
                COUPANG_PACKAGE to bitmap(),
                GMAIL_PACKAGE to bitmap(),
            ),
        )
        val resolver = AppIconResolver(source)

        resolver.resolve(COUPANG_PACKAGE)
        resolver.resolve(GMAIL_PACKAGE)
        resolver.clearAll()
        resolver.resolve(COUPANG_PACKAGE)
        resolver.resolve(GMAIL_PACKAGE)

        assertEquals(
            "clearAll should force a second source call for every package",
            2,
            source.loadIconCallCounts[COUPANG_PACKAGE] ?: 0,
        )
        assertEquals(
            "clearAll should force a second source call for every package",
            2,
            source.loadIconCallCounts[GMAIL_PACKAGE] ?: 0,
        )
    }

    /**
     * Test-only mutable wrapper so a single fake source can change its
     * `loadIcon` answer between resolver calls (used to verify cache
     * invalidation surfaces the new bitmap, not the stale one).
     */
    private class MutableLoadIconReturns(initial: Map<String, Bitmap?>) {
        private val map = initial.toMutableMap()
        fun get(pkg: String): Bitmap? = map[pkg]
        fun set(pkg: String, value: Bitmap?) {
            map[pkg] = value
        }
    }

    private class TrackingAppIconSource(
        private val rigged: MutableLoadIconReturns,
    ) : AppIconSource {
        private val _callCounts = mutableMapOf<String, Int>()
        val callCounts: Map<String, Int> get() = _callCounts.toMap()

        override fun loadIcon(packageName: String): Bitmap? {
            _callCounts[packageName] = (_callCounts[packageName] ?: 0) + 1
            return rigged.get(packageName)
        }

        override fun getApplicationIcon(packageName: String): Bitmap? = null
    }

    /**
     * Returns a tiny ARGB_8888 Bitmap suitable for identity-only
     * (`assertSame`) assertions. Robolectric is NOT on this test's
     * classpath, so we rely on `Bitmap.createBitmap` being a real call
     * even under the stubbed `android.jar` — pure-JVM tests in
     * SmartNoti's test classpath already exercise this surface (see
     * the Compose / Robolectric carve-outs in `app/build.gradle.kts`).
     * If this stub-jar limitation surfaces during Task 2 GREEN, the
     * fake source can be migrated to a `Bitmap` mock via Mockito or to
     * a Robolectric-annotated test class — neither changes the
     * resolver contract being pinned here.
     */
    private fun bitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private companion object {
        const val COUPANG_PACKAGE = "com.coupang.mobile"
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val LINKEDIN_PACKAGE = "com.linkedin.android"
        const val SYSTEM_PACKAGE = "com.android.systemservice"
    }
}
