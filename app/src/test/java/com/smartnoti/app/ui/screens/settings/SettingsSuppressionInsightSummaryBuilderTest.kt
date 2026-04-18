package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.domain.usecase.SuppressedAppInsight
import com.smartnoti.app.domain.usecase.SuppressionInsightsSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSuppressionInsightSummaryBuilderTest {

    private val builder = SettingsSuppressionInsightSummaryBuilder()

    @Test
    fun build_tokens_returns_disabled_state_without_metrics() {
        val tokens = builder.buildTokens(
            suppressEnabled = false,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 0,
                selectedCapturedCount = 0,
                selectedFilteredCount = 0,
                selectedFilteredSharePercent = 0,
            ),
        )

        assertEquals(SuppressionInsightState.Disabled, tokens.state)
        assertEquals(emptyList<SuppressionInsightMetric>(), tokens.metrics)
        assertEquals("기능을 켜고 앱을 선택하면 원본 알림 숨김 시도 상태를 여기서 확인할 수 있어요.", tokens.supportingMessage)
    }

    @Test
    fun build_tokens_returns_prompt_state_when_enabled_without_selected_apps() {
        val tokens = builder.buildTokens(
            suppressEnabled = true,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 0,
                selectedCapturedCount = 0,
                selectedFilteredCount = 0,
                selectedFilteredSharePercent = 0,
            ),
        )

        assertEquals(SuppressionInsightState.NeedsAppSelection, tokens.state)
        assertEquals(emptyList<SuppressionInsightMetric>(), tokens.metrics)
        assertEquals("아래 앱 목록에서 숨기고 싶은 앱을 선택하면 원본 숨김 시도 요약이 여기에 표시돼요.", tokens.supportingMessage)
    }

    @Test
    fun build_tokens_returns_ordered_metrics_with_top_app_context() {
        val tokens = builder.buildTokens(
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

        assertEquals(SuppressionInsightState.Active, tokens.state)
        assertEquals(
            listOf(
                SuppressionInsightMetric(label = "선택 앱", value = "2개"),
                SuppressionInsightMetric(label = "숨김 시도", value = "11건"),
                SuppressionInsightMetric(label = "비율", value = "55%"),
                SuppressionInsightMetric(label = "상위 앱", value = "쿠팡 8건"),
            ),
            tokens.metrics,
        )
        assertEquals(
            "선택한 앱 알림 중 55%에 대해 원본 숨김을 시도했고, 쿠팡에서 8건이 가장 많았어요. 기기/앱에 따라 원본은 계속 남아있을 수 있어요.",
            tokens.supportingMessage,
        )
    }

    @Test
    fun build_tokens_omits_top_app_metric_when_top_app_is_missing() {
        val tokens = builder.buildTokens(
            suppressEnabled = true,
            insights = SuppressionInsightsSummary(
                selectedAppCount = 1,
                selectedCapturedCount = 8,
                selectedFilteredCount = 3,
                selectedFilteredSharePercent = 38,
                topSelectedAppName = null,
                topSelectedAppFilteredCount = 0,
                appInsights = listOf(
                    appInsight("토스", filteredCount = 3, share = 38, suppressed = true),
                ),
            ),
        )

        assertEquals(SuppressionInsightState.Active, tokens.state)
        assertEquals(
            listOf(
                SuppressionInsightMetric(label = "선택 앱", value = "1개"),
                SuppressionInsightMetric(label = "숨김 시도", value = "3건"),
                SuppressionInsightMetric(label = "비율", value = "38%"),
            ),
            tokens.metrics,
        )
        assertEquals("선택한 앱의 원본 숨김 시도 상태를 계속 집계하고 있어요.", tokens.supportingMessage)
    }

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

        assertEquals("선택 앱 2개 · 숨김 시도 11건 · 55% · 쿠팡 8건", summary)
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

        assertEquals("꺼짐 · 원본 알림 숨김 시도 없음", summary)
    }

    @Test
    fun builds_enabled_summary_as_hide_attempt_not_guaranteed_hide() {
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

        assertEquals("선택 앱 2개 · 숨김 시도 11건 · 55% · 쿠팡 8건", summary)
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
