package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsightDrillDownSummaryBuilderTest {

    private val builder = InsightDrillDownSummaryBuilder()

    @Test
    fun builds_status_counts_and_top_reasons_from_drill_down_notifications() {
        val summary = builder.build(
            listOf(
                notification(id = "1", status = NotificationStatusUi.DIGEST, reasonTags = listOf("쇼핑 앱", "반복 알림")),
                notification(id = "2", status = NotificationStatusUi.SILENT, reasonTags = listOf("쇼핑 앱")),
                notification(id = "3", status = NotificationStatusUi.DIGEST, reasonTags = listOf("사용자 규칙")),
            )
        )

        assertEquals(3, summary.totalCount)
        assertEquals(2, summary.digestCount)
        assertEquals(1, summary.silentCount)
        assertEquals("쇼핑 앱", summary.topReasonTag)
        assertEquals(
            listOf(
                HomeReasonInsight(tag = "쇼핑 앱", count = 2),
                HomeReasonInsight(tag = "반복 알림", count = 1),
                HomeReasonInsight(tag = "사용자 규칙", count = 1),
            ),
            summary.topReasons,
        )
    }

    @Test
    fun ignores_non_explanatory_tags_when_building_reason_summary() {
        val summary = builder.build(
            listOf(
                notification(id = "1", status = NotificationStatusUi.DIGEST, reasonTags = listOf("발신자 있음", "사용자 규칙")),
                notification(id = "2", status = NotificationStatusUi.SILENT, reasonTags = listOf("일반 알림", "반복 알림")),
            )
        )

        assertEquals("사용자 규칙", summary.topReasonTag)
        assertEquals(
            listOf(
                HomeReasonInsight(tag = "사용자 규칙", count = 1),
                HomeReasonInsight(tag = "반복 알림", count = 1),
            ),
            summary.topReasons,
        )
    }

    @Test
    fun returns_empty_summary_when_no_notifications_exist() {
        val summary = builder.build(emptyList())

        assertEquals(0, summary.totalCount)
        assertEquals(0, summary.digestCount)
        assertEquals(0, summary.silentCount)
        assertNull(summary.topReasonTag)
        assertEquals(emptyList<HomeReasonInsight>(), summary.topReasons)
    }

    private fun notification(
        id: String,
        status: NotificationStatusUi,
        reasonTags: List<String>,
    ) = NotificationUiModel(
        id = id,
        appName = "쿠팡",
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
