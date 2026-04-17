package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightDrillDownBuilderTest {

    private val builder = InsightDrillDownBuilder()
    private val nowMillis = 1_700_000_000_000L

    @Test
    fun builds_app_filtered_notification_list_and_summary() {
        val result = builder.build(
            notifications = listOf(
                notification(id = timestampedId("1", hoursAgo = 1), appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱")),
                notification(id = timestampedId("2", hoursAgo = 0), appName = "쿠팡", status = NotificationStatusUi.SILENT, reasonTags = listOf("반복 알림")),
                notification(id = timestampedId("3", hoursAgo = 0), appName = "슬랙", status = NotificationStatusUi.PRIORITY, reasonTags = listOf("중요 키워드")),
            ),
            filter = InsightDrillDownFilter.App(appName = "쿠팡"),
            range = InsightDrillDownRange.ALL,
            nowMillis = nowMillis,
        )

        assertEquals("쿠팡 인사이트", result.title)
        assertEquals("쿠팡 알림 2건이 SmartNoti에서 어떻게 정리됐는지 보여줘요.", result.subtitle)
        assertEquals(2, result.notifications.size)
        assertEquals(listOf(timestampedId("2", 0), timestampedId("1", 1)), result.notifications.map { it.id })
    }

    @Test
    fun builds_reason_filtered_notification_list_and_summary() {
        val result = builder.build(
            notifications = listOf(
                notification(id = timestampedId("1", hoursAgo = 0), appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱", "반복 알림")),
                notification(id = timestampedId("2", hoursAgo = 0), appName = "당근", status = NotificationStatusUi.SILENT, reasonTags = listOf("사용자 규칙")),
                notification(id = timestampedId("3", hoursAgo = 0), appName = "뉴스", status = NotificationStatusUi.PRIORITY, reasonTags = listOf("쇼핑 앱")),
            ),
            filter = InsightDrillDownFilter.Reason(reasonTag = "쇼핑 앱"),
            range = InsightDrillDownRange.ALL,
            nowMillis = nowMillis,
        )

        assertEquals("쇼핑 앱 이유", result.title)
        assertEquals("'쇼핑 앱' 이유로 정리된 알림 1건을 모아봤어요.", result.subtitle)
        assertEquals(listOf(timestampedId("1", 0)), result.notifications.map { it.id })
    }

    @Test
    fun keeps_empty_result_when_no_notifications_match_filter() {
        val result = builder.build(
            notifications = listOf(
                notification(id = timestampedId("1", hoursAgo = 0), appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱")),
            ),
            filter = InsightDrillDownFilter.Reason(reasonTag = "사용자 규칙"),
            range = InsightDrillDownRange.ALL,
            nowMillis = nowMillis,
        )

        assertEquals("사용자 규칙 이유", result.title)
        assertEquals(0, result.notifications.size)
    }

    @Test
    fun filters_notifications_by_recent_3_hours_range() {
        val result = builder.build(
            notifications = listOf(
                notification(id = timestampedId("1", hoursAgo = 1), appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱")),
                notification(id = timestampedId("2", hoursAgo = 5), appName = "쿠팡", status = NotificationStatusUi.SILENT, reasonTags = listOf("반복 알림")),
            ),
            filter = InsightDrillDownFilter.App(appName = "쿠팡"),
            range = InsightDrillDownRange.RECENT_3_HOURS,
            nowMillis = nowMillis,
        )

        assertEquals(listOf(timestampedId("1", 1)), result.notifications.map { it.id })
    }

    @Test
    fun filters_notifications_by_recent_24_hours_range() {
        val result = builder.build(
            notifications = listOf(
                notification(id = timestampedId("1", hoursAgo = 6), appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱")),
                notification(id = timestampedId("2", hoursAgo = 30), appName = "쿠팡", status = NotificationStatusUi.SILENT, reasonTags = listOf("반복 알림")),
            ),
            filter = InsightDrillDownFilter.App(appName = "쿠팡"),
            range = InsightDrillDownRange.RECENT_24_HOURS,
            nowMillis = nowMillis,
        )

        assertEquals(listOf(timestampedId("1", 6)), result.notifications.map { it.id })
    }

    @Test
    fun excludes_persistent_notifications_when_hide_option_enabled() {
        val result = builder.build(
            notifications = listOf(
                notification(id = timestampedId("1", hoursAgo = 0), appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱"), isPersistent = true),
                notification(id = timestampedId("2", hoursAgo = 0), appName = "쿠팡", status = NotificationStatusUi.SILENT, reasonTags = listOf("반복 알림"), isPersistent = false),
            ),
            filter = InsightDrillDownFilter.App(appName = "쿠팡"),
            range = InsightDrillDownRange.ALL,
            nowMillis = nowMillis,
            hidePersistentNotifications = true,
        )

        assertEquals(listOf(timestampedId("2", 0)), result.notifications.map { it.id })
        assertEquals("쿠팡 알림 1건이 SmartNoti에서 어떻게 정리됐는지 보여줘요.", result.subtitle)
    }

    private fun notification(
        id: String,
        appName: String,
        status: NotificationStatusUi,
        reasonTags: List<String>,
        isPersistent: Boolean = false,
    ) = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = "pkg.$appName",
        sender = null,
        title = "제목 $id",
        body = "본문 $id",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = reasonTags,
        score = null,
        isBundled = false,
        isPersistent = isPersistent,
    )

    private fun timestampedId(prefix: String, hoursAgo: Int): String {
        val millis = nowMillis - hoursAgo * 60L * 60L * 1000L
        return "$prefix:$millis"
    }
}
