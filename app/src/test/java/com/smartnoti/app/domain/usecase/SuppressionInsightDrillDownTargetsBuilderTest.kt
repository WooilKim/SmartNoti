package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuppressionInsightDrillDownTargetsBuilderTest {

    private val builder = SuppressionInsightDrillDownTargetsBuilder()

    @Test
    fun builds_top_app_and_breakdown_routes_for_settings_insight() {
        val targets = builder.build(
            summary = SuppressionInsightsSummary(
                selectedAppCount = 2,
                selectedCapturedCount = 10,
                selectedFilteredCount = 4,
                selectedFilteredSharePercent = 40,
                topSelectedAppName = "쿠팡",
                topSelectedAppFilteredCount = 3,
            ),
            breakdownItems = listOf(
                SuppressionBreakdownItem(
                    appName = "쿠팡",
                    filteredCount = 3,
                    shareFraction = 0.75f,
                    isTopApp = true,
                ),
                SuppressionBreakdownItem(
                    appName = "뉴스",
                    filteredCount = 1,
                    shareFraction = 0.25f,
                    isTopApp = false,
                ),
            ),
        )

        assertEquals("insight/app/%EC%BF%A0%ED%8C%A1", targets.topAppRoute)
        assertEquals("insight/app/%EC%BF%A0%ED%8C%A1", targets.breakdownRoutesByAppName["쿠팡"])
        assertEquals("insight/app/%EB%89%B4%EC%8A%A4", targets.breakdownRoutesByAppName["뉴스"])
    }

    @Test
    fun returns_null_top_route_when_no_top_app_exists() {
        val targets = builder.build(
            summary = SuppressionInsightsSummary(
                selectedAppCount = 0,
                selectedCapturedCount = 0,
                selectedFilteredCount = 0,
                selectedFilteredSharePercent = 0,
            ),
            breakdownItems = emptyList(),
        )

        assertNull(targets.topAppRoute)
        assertEquals(emptyMap<String, String>(), targets.breakdownRoutesByAppName)
    }
}
