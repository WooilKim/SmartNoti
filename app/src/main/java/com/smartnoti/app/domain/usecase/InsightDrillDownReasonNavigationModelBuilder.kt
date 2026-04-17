package com.smartnoti.app.domain.usecase

class InsightDrillDownReasonNavigationModelBuilder {
    private val affordanceBuilder = InsightBreakdownRowAffordanceBuilder()

    fun build(
        items: List<InsightDrillDownReasonBreakdownItem>,
        currentReasonTag: String?,
    ): List<InsightDrillDownReasonNavigationItem> {
        return items.map { item ->
            val isCurrentReason = currentReasonTag == item.tag
            val affordance = affordanceBuilder.build(
                isClickable = !isCurrentReason,
                isCurrent = isCurrentReason,
            )
            InsightDrillDownReasonNavigationItem(
                tag = item.tag,
                count = item.count,
                shareFraction = item.shareFraction,
                isTopReason = item.isTopReason,
                isCurrentReason = isCurrentReason,
                isClickable = !isCurrentReason,
                hintLabel = affordance.hintLabel,
                showChevron = affordance.showChevron,
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
    val showChevron: Boolean,
)
