package com.smartnoti.app.domain.usecase

class InsightDrillDownReasonNavigationModelBuilder {
    fun build(
        items: List<InsightDrillDownReasonBreakdownItem>,
        currentReasonTag: String?,
    ): List<InsightDrillDownReasonNavigationItem> {
        return items.map { item ->
            val isCurrentReason = currentReasonTag == item.tag
            InsightDrillDownReasonNavigationItem(
                tag = item.tag,
                count = item.count,
                shareFraction = item.shareFraction,
                isTopReason = item.isTopReason,
                isCurrentReason = isCurrentReason,
                isClickable = !isCurrentReason,
                hintLabel = if (isCurrentReason) {
                    "현재 보고 있는 이유"
                } else {
                    "탭해서 이 이유만 다시 보기"
                },
            )
        }
    }
}

data class InsightDrillDownReasonNavigationItem(
    val tag: String,
    val count: Int,
    val shareFraction: Float,
    val isTopReason: Boolean,
    val isCurrentReason: Boolean,
    val isClickable: Boolean,
    val hintLabel: String,
)
