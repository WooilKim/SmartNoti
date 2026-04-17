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
}
