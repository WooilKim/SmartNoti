package com.smartnoti.app.domain.usecase

class InsightContextBadgeModelBuilder {
    fun build(source: InsightDrillDownSource): InsightContextBadgeModel {
        return when (source) {
            InsightDrillDownSource.GENERAL -> InsightContextBadgeModel(
                label = "일반 인사이트",
                tone = InsightContextBadgeTone.GENERAL,
            )
            InsightDrillDownSource.SUPPRESSION -> InsightContextBadgeModel(
                label = "숨김 인사이트",
                tone = InsightContextBadgeTone.SUPPRESSION,
            )
        }
    }
}

data class InsightContextBadgeModel(
    val label: String,
    val tone: InsightContextBadgeTone,
)

enum class InsightContextBadgeTone {
    GENERAL,
    SUPPRESSION,
}
