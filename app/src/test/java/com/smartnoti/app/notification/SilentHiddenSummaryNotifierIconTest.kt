package com.smartnoti.app.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.R
import com.smartnoti.app.data.local.NotificationEntity
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.usecase.SilentGroupKey
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
 * Pins the contract that [SilentHiddenSummaryNotifier]'s three
 * `setSmallIcon(android.R.drawable.ic_menu_view)` sites
 * (legacy archived summary `post`, group-summary `postGroupSummary`,
 * group-child `postGroupChild`) MUST honour after Task 4 wires in
 * [AppIconResolver] + [ReplacementActionIcon]:
 *
 *  - **SILENT group child, single source**: child notification carries
 *    the source app's launcher icon as `largeIcon` and
 *    `R.drawable.ic_replacement_silent` as the small-icon resource.
 *    One row, two glyphs: "쿠팡 (large) + SILENT (small)".
 *  - **SILENT group summary, mixed sources**: summary notification
 *    OMITS `largeIcon` (children come from 2+ different packages — no
 *    single source can fairly represent the group). Small icon stays
 *    `R.drawable.ic_replacement_silent` so the action is still
 *    identified.
 *  - **SILENT group summary, single source**: when every child shares
 *    the same packageName, the summary may carry that source's
 *    launcher icon as `largeIcon` (Product intent: a homogeneous
 *    group's source is unambiguous). The current Task 1 contract pins
 *    the mixed-source omission only — the single-source path is left
 *    as Task 4 wiring detail and is not asserted here, so this test
 *    file does not over-constrain Task 4.
 *  - **Legacy archived `post`**: action is SILENT (the archived inbox
 *    bell), so small icon must be `R.drawable.ic_replacement_silent`.
 *    No source app context exists at this call-site (the archived
 *    summary aggregates across all SILENT items in the inbox), so
 *    `largeIcon` MUST be omitted.
 *
 * These tests are RED on `main` because:
 *  1. [SilentHiddenSummaryNotifier] does NOT yet accept
 *     `appIconResolver` in its constructor — Task 4 adds it.
 *     Compile failure on the construction site is part of the
 *     intended RED signal.
 *  2. The notifier currently calls `setSmallIcon(android.R.drawable.ic_menu_view)`
 *     at lines 53 / 149 / 209 — the asserted resource id
 *     `R.drawable.ic_replacement_silent` does not yet exist in
 *     `app/src/main/res/drawable/` (Task 3 adds it).
 *  3. The notifier currently never calls `setLargeIcon` — Task 4 adds
 *     the wiring; the largeIcon-bitmap assertions surface the missing
 *     wiring once the resource ids and resolver type compile.
 *
 * Mirror file [SmartNotiNotifierIconTest] covers the
 * [SmartNotiNotifier.notifySuppressedNotification] DIGEST + SILENT
 * replacement paths.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SilentHiddenSummaryNotifierIconTest {

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
    fun group_child_single_source_carries_source_large_icon_and_silent_small_icon() {
        val coupangIcon = bitmap()
        val resolver = AppIconResolver(
            FakeAppIconSource(loadIconReturns = mapOf(COUPANG_PACKAGE to coupangIcon)),
        )
        val notifier = SilentHiddenSummaryNotifier(
            context = context,
            appIconResolver = resolver,
        )
        val key = SilentGroupKey.App(COUPANG_PACKAGE)

        notifier.postGroupChild(
            notificationId = 9_001L,
            entity = silentEntity("c1", COUPANG_PACKAGE, "쿠팡", "주문 상태 안내"),
            key = key,
            settings = testSettings,
        )

        val posted = singleChild().notification
        assertEquals(
            "SILENT group child small icon must be ic_replacement_silent",
            R.drawable.ic_replacement_silent,
            posted.smallIcon.resId,
        )
        // NotificationCompat.Builder.setLargeIcon(Bitmap) wraps the
        // bitmap into an IconCompat → Icon and the system parcels it
        // through `notify(...)`, so identity is not preserved on API 23+
        // even though the production builder calls setLargeIcon with the
        // exact bitmap the resolver returned. `Bitmap.sameAs` checks
        // pixel-by-pixel equivalence which is what the contract actually
        // wants — "the bitmap surfaced in the tray equals the resolver's
        // output, not some default substitute".
        val largeIcon = posted.getLargeIconBitmap()
        assertNotNull("SILENT group child must set largeIcon", largeIcon)
        assertTrue(
            "SILENT group child largeIcon pixels must match the source app launcher bitmap",
            coupangIcon.sameAs(largeIcon),
        )
    }

    @Test
    fun group_summary_with_mixed_sources_omits_large_icon_but_keeps_silent_small_icon() {
        // Children come from two different packages → no single source
        // can fairly represent the group. Product intent: omit the
        // large icon (do not pick one source as the "winner") but keep
        // the SILENT small icon so the action is still identified.
        val coupangIcon = bitmap()
        val gmailIcon = bitmap()
        val resolver = AppIconResolver(
            FakeAppIconSource(
                loadIconReturns = mapOf(
                    COUPANG_PACKAGE to coupangIcon,
                    GMAIL_PACKAGE to gmailIcon,
                ),
            ),
        )
        val notifier = SilentHiddenSummaryNotifier(
            context = context,
            appIconResolver = resolver,
        )
        val mixedKey = SilentGroupKey.Sender("엄마")

        notifier.postGroupSummary(
            key = mixedKey,
            count = 2,
            preview = listOf(
                silentEntity("m1", COUPANG_PACKAGE, "엄마", "잘 지내?"),
                silentEntity("m2", GMAIL_PACKAGE, "엄마", "답장해"),
            ),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
            settings = testSettings,
        )

        val posted = singleSummary().notification
        assertEquals(
            "SILENT summary small icon must be ic_replacement_silent even when sources are mixed",
            R.drawable.ic_replacement_silent,
            posted.smallIcon.resId,
        )
        assertNull(
            "SILENT summary with 2+ source packages MUST omit largeIcon (no fair single-source representative)",
            posted.getLargeIconBitmap(),
        )
    }

    @Test
    fun legacy_archived_summary_post_uses_silent_small_icon_and_omits_large_icon() {
        // The archived inbox bell aggregates across every SILENT item
        // in the inbox — there is no single source to represent.
        // Small icon stays SILENT; large icon is omitted.
        val resolver = AppIconResolver(FakeAppIconSource())
        val notifier = SilentHiddenSummaryNotifier(
            context = context,
            appIconResolver = resolver,
        )

        notifier.post(count = 3, settings = testSettings)

        val posted = singleSummary().notification
        assertEquals(
            "Legacy archived summary small icon must be ic_replacement_silent",
            R.drawable.ic_replacement_silent,
            posted.smallIcon.resId,
        )
        assertNull(
            "Archived summary aggregates across SILENT inbox — MUST omit largeIcon",
            posted.getLargeIconBitmap(),
        )
    }

    private fun activeSorted(): List<StatusBarNotification> =
        shadowOf(systemNm).activeNotifications.toList()

    private fun singleChild(): StatusBarNotification {
        val children = activeSorted().filter {
            (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0
        }
        assertEquals(1, children.size)
        return children.single()
    }

    private fun singleSummary(): StatusBarNotification {
        val active = activeSorted()
        assertEquals(1, active.size)
        val posted = active.single()
        assertNotNull(posted)
        return posted
    }

    /**
     * See `SmartNotiNotifierIconTest.getLargeIconBitmap` for the rationale.
     * On API 23+ AndroidX `NotificationCompat.Builder.setLargeIcon(Bitmap)`
     * wraps the bitmap into `IconCompat` and the legacy `largeIcon` field
     * is null; the bitmap is reachable only through `getLargeIcon()` →
     * `Icon.getBitmap()`.
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
        return runCatching {
            val m = android.graphics.drawable.Icon::class.java.getMethod("getBitmap")
            m.invoke(icon) as? Bitmap
        }.getOrNull()
    }

    private fun bitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private fun silentEntity(
        id: String,
        packageName: String,
        sender: String?,
        body: String,
    ): NotificationEntity = NotificationEntity(
        id = id,
        appName = packageName,
        packageName = packageName,
        sender = sender,
        title = sender ?: "알림",
        body = body,
        postedAtMillis = 1_700_000_000_000L,
        status = "SILENT",
        reasonTags = "",
        score = null,
        isBundled = false,
        isPersistent = false,
        contentSignature = "$packageName|$id",
    )

    private companion object {
        const val COUPANG_PACKAGE = "com.coupang.mobile"
        const val GMAIL_PACKAGE = "com.google.android.gm"
    }
}
