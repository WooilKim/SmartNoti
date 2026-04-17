package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNotificationInsightsBuilderTest {

    private val builder = HomeNotificationInsightsBuilder()

    @Test
    fun builds_top_filtered_app_and_reason_from_digest_and_silent_notifications() {
        val summary = builder.build(
            listOf(
                notification(id = "1", appName = "쿠팡", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱", "반복 알림")),
                notification(id = "2", appName = "쿠팡", status = NotificationStatusUi.SILENT, reasonTags = listOf("쇼핑 앱")),
                notification(id = "3", appName = "당근", status = NotificationStatusUi.DIGEST, reasonTags = listOf("사용자 규칙")),
                notification(id = "4", appName = "카카오톡", status = NotificationStatusUi.PRIORITY, reasonTags = listOf("중요한 사람")),
            ),
        )

        assertEquals("쿠팡", summary.topFilteredAppName)
        assertEquals(2, summary.topFilteredAppCount)
        assertEquals("쇼핑 앱", summary.topReasonTag)
        assertEquals(3, summary.filteredCount)
    }

    @Test
    fun ignores_generic_reason_tags_when_selecting_top_reason() {
        val summary = builder.build(
            listOf(
                notification(id = "1", status = NotificationStatusUi.DIGEST, reasonTags = listOf("발신자 있음", "사용자 규칙")),
                notification(id = "2", status = NotificationStatusUi.SILENT, reasonTags = listOf("발신자 있음", "반복 알림")),
            ),
        )

        assertEquals("사용자 규칙", summary.topReasonTag)
    }

    @Test
    fun returns_null_insights_when_there_are_no_filtered_notifications() {
        val summary = builder.build(
            listOf(
                notification(id = "1", appName = "카카오톡", status = NotificationStatusUi.PRIORITY, reasonTags = listOf("중요한 사람")),
            ),
        )

        assertEquals(0, summary.filteredCount)
        assertNull(summary.topFilteredAppName)
        assertNull(summary.topReasonTag)
    }

    private fun notification(
        id: String,
        appName: String = "뉴스",
        status: NotificationStatusUi,
        reasonTags: List<String>,
    ) = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = "pkg.$id",
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
