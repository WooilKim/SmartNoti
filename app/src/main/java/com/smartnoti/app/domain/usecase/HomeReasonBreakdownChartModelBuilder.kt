package com.smartnoti.app.domain.usecase

class HomeReasonBreakdownChartModelBuilder {
    fun build(reasons: List<HomeReasonInsight>): HomeReasonBreakdownChartModel {
        val total = reasons.sumOf(HomeReasonInsight::count)
        if (total == 0) {
            return HomeReasonBreakdownChartModel(items = emptyList())
        }

        val topCount = reasons.maxOf(HomeReasonInsight::count)
        return HomeReasonBreakdownChartModel(
            items = reasons.map { reason ->
                HomeReasonBreakdownItem(
                    tag = reason.tag,
                    count = reason.count,
                    shareFraction = reason.count.toFloat() / total.toFloat(),
                    isTopReason = reason.count == topCount,
                )
            },
        )
    }
}

data class HomeReasonBreakdownChartModel(
    val items: List<HomeReasonBreakdownItem>,
)

data class HomeReasonBreakdownItem(
    val tag: String,
    val count: Int,
    val shareFraction: Float,
    val isTopReason: Boolean,
)
