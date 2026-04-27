package com.smartnoti.app.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.R
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Failing tests for plan
 * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
 * Task 1 (Issue #510).
 *
 * Pins the contract that [SmartNotiNotifier.notifySuppressedNotification]
 * MUST honour after Task 4 wires in [AppIconResolver] +
 * [ReplacementActionIcon]:
 *
 *  - **DIGEST replacement, source resolves**: notification carries the
 *    source app's launcher icon as `largeIcon` (bitmap returned by
 *    [AppIconResolver.resolve]) and `R.drawable.ic_replacement_digest`
 *    as the small-icon resource id. One row, two glyphs, two facts:
 *    "쿠팡 (large) + DIGEST (small)" — exactly the user request in
 *    Issue #510.
 *  - **SILENT replacement, source resolves**: same shape but with
 *    `R.drawable.ic_replacement_silent` as the small-icon resource.
 *    (Note: SILENT 라우팅의 group summary / child 표시는
 *    [SilentHiddenSummaryNotifier] 가 담당하고
 *    [SilentHiddenSummaryNotifierIconTest] 가 그 contract 를 핀다.
 *    여기서는 `notifySuppressedNotification(SILENT, …)` 의 fall-through
 *    경로 — replacement alert 자체 — 만 다룬다.)
 *  - **Source resolver returns null**: notifier MUST NOT call
 *    `setLargeIcon` (Product intent: an empty large slot is more
 *    honest than mis-branding the tray row with a SmartNoti default
 *    icon, and the action small icon still identifies what SmartNoti
 *    did).
 *
 * These tests are RED on `main` because:
 *  1. [SmartNotiNotifier] does NOT yet accept `appIconResolver` in its
 *     constructor — Task 4 adds it. Compile failure on the construction
 *     site is part of the intended RED signal.
 *  2. The notifier currently calls `setSmallIcon(android.R.drawable.ic_dialog_info)`
 *     (line 82) — the asserted resource id `R.drawable.ic_replacement_digest`
 *     does not yet exist in `app/src/main/res/drawable/` (Task 3 adds it).
 *  3. The notifier currently never calls `setLargeIcon` — Task 4 adds
 *     the wiring; the largeIcon-bitmap assertions surface the missing
 *     wiring once the resource ids and resolver type compile.
 *
 * All three are consistent with the plan Task 1 carve-out: compile
 * errors and runtime mismatches both count as RED for the
 * failing-test gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmartNotiNotifierIconTest {

    private lateinit var context: Context
    private lateinit var systemNm: NotificationManager
    private val testSettings = SmartNotiSettings()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        systemNm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun digest_replacement_carries_source_large_icon_and_digest_small_icon() {
        val coupangIcon = bitmap()
        val resolver = AppIconResolver(
            FakeAppIconSource(loadIconReturns = mapOf(COUPANG_PACKAGE to coupangIcon)),
        )
        val notifier = SmartNotiNotifier(
            context = context,
            appIconResolver = resolver,
        )

        notifier.notifySuppressedNotification(
            decision = NotificationDecision.DIGEST,
            packageName = COUPANG_PACKAGE,
            appName = "쿠팡",
            title = "주문 도착",
            body = "주문하신 상품이 곧 도착합니다",
            notificationId = "n-digest-1",
            reasonTags = listOf("noise"),
            settings = testSettings,
        )

        val posted = singlePosted().notification
        assertEquals(
            "DIGEST replacement small icon must be ic_replacement_digest",
            R.drawable.ic_replacement_digest,
            posted.smallIcon.resId,
        )
        val largeIcon = posted.getLargeIconBitmap()
        assertNotNull(
            "DIGEST replacement must set largeIcon to the source app launcher bitmap",
            largeIcon,
        )
        // NotificationCompat.Builder.setLargeIcon(Bitmap) wraps the
        // bitmap into an IconCompat → Icon and the system parcels it
        // through `notify(...)`, so identity is not preserved on API 23+
        // even though the production builder calls setLargeIcon with the
        // exact bitmap the resolver returned. `Bitmap.sameAs` checks
        // pixel-by-pixel equivalence which is what the contract actually
        // wants — "the bitmap surfaced in the tray equals the resolver's
        // output, not some default substitute".
        assertTrue(
            "DIGEST replacement largeIcon pixels must match the AppIconResolver bitmap",
            coupangIcon.sameAs(largeIcon),
        )
    }

    @Test
    fun silent_replacement_carries_source_large_icon_and_silent_small_icon() {
        val gmailIcon = bitmap()
        val resolver = AppIconResolver(
            FakeAppIconSource(loadIconReturns = mapOf(GMAIL_PACKAGE to gmailIcon)),
        )
        val notifier = SmartNotiNotifier(
            context = context,
            appIconResolver = resolver,
        )

        notifier.notifySuppressedNotification(
            decision = NotificationDecision.SILENT,
            packageName = GMAIL_PACKAGE,
            appName = "Gmail",
            title = "프로모션 메일",
            body = "이번 주 할인 상품 안내",
            notificationId = "n-silent-1",
            reasonTags = listOf("promo"),
            settings = testSettings,
        )

        val posted = singlePosted().notification
        assertEquals(
            "SILENT replacement small icon must be ic_replacement_silent",
            R.drawable.ic_replacement_silent,
            posted.smallIcon.resId,
        )
        // See `digest_replacement_…` for the rationale on `sameAs` vs
        // `assertSame`.
        val largeIcon = posted.getLargeIconBitmap()
        assertNotNull(
            "SILENT replacement must set largeIcon to the source app launcher bitmap",
            largeIcon,
        )
        assertTrue(
            "SILENT replacement largeIcon pixels must match the AppIconResolver bitmap",
            gmailIcon.sameAs(largeIcon),
        )
    }

    @Test
    fun replacement_omits_largeIcon_when_resolver_returns_null() {
        // Genuine no-launcher-icon case (system service / plugin /
        // disabled). Notifier MUST NOT call setLargeIcon — Product
        // intent: an empty large slot is more honest than falsely
        // branding the tray row with a SmartNoti default icon.
        val resolver = AppIconResolver(
            FakeAppIconSource(
                loadIconReturns = mapOf(SYSTEM_PACKAGE to null),
                applicationIconReturns = mapOf(SYSTEM_PACKAGE to null),
            ),
        )
        val notifier = SmartNotiNotifier(
            context = context,
            appIconResolver = resolver,
        )

        notifier.notifySuppressedNotification(
            decision = NotificationDecision.DIGEST,
            packageName = SYSTEM_PACKAGE,
            appName = SYSTEM_PACKAGE,
            title = "system",
            body = "background service notice",
            notificationId = "n-sys-1",
            reasonTags = emptyList(),
            settings = testSettings,
        )

        val posted = singlePosted().notification
        assertEquals(
            "Even with no large icon, small icon must still identify the action (DIGEST)",
            R.drawable.ic_replacement_digest,
            posted.smallIcon.resId,
        )
        assertNull(
            "Resolver returned null → notifier MUST omit setLargeIcon (no SmartNoti-brand fallback)",
            posted.getLargeIconBitmap(),
        )
    }

    private fun singlePosted(): StatusBarNotification {
        val active = shadowOf(systemNm).activeNotifications.toList()
        assertEquals(1, active.size)
        return active.single()
    }

    /**
     * Reads back the bitmap that `setLargeIcon(Bitmap)` stored on the
     * notification. On API 23+ AndroidX
     * `NotificationCompat.Builder.setLargeIcon(Bitmap)` wraps the bitmap
     * into an `IconCompat` and emits it via `Notification.Builder
     * .setLargeIcon(Icon)`, so the legacy
     * `Notification.largeIcon` field is null and the bitmap is reachable
     * only through `notification.getLargeIcon()` → `Icon.getBitmap()`.
     * The helper checks the legacy field first (covers older API paths
     * exercised by other tests) then falls through to the Icon path.
     * Returning null distinguishes "notifier omitted setLargeIcon" from
     * "notifier called it with a null bitmap" (the latter is impossible
     * because the production builder ignores null inputs to
     * `setLargeIcon`).
     */
    @Suppress("DEPRECATION")
    private fun android.app.Notification.getLargeIconBitmap(): Bitmap? {
        this.largeIcon?.let { return it }
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return null
        val icon = this.getLargeIcon() ?: return null
        if (icon.type != android.graphics.drawable.Icon.TYPE_BITMAP &&
            icon.type != android.graphics.drawable.Icon.TYPE_ADAPTIVE_BITMAP
        ) {
            return null
        }
        // Icon.getBitmap() is available since API 23 but @hide; resort to
        // reflection so the test stays on the public surface w.r.t. lint.
        return runCatching {
            val m = android.graphics.drawable.Icon::class.java.getMethod("getBitmap")
            m.invoke(icon) as? Bitmap
        }.getOrNull()
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private companion object {
        const val COUPANG_PACKAGE = "com.coupang.mobile"
        const val GMAIL_PACKAGE = "com.google.android.gm"
        const val SYSTEM_PACKAGE = "com.android.systemservice"
    }
}
