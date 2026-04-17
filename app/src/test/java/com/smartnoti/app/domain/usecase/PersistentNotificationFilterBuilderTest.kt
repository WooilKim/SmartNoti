package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentNotificationFilterBuilderTest {

    private val builder = PersistentNotificationFilterBuilder()

    @Test
    fun excludes_persistent_notifications_when_hide_option_enabled() {
        val notifications = listOf(
            notification(id = "1", isPersistent = true),
            notification(id = "2", isPersistent = false),
        )

        val result = builder.filter(
            notifications = notifications,
            hidePersistentNotifications = true,
        )

        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun keeps_all_notifications_when_hide_option_disabled() {
        val notifications = listOf(
            notification(id = "1", isPersistent = true),
            notification(id = "2", isPersistent = false),
        )

        val result = builder.filter(
            notifications = notifications,
            hidePersistentNotifications = false,
        )

        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun excludes_persistent_items_from_digest_groups_when_hide_option_enabled() {
        val groups = listOf(
            DigestGroupUiModel(
                id = "digest:system",
                appName = "시스템 UI",
                count = 2,
                summary = "시스템 UI 관련 알림 2건",
                items = listOf(
                    notification(id = "1", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
                    notification(id = "2", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
                ),
            ),
            DigestGroupUiModel(
                id = "digest:shopping",
                appName = "쿠팡",
                count = 2,
                summary = "쿠팡 관련 알림 2건",
                items = listOf(
                    notification(id = "3", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = false),
                    notification(id = "4", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = true),
                ),
            ),
        )

        val result = builder.filterDigestGroups(
            groups = groups,
            hidePersistentNotifications = true,
        )

        assertEquals(1, result.size)
        assertEquals("digest:shopping", result.single().id)
        assertEquals(1, result.single().count)
        assertEquals(listOf("3"), result.single().items.map { it.id })
        assertEquals("쿠팡 관련 알림 1건", result.single().summary)
    }

    @Test
    fun recomputes_captured_app_counts_from_visible_notifications_when_hide_option_enabled() {
        val capturedApps = listOf(
            CapturedAppSelectionItem(
                packageName = "android.system",
                appName = "시스템 UI",
                notificationCount = 3,
                lastSeenLabel = "방금",
            ),
            CapturedAppSelectionItem(
                packageName = "com.coupang.mobile",
                appName = "쿠팡",
                notificationCount = 4,
                lastSeenLabel = "방금",
            ),
        )
        val notifications = listOf(
            notification(id = "1", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
            notification(id = "2", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
            notification(id = "3", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = false),
            notification(id = "4", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = true),
            notification(id = "5", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = false),
        )

        val result = builder.filterCapturedApps(
            capturedApps = capturedApps,
            notifications = notifications,
            hidePersistentNotifications = true,
        )

        assertEquals(1, result.size)
        assertEquals("com.coupang.mobile", result.single().packageName)
        assertEquals(2, result.single().notificationCount)
    }

    private fun notification(
        id: String,
        packageName: String = "pkg.$id",
        appName: String = "앱",
        isPersistent: Boolean,
    ) = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = packageName,
        sender = null,
        title = "제목",
        body = "본문",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.DIGEST,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
        isPersistent = isPersistent,
    )
}
