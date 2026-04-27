package com.smartnoti.app.notification

import android.content.pm.PackageManager
import android.content.res.Resources

/**
 * Test-only fake for the [PackageLabelSource] port that Task 2 of plan
 * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
 * introduces alongside [AppLabelResolver].
 *
 * Each entry-point method is independently riggable so the resolver's
 * fallback chain can be exercised step-by-step:
 *
 *  - `loadLabelReturns[pkg]`           — what `loadLabel` returns (null → not in map)
 *  - `applicationLabelReturns[pkg]`    — what `getApplicationLabel` returns
 *  - `nameForUidReturns[pkg]`          — what `getNameForUid` returns for the
 *                                        uid bound to `pkg`
 *  - `throwNameNotFoundFor`            — packages that throw [PackageManager.NameNotFoundException]
 *                                        from `loadApplicationInfo`
 *  - `loadLabelThrowsResourcesNotFoundFor` — packages whose `loadLabel`
 *                                            throws [Resources.NotFoundException]
 *
 * Production wiring (Task 2): a sibling `PackageManagerPackageLabelSource`
 * adapter that simply delegates to the real `PackageManager`. The fake here
 * lets every test in [AppLabelResolverTest] stay pure-JVM (no Robolectric)
 * and avoids subclassing Android's hundreds-of-abstract-method
 * [PackageManager] surface.
 */
internal class FakePackageLabelSource(
    private val loadLabelReturns: Map<String, String?> = emptyMap(),
    private val applicationLabelReturns: Map<String, String?> = emptyMap(),
    private val nameForUidReturns: Map<String, String?> = emptyMap(),
    private val throwNameNotFoundFor: Set<String> = emptySet(),
    private val loadLabelThrowsResourcesNotFoundFor: Set<String> = emptySet(),
) : PackageLabelSource {

    override fun loadLabel(packageName: String): String? {
        if (packageName in throwNameNotFoundFor) {
            throw PackageManager.NameNotFoundException(packageName)
        }
        if (packageName in loadLabelThrowsResourcesNotFoundFor) {
            throw Resources.NotFoundException("loadLabel resources missing: $packageName")
        }
        return loadLabelReturns[packageName]
    }

    override fun getApplicationLabel(packageName: String): String? {
        if (packageName in throwNameNotFoundFor) {
            throw PackageManager.NameNotFoundException(packageName)
        }
        return applicationLabelReturns[packageName]
    }

    override fun getNameForUid(packageName: String): String? {
        if (packageName in throwNameNotFoundFor && packageName !in nameForUidReturns) {
            return null
        }
        return nameForUidReturns[packageName]
    }
}
