package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuppressionBreakdownChartModelBuilderTest {

    private val builder = SuppressionBreakdownChartModelBuilder()

    @Test
    fun builds_share_bars_for_selected_suppressed_apps_only() {
        val model = builder.build(
            listOf(
                appInsight(appName = "쿠팡", filteredCount = 4, isSuppressed = true),
                appInsight(appName = "뉴스", filteredCount = 2, isSuppressed = true),
                appInsight(appName = "슬랙", filteredCount = 5, isSuppressed = false),
            )
        )

        assertEquals(2, model.items.size)
        assertEquals("쿠팡", model.items[0].appName)
        assertEquals(0.67f, model.items[0].shareFraction)
        assertTrue(model.items[0].isTopApp)
        assertEquals("뉴스", model.items[1].appName)
        assertEquals(0.33f, model.items[1].shareFraction)
        assertFalse(model.items[1].isTopApp)
    }

    @Test
    fun marks_tied_top_apps_when_filtered_counts_match() {
        val model = builder.build(
            listOf(
                appInsight(appName = "쿠팡", filteredCount = 3, isSuppressed = true),
                appInsight(appName = "뉴스", filteredCount = 3, isSuppressed = true),
            )
        )

        assertTrue(model.items.all { it.isTopApp })
    }

    @Test
    fun returns_empty_when_selected_apps_have_no_filtered_notifications() {
        val model = builder.build(
            listOf(
                appInsight(appName = "쿠팡", filteredCount = 0, isSuppressed = true),
                appInsight(appName = "뉴스", filteredCount = 0, isSuppressed = false),
            )
        )

        assertTrue(model.items.isEmpty())
    }

    private fun appInsight(
        appName: String,
        filteredCount: Int,
        isSuppressed: Boolean,
    ) = SuppressedAppInsight(
        packageName = "pkg.$appName",
        appName = appName,
        capturedCount = 10,
        filteredCount = filteredCount,
        filteredSharePercent = filteredCount * 10,
        lastSeenLabel = "방금",
        isSuppressed = isSuppressed,
    )
}
