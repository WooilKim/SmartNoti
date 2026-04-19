package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.notificationPostedAtMillisOrNull

class InsightDrillDownBuilder {
    fun build(
        notifications: List<NotificationUiModel>,
        filter: InsightDrillDownFilter,
        range: InsightDrillDownRange = InsightDrillDownRange.ALL,
        nowMillis: Long = System.currentTimeMillis(),
    ): InsightDrillDownResult {
        val filteredNotifications = notifications.filter { notification ->
            notification.status == NotificationStatusUi.DIGEST ||
                notification.status == NotificationStatusUi.SILENT
        }.filter { notification ->
            when (filter) {
                is InsightDrillDownFilter.App -> notification.appName == filter.appName
                is InsightDrillDownFilter.Reason -> filter.reasonTag in notification.reasonTags
            }
        }.filter { notification ->
            range.includes(notification.id, nowMillis)
        }.sortedByDescending(NotificationUiModel::id)

        val title = when (filter) {
            is InsightDrillDownFilter.App -> "${filter.appName} 인사이트"
            is InsightDrillDownFilter.Reason -> "${filter.reasonTag} 이유"
        }
        val subtitle = when (filter) {
            is InsightDrillDownFilter.App -> "${filter.appName} 알림 ${filteredNotifications.size}건이 SmartNoti에서 어떻게 정리됐는지 보여줘요."
            is InsightDrillDownFilter.Reason -> "'${filter.reasonTag}' 이유로 정리된 알림 ${filteredNotifications.size}건을 모아봤어요."
        }

        return InsightDrillDownResult(
            title = title,
            subtitle = subtitle,
            notifications = filteredNotifications,
        )
    }
}

sealed interface InsightDrillDownFilter {
    data class App(val appName: String) : InsightDrillDownFilter
    data class Reason(val reasonTag: String) : InsightDrillDownFilter
}

data class InsightDrillDownResult(
    val title: String,
    val subtitle: String,
    val notifications: List<NotificationUiModel>,
)

enum class InsightDrillDownRange(
    val label: String,
    val windowMillis: Long?,
    val routeValue: String,
) {
    RECENT_3_HOURS(label = "최근 3시간", windowMillis = 3 * 60 * 60 * 1000L, routeValue = "recent_3_hours"),
    RECENT_24_HOURS(label = "최근 24시간", windowMillis = 24 * 60 * 60 * 1000L, routeValue = "recent_24_hours"),
    ALL(label = "전체", windowMillis = null, routeValue = "all");

    companion object {
        fun fromRouteValue(routeValue: String?): InsightDrillDownRange {
            return entries.firstOrNull { it.routeValue == routeValue } ?: RECENT_24_HOURS
        }
    }
}

private fun InsightDrillDownRange.includes(notificationId: String, nowMillis: Long): Boolean {
    val windowMillis = windowMillis ?: return true
    val postedAtMillis = notificationId.notificationPostedAtMillisOrNull() ?: return false
    val windowStart = nowMillis - windowMillis
    return postedAtMillis in windowStart..nowMillis
}
