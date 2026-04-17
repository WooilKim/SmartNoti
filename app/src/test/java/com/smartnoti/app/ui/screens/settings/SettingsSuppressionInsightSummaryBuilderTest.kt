package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.domain.usecase.SuppressedAppInsight
import com.smartnoti.app.domain.usecase.SuppressionInsightsSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSuppressionInsightSummaryBuilderTest {

    private val builder = SettingsSuppressionInsightSummaryBuilder()

    @Test
    fun builds_enabled_summary_with_top_app_context() {
        val summary = builder.build(
            suppressEnabled = true,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 2,
                selectedCapturedCount = 20,
                selectedFilteredCount = 11,
                selectedFilteredSharePercent = 55,
                topSelectedAppName = "쿠팡",
                topSelectedAppFilteredCount = 8,
                appInsights = listOf(
                    appInsight("쿠팡", filteredCount = 8, share = 57, suppressed = true),
                    appInsight("토스", filteredCount = 3, share = 42, suppressed = true),
                ),
            ),
        )

        assertEquals("선택 앱 2개 · 11건 정리 · 55% · 쿠팡 8건", summary)
    }

    @Test
    fun builds_disabled_summary_when_master_toggle_is_off() {
        val summary = builder.build(
            suppressEnabled = false,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 0,
                selectedCapturedCount = 0,
                selectedFilteredCount = 0,
                selectedFilteredSharePercent = 0,
            ),
        )

        assertEquals("꺼짐 · 앱을 선택하면 숨김 효과를 요약해줘요", summary)
    }

    @Test
    fun builds_empty_selection_summary_when_enabled_without_selected_apps() {
        val summary = builder.build(
            suppressEnabled = true,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 0,
                selectedCapturedCount = 0,
                selectedFilteredCount = 0,
                selectedFilteredSharePercent = 0,
            ),
        )

        assertEquals("숨길 앱을 아직 고르지 않았어요", summary)
    }

    private fun appInsight(
        name: String,
        filteredCount: Int,
        share: Int,
        suppressed: Boolean,
    ) = SuppressedAppInsight(
        packageName = "pkg.$name",
        appName = name,
        capturedCount = 10,
        filteredCount = filteredCount,
        filteredSharePercent = share,
        lastSeenLabel = "방금",
        isSuppressed = suppressed,
    )
}
