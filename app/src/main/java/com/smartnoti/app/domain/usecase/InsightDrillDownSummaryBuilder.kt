package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi

class InsightDrillDownSummaryBuilder {
    private val nonExplanatoryReasonTags = setOf(
        "발신자 있음",
        "일반 알림",
    )

    fun build(notifications: List<com.smartnoti.app.domain.model.NotificationUiModel>): InsightDrillDownSummary {
        if (notifications.isEmpty()) {
            return InsightDrillDownSummary()
        }

        val reasonCounts = notifications
            .flatMap { notification -> notification.reasonTags }
            .map(String::trim)
            .filter { tag -> tag.isNotEmpty() && tag !in nonExplanatoryReasonTags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(3)
            .map { HomeReasonInsight(tag = it.key, count = it.value) }

        return InsightDrillDownSummary(
            totalCount = notifications.size,
            digestCount = notifications.count { it.status == NotificationStatusUi.DIGEST },
            silentCount = notifications.count { it.status == NotificationStatusUi.SILENT },
            ignoredCount = notifications.count { it.status == NotificationStatusUi.IGNORE },
            topReasonTag = reasonCounts.firstOrNull()?.tag,
            topReasons = reasonCounts,
        )
    }
}

data class InsightDrillDownSummary(
    val totalCount: Int = 0,
    val digestCount: Int = 0,
    val silentCount: Int = 0,
    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6: IGNORE surfaces as
    // its own count so weekly insights can render "정리 N건 · 삭제 M건"
    // separately rather than merging it into the DIGEST / SILENT "조용히
    // 처리" bucket.
    val ignoredCount: Int = 0,
    val topReasonTag: String? = null,
    val topReasons: List<HomeReasonInsight> = emptyList(),
)
