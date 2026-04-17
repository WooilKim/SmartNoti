package com.smartnoti.app.domain.usecase

import com.smartnoti.app.navigation.Routes

class SuppressionInsightDrillDownTargetsBuilder {
    fun build(
        summary: SuppressionInsightsSummary,
        breakdownItems: List<SuppressionBreakdownItem>,
    ): SuppressionInsightDrillDownTargets {
        return SuppressionInsightDrillDownTargets(
            topAppRoute = summary.topSelectedAppName?.let(Routes.Insight::createForApp),
            breakdownRoutesByAppName = breakdownItems.associate { item ->
                item.appName to Routes.Insight.createForApp(item.appName)
            },
        )
    }
}

data class SuppressionInsightDrillDownTargets(
    val topAppRoute: String? = null,
    val breakdownRoutesByAppName: Map<String, String> = emptyMap(),
)
