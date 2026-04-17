package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel

class SuppressionInsightsBuilder {
    fun build(
        capturedApps: List<CapturedAppSelectionItem>,
        notifications: List<NotificationUiModel>,
        suppressedPackages: Set<String>,
    ): SuppressionInsightsSummary {
        val appInsights = capturedApps.map { app ->
            val filteredCount = notifications.count { notification ->
                notification.packageName == app.packageName &&
                    (notification.status == NotificationStatusUi.DIGEST || notification.status == NotificationStatusUi.SILENT)
            }
            val filteredSharePercent = if (app.notificationCount == 0L) {
                0
            } else {
                ((filteredCount * 100.0) / app.notificationCount).toInt()
            }

            SuppressedAppInsight(
                packageName = app.packageName,
                appName = app.appName,
                capturedCount = app.notificationCount,
                filteredCount = filteredCount,
                filteredSharePercent = filteredSharePercent,
                lastSeenLabel = app.lastSeenLabel,
                isSuppressed = app.packageName in suppressedPackages,
            )
        }.sortedWith(
            compareByDescending<SuppressedAppInsight> { it.isSuppressed }
                .thenByDescending { it.filteredCount }
                .thenByDescending { it.capturedCount }
                .thenBy { it.appName },
        )

        val selectedAppInsights = appInsights.filter { it.isSuppressed }
        val selectedCapturedCount = selectedAppInsights.sumOf { it.capturedCount }
        val selectedFilteredCount = selectedAppInsights.sumOf { it.filteredCount }
        val selectedFilteredSharePercent = if (selectedCapturedCount == 0L) {
            0
        } else {
            ((selectedFilteredCount * 100.0) / selectedCapturedCount).toInt()
        }
        val topSelectedApp = selectedAppInsights.maxWithOrNull(
            compareBy<SuppressedAppInsight> { it.filteredCount }
                .thenBy { it.appName },
        )

        return SuppressionInsightsSummary(
            selectedAppCount = selectedAppInsights.size,
            selectedCapturedCount = selectedCapturedCount,
            selectedFilteredCount = selectedFilteredCount,
            selectedFilteredSharePercent = selectedFilteredSharePercent,
            topSelectedAppName = topSelectedApp?.appName,
            topSelectedAppFilteredCount = topSelectedApp?.filteredCount ?: 0,
            appInsights = appInsights,
        )
    }
}

data class SuppressionInsightsSummary(
    val selectedAppCount: Int,
    val selectedCapturedCount: Long,
    val selectedFilteredCount: Int,
    val selectedFilteredSharePercent: Int,
    val topSelectedAppName: String? = null,
    val topSelectedAppFilteredCount: Int = 0,
    val appInsights: List<SuppressedAppInsight> = emptyList(),
)

data class SuppressedAppInsight(
    val packageName: String,
    val appName: String,
    val capturedCount: Long,
    val filteredCount: Int,
    val filteredSharePercent: Int,
    val lastSeenLabel: String,
    val isSuppressed: Boolean,
)
