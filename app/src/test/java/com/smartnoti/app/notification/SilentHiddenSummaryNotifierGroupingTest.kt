package com.smartnoti.app.notification

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.smartnoti.app.data.local.NotificationEntity
import com.smartnoti.app.domain.usecase.SilentGroupKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests-first for `silent-tray-sender-grouping` Task 2.
 *
 * These tests pin the Notifier's group summary + child posting behaviour:
 *  - group summary rides on the `smartnoti_silent_group` IMPORTANCE_MIN channel
 *  - children share the same `setGroup(...)` key as their summary
 *  - cancelling a group summary by `SilentGroupKey` removes exactly that summary
 *  - Sender and App group keys produce distinct group tags
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SilentHiddenSummaryNotifierGroupingTest {

    private lateinit var context: Context
    private lateinit var notifier: SilentHiddenSummaryNotifier
    private lateinit var systemNm: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier = SilentHiddenSummaryNotifier(context)
        systemNm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifier.ensureChannel()
    }

    private fun active(): List<android.service.notification.StatusBarNotification> {
        return shadowOf(systemNm).activeNotifications.toList()
    }

    @Test
    fun post_group_summary_creates_silent_group_channel_with_importance_min() {
        notifier.postGroupSummary(
            key = SilentGroupKey.Sender("엄마"),
            count = 2,
            preview = listOf(silentEntity("m1", "com.kakao.talk", "엄마", "잘 지내?")),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
        )

        val channel = systemNm.getNotificationChannel(SmartNotiNotifier.CHANNEL_SILENT_GROUP)
        assertNotNull("Silent group channel should be created", channel)
        assertEquals(NotificationManager.IMPORTANCE_MIN, channel!!.importance)
    }

    @Test
    fun post_group_summary_marks_notification_as_group_summary_with_stable_key() {
        val key = SilentGroupKey.Sender("엄마")

        notifier.postGroupSummary(
            key = key,
            count = 3,
            preview = listOf(
                silentEntity("m1", "com.kakao.talk", "엄마", "잘 지내?"),
                silentEntity("m2", "com.android.mms", "엄마", "답장해"),
            ),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
        )

        val active = active()
        assertEquals(1, active.size)
        val summary = active.single()
        assertTrue(
            "group summary flag should be set",
            (summary.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0,
        )
        assertEquals(SmartNotiNotifier.CHANNEL_SILENT_GROUP, summary.notification.channelId)
        assertEquals(SilentHiddenSummaryNotifier.groupTagFor(key), summary.notification.group)
    }

    @Test
    fun post_group_child_shares_group_tag_with_summary_and_uses_silent_group_channel() {
        val key = SilentGroupKey.Sender("엄마")
        notifier.postGroupSummary(
            key = key,
            count = 2,
            preview = emptyList(),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
        )

        notifier.postGroupChild(
            notificationId = 7_001L,
            entity = silentEntity("m1", "com.kakao.talk", "엄마", "잘 지내?"),
            key = key,
        )

        val active = active()
        assertEquals(2, active.size)
        val child = active.single { (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 }
        assertEquals(SmartNotiNotifier.CHANNEL_SILENT_GROUP, child.notification.channelId)
        assertEquals(SilentHiddenSummaryNotifier.groupTagFor(key), child.notification.group)
        @Suppress("DEPRECATION")
        val priority = child.notification.priority
        assertEquals(NotificationCompat.PRIORITY_MIN, priority)
    }

    @Test
    fun cancel_group_summary_removes_only_the_matching_summary() {
        val mom = SilentGroupKey.Sender("엄마")
        val promo = SilentGroupKey.Sender("광고팀")

        notifier.postGroupSummary(
            key = mom,
            count = 2,
            preview = emptyList(),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
        )
        notifier.postGroupSummary(
            key = promo,
            count = 3,
            preview = emptyList(),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
        )
        assertEquals(2, active().size)

        notifier.cancelGroupSummary(mom)

        val remaining = active()
        assertEquals(1, remaining.size)
        assertEquals(SilentHiddenSummaryNotifier.groupTagFor(promo), remaining.single().notification.group)
    }

    @Test
    fun sender_and_app_group_keys_produce_distinct_group_tags() {
        val senderTag = SilentHiddenSummaryNotifier.groupTagFor(SilentGroupKey.Sender("엄마"))
        val appTag = SilentHiddenSummaryNotifier.groupTagFor(SilentGroupKey.App("com.kakao.talk"))

        assertTrue(senderTag.startsWith("smartnoti_silent_group_"))
        assertTrue(appTag.startsWith("smartnoti_silent_group_"))
        assertTrue(
            "Sender vs App key collision would allow cross-pollination between messaging and app-only groups",
            senderTag != appTag,
        )
    }

    @Test
    fun post_group_summary_with_zero_count_cancels_existing_summary() {
        val key = SilentGroupKey.App("com.example.shop")
        notifier.postGroupSummary(
            key = key,
            count = 2,
            preview = emptyList(),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
        )
        assertEquals(1, active().size)

        notifier.postGroupSummary(
            key = key,
            count = 0,
            preview = emptyList(),
            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
        )

        assertEquals(0, active().size)
        // channel itself stays — we don't tear it down per group.
        assertNotNull(systemNm.getNotificationChannel(SmartNotiNotifier.CHANNEL_SILENT_GROUP))
    }

    @Test
    fun post_group_child_ensures_silent_group_channel_even_if_not_pre_created() {
        // Simulate a path where the channel was never pre-created by a prior post — the child
        // entry must still bring the channel up on its own.
        val fresh = SilentHiddenSummaryNotifier(context)
        val key = SilentGroupKey.Sender("엄마")

        fresh.postGroupChild(
            notificationId = 42L,
            entity = silentEntity("m1", "com.kakao.talk", "엄마", "잘 지내?"),
            key = key,
        )

        assertNotNull(
            "posting a child must lazily create the silent group channel so children are never orphaned",
            systemNm.getNotificationChannel(SmartNotiNotifier.CHANNEL_SILENT_GROUP),
        )
    }

    @Test
    fun legacy_post_still_goes_to_archived_summary_channel_not_the_group_channel() {
        // Regression guard: the pre-existing `post(count)` path must stay on the
        // `smartnoti_silent_summary` channel so Task 2 doesn't accidentally reroute the archived
        // summary into the group channel.
        notifier.post(count = 2)

        val active = active()
        assertEquals(1, active.size)
        val posted = active.single().notification
        assertEquals(SilentHiddenSummaryNotifier.CHANNEL_ID, posted.channelId)
        // And it must NOT be tagged with a SilentGroupKey-derived group tag. The implicit
        // `"silent"` group that setSilent(true) attaches is a platform concern, not our
        // per-sender tag.
        assertTrue(
            "archived summary must not be tagged with a SilentGroupKey-derived group tag",
            posted.group == null || !posted.group.startsWith("smartnoti_silent_group_"),
        )
    }

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
}
