package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNotificationTimelineBuilderTest {

    private val builder = HomeNotificationTimelineBuilder()

    @Test
    fun groups_notifications_into_recent_hour_buckets_and_sorts_oldest_to_newest() {
        val timeline = builder.build(
            notifications = listOf(
                notification(id = "a:1000", status = NotificationStatusUi.DIGEST),
                notification(id = "b:1500", status = NotificationStatusUi.SILENT),
                notification(id = "c:2500", status = NotificationStatusUi.PRIORITY),
                notification(id = "d:3500", status = NotificationStatusUi.DIGEST),
            ),
            nowMillis = 3_600,
            windowMillis = 3_000,
            bucketSizeMillis = 1_000,
        )

        assertEquals(3, timeline.totalFilteredCount)
        assertEquals(listOf("2시간 전", "1시간 전", "방금 전"), timeline.buckets.map { bucket -> bucket.label })
        assertEquals(listOf(2, 0, 1), timeline.buckets.map { bucket -> bucket.filteredCount })
        assertEquals(listOf(0, 1, 0), timeline.buckets.map { bucket -> bucket.priorityCount })
    }

    @Test
    fun omits_empty_buckets_and_marks_peak_bucket() {
        val timeline = builder.build(
            notifications = listOf(
                notification(id = "a:1000", status = NotificationStatusUi.DIGEST),
                notification(id = "b:1100", status = NotificationStatusUi.SILENT),
                notification(id = "c:2900", status = NotificationStatusUi.DIGEST),
            ),
            nowMillis = 3_600,
            windowMillis = 3_000,
            bucketSizeMillis = 1_000,
        )

        assertEquals(2, timeline.buckets.size)
        assertTrue(timeline.buckets.first().isPeakFilteredBucket)
        assertEquals(false, timeline.buckets.last().isPeakFilteredBucket)
    }

    @Test
    fun returns_empty_timeline_when_no_recent_notifications_exist() {
        val timeline = builder.build(
            notifications = listOf(
                notification(id = "a:1000", status = NotificationStatusUi.DIGEST),
            ),
            nowMillis = 10_000,
            windowMillis = 1_000,
            bucketSizeMillis = 500,
        )

        assertEquals(0, timeline.totalFilteredCount)
        assertTrue(timeline.buckets.isEmpty())
    }

    @Test
    fun recent_24_hours_range_includes_older_notifications_than_recent_3_hours() {
        val notifications = listOf(
            notification(id = "a:1000", status = NotificationStatusUi.DIGEST),
            notification(id = "b:7_200_000", status = NotificationStatusUi.SILENT),
            notification(id = "c:82_800_000", status = NotificationStatusUi.DIGEST),
        )

        val recent3Hours = builder.build(
            notifications = notifications,
            range = HomeTimelineRange.RECENT_3_HOURS,
            nowMillis = 86_400_000,
        )
        val recent24Hours = builder.build(
            notifications = notifications,
            range = HomeTimelineRange.RECENT_24_HOURS,
            nowMillis = 86_400_000,
        )

        assertEquals(1, recent3Hours.totalFilteredCount)
        assertEquals(3, recent24Hours.totalFilteredCount)
        assertEquals("최근 3시간", recent3Hours.range.label)
        assertEquals("최근 24시간", recent24Hours.range.label)
    }

    private fun notification(
        id: String,
        status: NotificationStatusUi,
    ) = NotificationUiModel(
        id = id,
        appName = "앱",
        packageName = "pkg.$id",
        sender = null,
        title = "제목",
        body = "본문",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
    )
}
