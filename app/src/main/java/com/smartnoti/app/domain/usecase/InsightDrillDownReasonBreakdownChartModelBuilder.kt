package com.smartnoti.app.domain.usecase

import kotlin.math.roundToInt

class InsightDrillDownReasonBreakdownChartModelBuilder {
    fun build(reasons: List<HomeReasonInsight>): InsightDrillDownReasonBreakdownChartModel {
        val total = reasons.sumOf(HomeReasonInsight::count)
        if (total == 0) {
            return InsightDrillDownReasonBreakdownChartModel(items = emptyList())
        }

        val topCount = reasons.maxOf(HomeReasonInsight::count)
        return InsightDrillDownReasonBreakdownChartModel(
            items = reasons.map { reason ->
                InsightDrillDownReasonBreakdownItem(
                    tag = reason.tag,
                    count = reason.count,
                    shareFraction = ((reason.count.toFloat() / total.toFloat()) * 100)
                        .roundToInt() / 100f,
                    isTopReason = reason.count == topCount,
                )
            }
        )
    }
}

data class InsightDrillDownReasonBreakdownChartModel(
    val items: List<InsightDrillDownReasonBreakdownItem>,
)

data class InsightDrillDownReasonBreakdownItem(
    val tag: String,
    val count: Int,
    val shareFraction: Float,
    val isTopReason: Boolean,
)
