package com.smartnoti.app.notification

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap

/**
 * Test-only fake for the `AppIconSource` port that Task 2 of plan
 * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
 * introduces alongside [AppIconResolver]. Mirrors [FakePackageLabelSource]
 * (sibling pattern from Issue #503 / PR #507) so the resolver's
 * resolution chain + cache invalidation contract can be exercised
 * step-by-step without subclassing Android's hundreds-of-abstract-method
 * [PackageManager] surface.
 *
 * Each entry-point is independently riggable:
 *
 *  - `loadIconReturns[pkg]`            — what `loadIcon` returns
 *                                        (null → resolver advances to the
 *                                        next chain step, NOT thrown)
 *  - `applicationIconReturns[pkg]`     — what the fallback
 *                                        `getApplicationIcon` returns
 *  - `throwNameNotFoundFor`            — packages that throw
 *                                        [PackageManager.NameNotFoundException]
 *                                        from `loadIcon` (uninstalled
 *                                        mid-flight / package visibility
 *                                        blocked)
 *  - `loadIconThrowsResourcesNotFoundFor` — packages whose `loadIcon`
 *                                            throws
 *                                            [Resources.NotFoundException]
 *                                            (mid-update APK)
 *  - `loadIconCallCounts`              — observable per-package call
 *                                        counter so the cache-hit /
 *                                        invalidate tests can assert how
 *                                        many round-trips the resolver
 *                                        actually made.
 *
 * Production wiring (Task 2 of the plan): a sibling
 * `AndroidAppIconSource` adapter that simply delegates to the real
 * [PackageManager] + `Drawable.toBitmap`. The fake here lets every test
 * in [AppIconResolverTest] stay pure-JVM (no Robolectric).
 */
internal class FakeAppIconSource(
    private val loadIconReturns: Map<String, Bitmap?> = emptyMap(),
    private val applicationIconReturns: Map<String, Bitmap?> = emptyMap(),
    private val throwNameNotFoundFor: Set<String> = emptySet(),
    private val loadIconThrowsResourcesNotFoundFor: Set<String> = emptySet(),
) : AppIconSource {

    private val _loadIconCallCounts = mutableMapOf<String, Int>()
    val loadIconCallCounts: Map<String, Int>
        get() = _loadIconCallCounts.toMap()

    override fun loadIcon(packageName: String): Bitmap? {
        _loadIconCallCounts[packageName] = (_loadIconCallCounts[packageName] ?: 0) + 1
        if (packageName in throwNameNotFoundFor) {
            throw PackageManager.NameNotFoundException(packageName)
        }
        if (packageName in loadIconThrowsResourcesNotFoundFor) {
            throw Resources.NotFoundException("loadIcon resources missing: $packageName")
        }
        return loadIconReturns[packageName]
    }

    override fun getApplicationIcon(packageName: String): Bitmap? {
        if (packageName in throwNameNotFoundFor && packageName !in applicationIconReturns) {
            return null
        }
        return applicationIconReturns[packageName]
    }
}
