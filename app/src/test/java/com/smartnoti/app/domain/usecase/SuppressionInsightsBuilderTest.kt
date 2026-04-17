package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuppressionInsightsBuilderTest {

    private val builder = SuppressionInsightsBuilder()

    @Test
    fun aggregates_selected_suppressed_apps_for_settings_summary() {
        val summary = builder.build(
            capturedApps = listOf(
                capturedApp(packageName = "com.coupang.mobile", appName = "쿠팡", notificationCount = 6),
                capturedApp(packageName = "com.news.app", appName = "뉴스", notificationCount = 4),
            ),
            notifications = listOf(
                notification(id = "1", packageName = "com.coupang.mobile", appName = "쿠팡", status = NotificationStatusUi.DIGEST),
                notification(id = "2", packageName = "com.coupang.mobile", appName = "쿠팡", status = NotificationStatusUi.SILENT),
                notification(id = "3", packageName = "com.coupang.mobile", appName = "쿠팡", status = NotificationStatusUi.PRIORITY),
                notification(id = "4", packageName = "com.news.app", appName = "뉴스", status = NotificationStatusUi.DIGEST),
            ),
            suppressedPackages = setOf("com.coupang.mobile"),
        )

        assertEquals(1, summary.selectedAppCount)
        assertEquals(6, summary.selectedCapturedCount)
        assertEquals(2, summary.selectedFilteredCount)
        assertEquals(33, summary.selectedFilteredSharePercent)
        assertEquals("쿠팡", summary.topSelectedAppName)
        assertEquals(2, summary.topSelectedAppFilteredCount)
    }

    @Test
    fun app_insights_include_filtered_counts_and_selected_apps_are_sorted_first() {
        val summary = builder.build(
            capturedApps = listOf(
                capturedApp(packageName = "com.coupang.mobile", appName = "쿠팡", notificationCount = 5),
                capturedApp(packageName = "com.news.app", appName = "뉴스", notificationCount = 3),
            ),
            notifications = listOf(
                notification(id = "1", packageName = "com.news.app", appName = "뉴스", status = NotificationStatusUi.SILENT),
                notification(id = "2", packageName = "com.coupang.mobile", appName = "쿠팡", status = NotificationStatusUi.DIGEST),
                notification(id = "3", packageName = "com.coupang.mobile", appName = "쿠팡", status = NotificationStatusUi.DIGEST),
            ),
            suppressedPackages = setOf("com.news.app"),
        )

        assertEquals("뉴스", summary.appInsights.first().appName)
        assertTrue(summary.appInsights.first().isSuppressed)
        assertEquals(1, summary.appInsights.first().filteredCount)
        assertEquals(33, summary.appInsights.first().filteredSharePercent)

        assertEquals("쿠팡", summary.appInsights[1].appName)
        assertFalse(summary.appInsights[1].isSuppressed)
        assertEquals(2, summary.appInsights[1].filteredCount)
        assertEquals(40, summary.appInsights[1].filteredSharePercent)
    }

    @Test
    fun returns_zero_summary_when_no_suppressed_app_is_selected() {
        val summary = builder.build(
            capturedApps = listOf(
                capturedApp(packageName = "com.coupang.mobile", appName = "쿠팡", notificationCount = 2),
            ),
            notifications = listOf(
                notification(id = "1", packageName = "com.coupang.mobile", appName = "쿠팡", status = NotificationStatusUi.DIGEST),
            ),
            suppressedPackages = emptySet(),
        )

        assertEquals(0, summary.selectedAppCount)
        assertEquals(0, summary.selectedCapturedCount)
        assertEquals(0, summary.selectedFilteredCount)
        assertEquals(0, summary.selectedFilteredSharePercent)
        assertNull(summary.topSelectedAppName)
        assertEquals(0, summary.topSelectedAppFilteredCount)
    }

    private fun capturedApp(
        packageName: String,
        appName: String,
        notificationCount: Long,
    ) = CapturedAppSelectionItem(
        packageName = packageName,
        appName = appName,
        notificationCount = notificationCount,
        lastSeenLabel = "방금",
    )

    private fun notification(
        id: String,
        packageName: String,
        appName: String,
        status: NotificationStatusUi,
    ) = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = packageName,
        sender = null,
        title = "제목 $id",
        body = "본문 $id",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
    )
}
