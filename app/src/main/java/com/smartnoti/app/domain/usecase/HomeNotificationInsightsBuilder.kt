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

        val topReasonTag = filteredNotifications
            .asSequence()
            .flatMap { notification ->
                notification.reasonTags.asSequence()
            }
            .map(String::trim)
            .filter { tag -> tag.isNotEmpty() && tag !in nonExplanatoryReasonTags }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { (_, count) -> count }
            ?.key

        return HomeNotificationInsightsSummary(
            filteredCount = filteredNotifications.size,
            topFilteredAppName = topFilteredApp?.key,
            topFilteredAppCount = topFilteredApp?.value ?: 0,
            topReasonTag = topReasonTag,
        )
    }
}

data class HomeNotificationInsightsSummary(
    val filteredCount: Int,
    val topFilteredAppName: String? = null,
    val topFilteredAppCount: Int = 0,
    val topReasonTag: String? = null,
)
