package com.smartnoti.app.domain.usecase

import kotlin.math.roundToInt

class SuppressionBreakdownChartModelBuilder {
    fun build(appInsights: List<SuppressedAppInsight>): SuppressionBreakdownChartModel {
        val selectedApps = appInsights.filter { it.isSuppressed && it.filteredCount > 0 }
        val totalFilteredCount = selectedApps.sumOf(SuppressedAppInsight::filteredCount)
        if (totalFilteredCount == 0) {
            return SuppressionBreakdownChartModel(items = emptyList())
        }

        val topFilteredCount = selectedApps.maxOf(SuppressedAppInsight::filteredCount)
        return SuppressionBreakdownChartModel(
            items = selectedApps.map { appInsight ->
                SuppressionBreakdownItem(
                    appName = appInsight.appName,
                    filteredCount = appInsight.filteredCount,
                    shareFraction = ((appInsight.filteredCount.toFloat() / totalFilteredCount.toFloat()) * 100)
                        .roundToInt() / 100f,
                    isTopApp = appInsight.filteredCount == topFilteredCount,
                )
            }
        )
    }
}

data class SuppressionBreakdownChartModel(
    val items: List<SuppressionBreakdownItem>,
)

data class SuppressionBreakdownItem(
    val appName: String,
    val filteredCount: Int,
    val shareFraction: Float,
    val isTopApp: Boolean,
)
