package com.smartnoti.app.notification

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources

/**
 * Three-step port over [PackageManager]'s app-label surface, introduced by
 * plan
 * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
 * Task 2 to back the explicit fallback chain in [AppLabelResolver] without
 * coupling the unit tests to Android's hundreds-of-abstract-method
 * [PackageManager] surface.
 *
 * Each method maps 1:1 to a step the resolver tries in order. Any method MAY
 * throw [PackageManager.NameNotFoundException] when the underlying
 * `getApplicationInfo` lookup fails, and [Resources.NotFoundException] when
 * the resource resolution path inside `loadLabel` is broken (mid-update APK).
 * The resolver catches those exceptions and advances to the next step rather
 * than swallowing every [Exception] like the legacy
 * `resolveApplicationLabel` did.
 *
 * Returning `null` (vs. throwing) means "this step has no answer for this
 * package, try the next step" — used for genuinely empty / blank labels
 * without paying an exception allocation cost.
 *
 * Production wiring lives in [AndroidPackageLabelSource] which wraps a real
 * [PackageManager]. Test fakes (e.g. `FakePackageLabelSource` under
 * `app/src/test`) implement this directly so each chain step can be made to
 * return blank, return a packageName-shaped string, or throw independently.
 */
interface PackageLabelSource {
    /**
     * Step 1: `applicationInfo.loadLabel(pm).toString()`. May return blank
     * or echo back `packageName` on degraded OEM behaviour — the resolver
     * detects that and tries the next step.
     */
    fun loadLabel(packageName: String): String?

    /**
     * Step 2: `pm.getApplicationLabel(applicationInfo).toString()`. Some
     * OEMs return a different string from `loadLabel` (Hypothesis H1 in the
     * plan body), so this is genuinely a separate API path even though the
     * names sound similar.
     */
    fun getApplicationLabel(packageName: String): String?

    /**
     * Step 3: `pm.getNameForUid(applicationInfo.uid)`. Hypothesis H3:
     * Listener service runs with a system context whose PackageManager
     * mis-resolves user-installed app labels via the per-ApplicationInfo
     * path; the uid-keyed lookup is a different code path that sometimes
     * recovers.
     */
    fun getNameForUid(packageName: String): String?
}

/**
 * Production [PackageLabelSource] that delegates to a real [PackageManager].
 *
 * Each entry-point catches only [PackageManager.NameNotFoundException] (and
 * [Resources.NotFoundException] for the `loadLabel` resource path), then
 * rethrows so the resolver's per-step `try { } catch (e: NameNotFoundException) { }`
 * can advance to the next step. Other exceptions (security, permission)
 * propagate out — the resolver catches generic [RuntimeException] for those
 * cases as a wide-but-not-bare safety net.
 */
class AndroidPackageLabelSource(
    private val packageManager: PackageManager,
) : PackageLabelSource {

    override fun loadLabel(packageName: String): String? {
        val info = applicationInfoOrThrow(packageName)
        // `loadLabel` may itself throw Resources.NotFoundException when the
        // APK's resources are mid-update; let that propagate so the resolver
        // can catch it and advance to step 2.
        val label = info.loadLabel(packageManager).toString()
        return label.takeIf { it.isNotBlank() }
    }

    override fun getApplicationLabel(packageName: String): String? {
        val info = applicationInfoOrThrow(packageName)
        val label = packageManager.getApplicationLabel(info).toString()
        return label.takeIf { it.isNotBlank() }
    }

    override fun getNameForUid(packageName: String): String? {
        val info = applicationInfoOrThrow(packageName)
        return packageManager.getNameForUid(info.uid)?.takeIf { it.isNotBlank() }
    }

    private fun applicationInfoOrThrow(packageName: String): ApplicationInfo =
        packageManager.getApplicationInfo(packageName, 0)
}
