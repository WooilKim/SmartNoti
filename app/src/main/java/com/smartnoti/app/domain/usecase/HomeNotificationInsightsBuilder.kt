package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel

class HomeNotificationInsightsBuilder {
    private val nonExplanatoryReasonTags = setOf("발신자 있음")

    fun build(notifications: List<NotificationUiModel>): HomeNotificationInsightsSummary {
        val filteredNotifications = notifications.filter { notification ->
            notification.status == NotificationStatusUi.DIGEST ||
                notification.status == NotificationStatusUi.SILENT
        }

        if (filteredNotifications.isEmpty()) {
            return HomeNotificationInsightsSummary(filteredCount = 0)
        }

        val topFilteredApp = filteredNotifications
            .groupingBy { it.appName }
            .eachCount()
            .maxByOrNull { (_, count) -> count }

        val reasonSourceNotifications = filteredNotifications.filter { notification ->
            notification.appName == topFilteredApp?.key
        }
        val reasonCounts = reasonSourceNotifications
            .asSequence()
            .flatMap { notification ->
                notification.reasonTags.asSequence()
            }
            .map(String::trim)
            .filter { tag -> tag.isNotEmpty() && tag !in nonExplanatoryReasonTags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }

        val filteredSharePercent = ((filteredNotifications.size * 100.0) / notifications.size)
            .toInt()

        return HomeNotificationInsightsSummary(
            filteredCount = filteredNotifications.size,
            filteredSharePercent = filteredSharePercent,
            topFilteredAppName = topFilteredApp?.key,
            topFilteredAppCount = topFilteredApp?.value ?: 0,
            topReasonTag = reasonCounts.firstOrNull()?.key,
            topReasons = reasonCounts.take(MAX_REASON_INSIGHTS).map { entry ->
                HomeReasonInsight(tag = entry.key, count = entry.value)
            },
        )
    }

    private companion object {
        const val MAX_REASON_INSIGHTS = 3
    }
}

data class HomeNotificationInsightsSummary(
    val filteredCount: Int,
    val filteredSharePercent: Int = 0,
    val topFilteredAppName: String? = null,
    val topFilteredAppCount: Int = 0,
    val topReasonTag: String? = null,
    val topReasons: List<HomeReasonInsight> = emptyList(),
)

data class HomeReasonInsight(
    val tag: String,
    val count: Int,
)
