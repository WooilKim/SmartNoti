package com.smartnoti.app.domain.usecase

class InsightDrillDownCopyBuilder {
    fun build(
        filter: InsightDrillDownFilter,
        source: InsightDrillDownSource,
        range: InsightDrillDownRange,
        summary: InsightDrillDownSummary,
    ): InsightDrillDownCopy {
        val title = when {
            source == InsightDrillDownSource.SUPPRESSION && filter is InsightDrillDownFilter.App -> {
                "${filter.appName} 숨김 인사이트"
            }
            filter is InsightDrillDownFilter.App -> "${filter.appName} 인사이트"
            filter is InsightDrillDownFilter.Reason -> "${filter.reasonTag} 이유"
            else -> "인사이트"
        }

        val subtitle = when {
            source == InsightDrillDownSource.SUPPRESSION && filter is InsightDrillDownFilter.App -> {
                "원본 알림을 숨긴 상태에서 ${filter.appName} 알림 ${summary.totalCount}건이 SmartNoti에 어떻게 남았는지 보여줘요."
            }
            filter is InsightDrillDownFilter.App -> {
                "${filter.appName} 알림 ${summary.totalCount}건이 SmartNoti에서 어떻게 정리됐는지 보여줘요."
            }
            filter is InsightDrillDownFilter.Reason -> {
                "'${filter.reasonTag}' 이유로 정리된 알림 ${summary.totalCount}건을 모아봤어요."
            }
            else -> null
        }

        val overview = when {
            source == InsightDrillDownSource.SUPPRESSION && filter is InsightDrillDownFilter.App -> {
                "${range.label} 기준 원본 알림이 숨겨진 ${filter.appName} 알림 ${summary.totalCount}건을 모아봤어요."
            }
            filter is InsightDrillDownFilter.App -> {
                "${range.label} 기준 ${filter.appName}에서 정리된 알림 ${summary.totalCount}건을 시간순으로 보여줘요."
            }
            filter is InsightDrillDownFilter.Reason -> {
                "${range.label} 기준 '${filter.reasonTag}' 이유로 정리된 알림 ${summary.totalCount}건을 시간순으로 보여줘요."
            }
            else -> null
        }

        val topReasonText = when (filter) {
            is InsightDrillDownFilter.App -> {
                summary.topReasonTag?.let { topReason ->
                    if (source == InsightDrillDownSource.SUPPRESSION) {
                        "이 앱에서 숨김 처리된 알림은 '$topReason' 이유가 가장 많아요."
                    } else {
                        "이 앱에서 가장 많이 보인 이유는 '$topReason'이에요."
                    }
                }
            }
            is InsightDrillDownFilter.Reason -> {
                summary.topReasons
                    .firstOrNull { it.tag != filter.reasonTag }
                    ?.tag
                    ?.let { secondaryReason ->
                        "'${filter.reasonTag}'와 함께 많이 보인 이유는 '$secondaryReason'이에요."
                    }
            }
        }

        return InsightDrillDownCopy(
            title = title,
            subtitle = subtitle,
            overview = overview,
            topReasonText = topReasonText,
        )
    }
}

enum class InsightDrillDownSource(val routeValue: String) {
    GENERAL(routeValue = "general"),
    SUPPRESSION(routeValue = "suppression");

    companion object {
        fun fromRouteValue(routeValue: String?): InsightDrillDownSource {
            return entries.firstOrNull { it.routeValue == routeValue } ?: GENERAL
        }
    }
}

data class InsightDrillDownCopy(
    val title: String,
    val subtitle: String? = null,
    val overview: String? = null,
    val topReasonText: String? = null,
)
