package com.smartnoti.app.data.local

import com.smartnoti.app.domain.model.NotificationStatusUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryQueryTest {

    @Test
    fun captured_app_selection_items_preserve_count_and_labels() {
        val apps = listOf(
            CapturedAppOption(
                packageName = "com.coupang.mobile",
                appName = "쿠팡",
                lastPostedAtMillis = 1_700_000_000_000,
                notificationCount = 2,
            )
        ).toCapturedAppSelectionItems()

        assertEquals(1, apps.size)
        assertEquals("com.coupang.mobile", apps.first().packageName)
        assertEquals("쿠팡", apps.first().appName)
        assertEquals(2, apps.first().notificationCount)
        assertTrue(apps.first().lastSeenLabel.isNotBlank())
    }

    @Test
    fun filtered_captured_app_selection_items_recompute_visible_counts_when_hiding_persistent_notifications() {
        val apps = listOf(
            CapturedAppOption(
                packageName = "android.system",
                appName = "시스템 UI",
                lastPostedAtMillis = 1_700_000_000_000,
                notificationCount = 3,
            ),
            CapturedAppOption(
                packageName = "com.coupang.mobile",
                appName = "쿠팡",
                lastPostedAtMillis = 1_700_000_100_000,
                notificationCount = 4,
            ),
        ).toCapturedAppSelectionItems().toCapturedAppSelectionItems(
            notifications = listOf(
                notification(id = "1", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
                notification(id = "2", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
                notification(id = "3", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = false),
                notification(id = "4", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = true),
                notification(id = "5", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = false),
            ),
            hidePersistentNotifications = true,
        )

        assertEquals(1, apps.size)
        assertEquals("com.coupang.mobile", apps.first().packageName)
        assertEquals(2, apps.first().notificationCount)
    }

    @Test
    fun digest_groups_are_built_from_digest_notifications() {
        val digestOne = NotificationEntity(
            id = "digest-1",
            appName = "쿠팡",
            packageName = "com.coupang.mobile",
            sender = null,
            title = "오늘의 특가",
            body = "장바구니 상품이 할인 중이에요",
            postedAtMillis = 1_700_000_000_000,
            status = NotificationStatusUi.DIGEST.name,
            reasonTags = "쇼핑 앱|반복 알림",
            score = 30,
            isBundled = true,
            isPersistent = false,
            contentSignature = "오늘의 특가 장바구니 상품이 할인 중이에요",
        )
        val digestTwo = digestOne.copy(
            id = "digest-2",
            title = "가격 하락",
            body = "관심 상품 가격이 내려갔어요",
            postedAtMillis = 1_700_000_100_000,
            contentSignature = "가격 하락 관심 상품 가격이 내려갔어요",
        )
        val priority = digestOne.copy(
            id = "priority-1",
            status = NotificationStatusUi.PRIORITY.name,
            title = "결제 완료",
            body = "지금 확인이 필요해요",
            isBundled = false,
            contentSignature = "결제 완료 지금 확인이 필요해요",
        )

        val groups = listOf(digestOne, digestTwo, priority)
            .map { it.toUiModel() }
            .toDigestGroups()

        assertEquals(1, groups.size)
        assertEquals("쿠팡", groups.first().appName)
        assertEquals(2, groups.first().count)
        assertEquals(2, groups.first().items.size)
    }

    @Test
    fun filtered_digest_groups_exclude_persistent_notifications_when_enabled() {
        val groups = listOf(
            notification(id = "1", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
            notification(id = "2", packageName = "android.system", appName = "시스템 UI", isPersistent = true),
            notification(id = "3", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = false),
            notification(id = "4", packageName = "com.coupang.mobile", appName = "쿠팡", isPersistent = true),
        ).toDigestGroups(hidePersistentNotifications = true)

        assertEquals(1, groups.size)
        assertEquals("digest:com.coupang.mobile", groups.first().id)
        assertEquals(1, groups.first().count)
        assertEquals("쿠팡 관련 알림 1건", groups.first().summary)
    }

    @Test
    fun filtered_priority_notifications_exclude_persistent_when_enabled() {
        val notifications = listOf(
            notification(id = "1", status = NotificationStatusUi.PRIORITY, isPersistent = true),
            notification(id = "2", status = NotificationStatusUi.PRIORITY, isPersistent = false),
            notification(id = "3", status = NotificationStatusUi.DIGEST, isPersistent = false),
        ).toPriorityNotifications(hidePersistentNotifications = true)

        assertEquals(listOf("2"), notifications.map { it.id })
    }

    private fun notification(
        id: String,
        packageName: String = "pkg.$id",
        appName: String = "앱",
        status: NotificationStatusUi = NotificationStatusUi.DIGEST,
        isPersistent: Boolean,
    ) = NotificationEntity(
        id = id,
        appName = appName,
        packageName = packageName,
        sender = null,
        title = "제목 $id",
        body = "본문 $id",
        postedAtMillis = 1_700_000_000_000,
        status = status.name,
        reasonTags = "",
        score = null,
        isBundled = false,
        isPersistent = isPersistent,
        contentSignature = "제목 $id 본문 $id",
    ).toUiModel()
}
