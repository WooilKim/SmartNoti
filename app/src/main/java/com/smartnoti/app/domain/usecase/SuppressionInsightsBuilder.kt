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
            // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6: IGNORE is
            // tracked alongside — but never merged into — the DIGEST+SILENT
            // "정리" bucket so Settings/Home can render "정리 N + 삭제 M"
            // separately. Sort ordering below continues to use filteredCount
            // (DIGEST+SILENT) to preserve the "조용히 처리" ranking; consumers
            // that want a combined noise-reduction total sum the two counts.
            val filteredCount = notifications.count { notification ->
                notification.packageName == app.packageName &&
                    (notification.status == NotificationStatusUi.DIGEST || notification.status == NotificationStatusUi.SILENT)
            }
            val ignoredCount = notifications.count { notification ->
                notification.packageName == app.packageName &&
                    notification.status == NotificationStatusUi.IGNORE
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
                ignoredCount = ignoredCount,
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
        val selectedIgnoredCount = selectedAppInsights.sumOf { it.ignoredCount }
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
            selectedIgnoredCount = selectedIgnoredCount,
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
    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6: IGNORE count lives
    // alongside filteredCount so Settings can surface "정리 N + 삭제 M" — the
    // rule-driven IGNORE stream is philosophically distinct from the
    // DIGEST+SILENT "조용히 처리" bucket and users asked for transparent
    // separation.
    val selectedIgnoredCount: Int = 0,
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
    // Per-app IGNORE stream — see `SuppressionInsightsSummary.selectedIgnoredCount`
    // for rationale. Defaulted to 0 so legacy construction sites that still
    // build the model without the IGNORE axis keep compiling.
    val ignoredCount: Int = 0,
    val filteredSharePercent: Int,
    val lastSeenLabel: String,
    val isSuppressed: Boolean,
)
