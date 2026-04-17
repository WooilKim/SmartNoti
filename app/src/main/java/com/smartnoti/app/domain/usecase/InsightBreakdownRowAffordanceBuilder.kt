package com.smartnoti.app.domain.usecase

class InsightBreakdownRowAffordanceBuilder {
    fun build(
        isClickable: Boolean,
        isCurrent: Boolean = false,
    ): InsightBreakdownRowAffordance {
        return when {
            isCurrent -> InsightBreakdownRowAffordance(
                hintLabel = "현재 보고 있는 항목",
                showChevron = false,
                emphasizeCurrent = true,
            )
            isClickable -> InsightBreakdownRowAffordance(
                hintLabel = "탭해서 자세히 보기",
                showChevron = true,
                emphasizeCurrent = false,
            )
            else -> InsightBreakdownRowAffordance(
                hintLabel = "참고용 요약",
                showChevron = false,
                emphasizeCurrent = false,
            )
        }
    }
}

data class InsightBreakdownRowAffordance(
    val hintLabel: String,
    val showChevron: Boolean,
    val emphasizeCurrent: Boolean,
)
