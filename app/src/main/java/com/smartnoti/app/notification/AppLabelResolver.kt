package com.smartnoti.app.notification

import android.content.pm.PackageManager
import android.content.res.Resources
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicit fallback chain for resolving an Android `packageName` to its
 * user-facing application label, introduced by plan
 * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
 * Task 2 to fix Issue #503 (16 popular packages — Gmail, 쿠팡, 하나카드페이,
 * 네이버, YouTube, LinkedIn, … — fell back to raw `packageName` because the
 * legacy single-step resolver inside
 * [SmartNotiNotificationListenerService.resolveApplicationLabel] swallowed
 * every [Exception] and bypassed all the recovery paths PackageManager
 * exposes).
 *
 * The chain — applied in order, each step's result discarded if it is blank
 * OR equals the input `packageName` (the H1 OEM degradation pattern at the
 * heart of the bug):
 *
 *   1. [PackageLabelSource.loadLabel]            — `applicationInfo.loadLabel(pm)`
 *   2. [PackageLabelSource.getApplicationLabel]  — `pm.getApplicationLabel(applicationInfo)`
 *   3. [PackageLabelSource.getNameForUid]        — `pm.getNameForUid(applicationInfo.uid)`
 *   4. final fallback                            — `packageName` itself
 *
 * The final step is a deliberate UX choice (see plan "Product intent" §): a
 * raw packageName is more honest than an empty string for the rare OEM
 * background-service packages that genuinely have no displayable label. The
 * resolver NEVER throws — every exception path either advances to the next
 * step or lands on the packageName fallback so the caller (UI render path)
 * never crashes.
 *
 * Catch granularity (Task 2 step 3 in the plan body): the resolver catches
 * [PackageManager.NameNotFoundException] (uninstalled mid-flight, hidden, or
 * package visibility blocked), [Resources.NotFoundException] (mid-update
 * APK), and a wide-but-not-bare [RuntimeException] (system-context
 * permission edge cases). It does NOT catch [Exception] whole — that was
 * exactly the bug.
 *
 * # Caching (Task 3)
 *
 * Per-package memoization via a [ConcurrentHashMap]: the first resolve for a
 * given packageName runs the full chain, every subsequent call hits the
 * cache. Invalidation is broadcast-driven — the listener service registers
 * a [android.content.BroadcastReceiver] for `Intent.ACTION_PACKAGE_REPLACED`
 * / `_ADDED` / `_REMOVED` and calls [invalidate] for the affected package
 * (or [clearAll] when the broadcast carries no package). Race window: a
 * notification arriving in the same millisecond as a package upgrade may
 * see the stale label once; the next notification recovers. Plan accepts
 * this trade-off vs. the per-call PackageManager round-trip cost.
 */
class AppLabelResolver(
    private val source: PackageLabelSource,
) {

    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Resolve [packageName] to a user-facing label using the explicit
     * fallback chain. Returns the cached value if present; otherwise runs
     * the chain and stores the result. Never throws.
     */
    fun resolve(packageName: String): String {
        cache[packageName]?.let { return it }
        val resolved = runChain(packageName)
        cache[packageName] = resolved
        return resolved
    }

    /**
     * Drop the cached entry for [packageName] so the next [resolve] call
     * re-runs the chain. No-op when the package was never resolved.
     *
     * Wired from the listener service's package-broadcast receiver
     * (`Intent.ACTION_PACKAGE_REPLACED` / `_ADDED` / `_REMOVED`) so an app
     * upgrade that ships a new label is reflected on the next notification.
     */
    fun invalidate(packageName: String) {
        cache.remove(packageName)
    }

    /**
     * Drop every cached entry — used when a package broadcast does not
     * carry a packageName payload (rare) or when tests want a clean slate.
     */
    fun clearAll() {
        cache.clear()
    }

    private fun runChain(packageName: String): String {
        // Step 1: applicationInfo.loadLabel(pm)
        val step1 = trySource(packageName) { source.loadLabel(packageName) }
        if (step1.isUsable(packageName)) return step1!!

        // Step 2: pm.getApplicationLabel(applicationInfo) — different API
        // path that on some OEMs returns a label even when step 1 echoed
        // packageName or returned blank.
        val step2 = trySource(packageName) { source.getApplicationLabel(packageName) }
        if (step2.isUsable(packageName)) return step2!!

        // Step 3: pm.getNameForUid(applicationInfo.uid) — last recovery
        // before the raw-packageName fallback.
        val step3 = trySource(packageName) { source.getNameForUid(packageName) }
        if (step3.isUsable(packageName)) return step3!!

        // Step 4: degrade to the raw packageName so the UI never renders an
        // empty string. Honest > pretty for the genuine no-label case.
        return packageName
    }

    /**
     * Run [block] but classify exceptions per the catch granularity rules
     * (NameNotFoundException / ResourcesNotFoundException / RuntimeException)
     * and return null on any of them — the caller treats null as "this step
     * had no answer, try the next step".
     */
    private inline fun trySource(@Suppress("UNUSED_PARAMETER") packageName: String, block: () -> String?): String? {
        return try {
            block()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: RuntimeException) {
            // Wide-but-not-bare safety net for system-context permission
            // edge cases. NOT `Exception` whole — that was the bug.
            null
        }
    }

    private fun String?.isUsable(packageName: String): Boolean =
        this != null && this.isNotBlank() && this != packageName
}
