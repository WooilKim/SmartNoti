package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightDrillDownBuilderTest {

    private val builder = InsightDrillDownBuilder()

    @Test
    fun builds_app_filtered_notification_list_and_summary() {
        val result = builder.build(
            notifications = listOf(
                notification(id = "1", appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱")),
                notification(id = "2", appName = "쿠팡", status = NotificationStatusUi.SILENT, reasonTags = listOf("반복 알림")),
                notification(id = "3", appName = "슬랙", status = NotificationStatusUi.PRIORITY, reasonTags = listOf("중요 키워드")),
            ),
            filter = InsightDrillDownFilter.App(appName = "쿠팡"),
        )

        assertEquals("쿠팡 인사이트", result.title)
        assertEquals("쿠팡 알림 2건이 SmartNoti에서 어떻게 정리됐는지 보여줘요.", result.subtitle)
        assertEquals(2, result.notifications.size)
        assertEquals(listOf("2", "1"), result.notifications.map { it.id })
    }

    @Test
    fun builds_reason_filtered_notification_list_and_summary() {
        val result = builder.build(
            notifications = listOf(
                notification(id = "1", appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱", "반복 알림")),
                notification(id = "2", appName = "당근", status = NotificationStatusUi.SILENT, reasonTags = listOf("사용자 규칙")),
                notification(id = "3", appName = "뉴스", status = NotificationStatusUi.PRIORITY, reasonTags = listOf("쇼핑 앱")),
            ),
            filter = InsightDrillDownFilter.Reason(reasonTag = "쇼핑 앱"),
        )

        assertEquals("쇼핑 앱 이유", result.title)
        assertEquals("'쇼핑 앱' 이유로 정리된 알림 1건을 모아봤어요.", result.subtitle)
        assertEquals(listOf("1"), result.notifications.map { it.id })
    }

    @Test
    fun keeps_empty_result_when_no_notifications_match_filter() {
        val result = builder.build(
            notifications = listOf(
                notification(id = "1", appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱")),
            ),
            filter = InsightDrillDownFilter.Reason(reasonTag = "사용자 규칙"),
        )

        assertEquals("사용자 규칙 이유", result.title)
        assertEquals(0, result.notifications.size)
    }

    private fun notification(
        id: String,
        appName: String,
        status: NotificationStatusUi,
        reasonTags: List<String>,
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
    )
}
