package com.smartnoti.app.notification

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.graphics.drawable.toBitmap

/**
 * Two-step port over [PackageManager]'s app-icon surface, introduced by
 * plan
 * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
 * Task 2 to back the explicit fallback chain in [AppIconResolver] without
 * coupling the unit tests to Android's hundreds-of-abstract-method
 * [PackageManager] surface (mirrors [PackageLabelSource] from PR #507).
 *
 * Each method maps 1:1 to a step the resolver tries in order. Returning
 * `null` (vs. throwing) means "this step has no answer for this package,
 * try the next step" — used for genuinely empty / blank icons without
 * paying an exception allocation cost.
 *
 * Production wiring lives in [AndroidAppIconSource] which wraps a real
 * [PackageManager] + Drawable→Bitmap conversion. Test fakes (e.g.
 * `FakeAppIconSource` under `app/src/test`) implement this directly so
 * each chain step can be made to return a bitmap, return null, or throw
 * independently — keeping resolver tests pure-JVM (no Robolectric).
 */
/**
 * No-op [AppIconSource] used as a default for production constructors that
 * still take a resolver argument but where no real PackageManager wiring
 * exists yet (legacy callers, fakes in tests that don't care about icons).
 * Returns `null` for every package so the resolver chain falls through to
 * "omit setLargeIcon" — the safe, no-stale-data default.
 */
object NoOpAppIconSource : AppIconSource {
    override fun loadIcon(packageName: String): Bitmap? = null
    override fun getApplicationIcon(packageName: String): Bitmap? = null
}

interface AppIconSource {
    /**
     * Step 1: `pm.getApplicationInfo(packageName, 0).loadIcon(pm)` →
     * Drawable → Bitmap. Most common path. May throw
     * [PackageManager.NameNotFoundException] when the package was
     * uninstalled mid-flight or hidden by package-visibility rules, and
     * [Resources.NotFoundException] when the app's APK resources are
     * mid-update — the resolver catches both and advances to step 2.
     */
    fun loadIcon(packageName: String): Bitmap?

    /**
     * Step 2: `pm.getApplicationIcon(packageName)`. Some OEMs return a
     * different result from `loadIcon` for the same package, so this is
     * a genuinely separate API path even though the names sound similar.
     * Returns null when this code path has nothing either — the resolver
     * then degrades to a null result so the notifier omits `setLargeIcon`.
     */
    fun getApplicationIcon(packageName: String): Bitmap?
}

/**
 * Production [AppIconSource] that delegates to a real [PackageManager].
 *
 * Drawable → Bitmap conversion uses AndroidX core-ktx's
 * [androidx.core.graphics.drawable.toBitmap] which transparently handles
 * `BitmapDrawable` (fast path: returns the underlying bitmap) and
 * `AdaptiveIconDrawable` (compose foreground + background into a single
 * bitmap). [OutOfMemoryError] from the bitmap allocation is the only
 * `Error` we catch — a missing launcher icon is preferable to crashing
 * the listener service. Other exceptions propagate so the resolver's
 * per-step `try { } catch` advances the chain.
 */
class AndroidAppIconSource(
    private val packageManager: PackageManager,
) : AppIconSource {

    override fun loadIcon(packageName: String): Bitmap? {
        val info = packageManager.getApplicationInfo(packageName, 0)
        // `loadIcon` itself may throw Resources.NotFoundException when
        // the APK's resources are mid-update; let that propagate so the
        // resolver can catch it and advance to step 2.
        val drawable: Drawable = info.loadIcon(packageManager) ?: return null
        return drawable.toBitmapSafely()
    }

    override fun getApplicationIcon(packageName: String): Bitmap? {
        val drawable: Drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return drawable.toBitmapSafely()
    }

    /**
     * Convert [drawable] to a [Bitmap], picking sensible default dimensions
     * for icons that report zero intrinsic size (some
     * [AdaptiveIconDrawable] instances do until placed in a layout).
     * [OutOfMemoryError] is the only Error we swallow — large adaptive
     * icons can spike memory on low-end OEMs and a missing largeIcon is
     * preferable to crashing the listener service.
     */
    private fun Drawable.toBitmapSafely(): Bitmap? {
        return try {
            if (this is BitmapDrawable && bitmap != null) {
                bitmap
            } else {
                val width = intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_ICON_PX
                val height = intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_ICON_PX
                val isAdaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    this is AdaptiveIconDrawable
                val w = if (isAdaptive && width < DEFAULT_ICON_PX) DEFAULT_ICON_PX else width
                val h = if (isAdaptive && height < DEFAULT_ICON_PX) DEFAULT_ICON_PX else height
                toBitmap(w, h)
            }
        } catch (oom: OutOfMemoryError) {
            Log.w(TAG, "OOM converting drawable to bitmap; omitting largeIcon", oom)
            null
        }
    }

    private companion object {
        const val TAG = "AndroidAppIconSource"
        // ~96px square — a sensible default for tray largeIcon when the
        // drawable reports no intrinsic size. The system tray rescales
        // anyway; this only avoids the zero-dim Bitmap crash.
        const val DEFAULT_ICON_PX = 96
    }
}
