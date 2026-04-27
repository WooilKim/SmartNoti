package com.smartnoti.app.notification

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.util.LruCache

/**
 * Sibling of [AppLabelResolver] for the source-app **launcher icon**
 * surface. Introduced by plan
 * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
 * Task 2 to fix Issue #510 — SmartNoti's replacement notifier surface
 * (DIGEST replacement / SILENT group summary / SILENT group child) used
 * a single static SmartNoti drawable for `setSmallIcon` and never called
 * `setLargeIcon`, so users could not visually identify (a) which source
 * app the row originated from or (b) what action SmartNoti took. This
 * resolver supplies the `large = source app launcher icon` half of the
 * fix; the action half is handled by [ReplacementActionIcon].
 *
 * Resolution chain (each step's result discarded if `null`):
 *
 *   1. [AppIconSource.loadIcon]            — `applicationInfo.loadIcon(pm)`
 *   2. [AppIconSource.getApplicationIcon]  — `pm.getApplicationIcon(packageName)`
 *   3. final fallback                      — `null`
 *
 * Step 3 returning `null` (vs. a SmartNoti default brand bitmap) is a
 * deliberate UX choice (see plan "Product intent" §): an empty large
 * slot is more honest than mis-branding a tray row, and the action
 * small icon still identifies what SmartNoti did. The resolver NEVER
 * throws — every exception path either advances to the next step or
 * lands on the null fallback so the caller (notifier builder) never
 * crashes.
 *
 * Catch granularity (mirrors [AppLabelResolver] / Issue #503): the
 * resolver catches [PackageManager.NameNotFoundException] (uninstalled
 * mid-flight, hidden, or package visibility blocked),
 * [Resources.NotFoundException] (mid-update APK), and a wide-but-not-bare
 * [RuntimeException] (system-context permission edge cases). It does
 * NOT catch [Exception] whole — that was exactly Issue #503's bug.
 *
 * # Caching
 *
 * Per-package memoization via a bounded [LruCache] (cap defaults to 64
 * entries — sized for the typical 20–40 packages a user actually receives
 * notifications from, with headroom). Bitmaps are heavier than the label
 * strings cached by [AppLabelResolver] so the LRU bound matters here.
 * Invalidation is broadcast-driven — the listener service registers a
 * [android.content.BroadcastReceiver] for `Intent.ACTION_PACKAGE_REPLACED`
 * / `_ADDED` / `_REMOVED` and calls [invalidate] for the affected
 * package (or [clearAll] when the broadcast carries no package). Race
 * window: a notification arriving in the same millisecond as a package
 * upgrade may see the stale icon once; the next notification recovers.
 * Plan accepts this trade-off vs. the per-call PackageManager round-trip
 * cost.
 */
class AppIconResolver(
    private val source: AppIconSource,
    cacheCap: Int = DEFAULT_CACHE_CAP,
) {

    private val cache = LruCache<String, Bitmap>(cacheCap)

    /**
     * Resolve [packageName] to a launcher Bitmap using the explicit
     * fallback chain. Returns the cached value if present; otherwise
     * runs the chain and stores the result. Never throws. Returns
     * `null` when both chain steps have nothing — the caller (notifier
     * builder) treats null as "source has no displayable launcher icon"
     * and omits `setLargeIcon`.
     */
    fun resolve(packageName: String): Bitmap? {
        cache.get(packageName)?.let { return it }
        val resolved = runChain(packageName) ?: return null
        cache.put(packageName, resolved)
        return resolved
    }

    /**
     * Drop the cached entry for [packageName] so the next [resolve] call
     * re-runs the chain. No-op when the package was never resolved.
     *
     * Wired from the listener service's package-broadcast receiver so an
     * app upgrade that ships a new launcher icon is reflected on the
     * next replacement notification.
     */
    fun invalidate(packageName: String) {
        cache.remove(packageName)
    }

    /**
     * Drop every cached entry — used when a package broadcast does not
     * carry a packageName payload (rare) or when tests want a clean slate.
     */
    fun clearAll() {
        cache.evictAll()
    }

    private fun runChain(packageName: String): Bitmap? {
        // Step 1: applicationInfo.loadIcon(pm)
        val step1 = trySource { source.loadIcon(packageName) }
        if (step1 != null) return step1

        // Step 2: pm.getApplicationIcon(packageName) — separate API path
        // that on some OEMs returns a launcher icon even when step 1
        // returned null.
        val step2 = trySource { source.getApplicationIcon(packageName) }
        if (step2 != null) return step2

        // Step 3: null. Caller omits setLargeIcon — Product intent.
        return null
    }

    /**
     * Run [block] but classify exceptions per the catch granularity rules
     * (NameNotFoundException / ResourcesNotFoundException /
     * RuntimeException) and return null on any of them — the caller
     * treats null as "this step had no answer, try the next step".
     */
    private inline fun trySource(block: () -> Bitmap?): Bitmap? {
        return try {
            block()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: RuntimeException) {
            // Wide-but-not-bare safety net for system-context permission
            // edge cases. NOT `Exception` whole — that was Issue #503's
            // bug, not repeated here.
            null
        }
    }

    companion object {
        /**
         * Default LRU cap. Sized for the typical 20–40 packages a user
         * actually receives notifications from, with headroom for power
         * users. 64 × ~96×96 ARGB Bitmap ≈ 2.4 MB worst case — bounded
         * and reclaimable.
         */
        const val DEFAULT_CACHE_CAP = 64
    }
}
