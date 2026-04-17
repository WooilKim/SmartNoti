package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.domain.usecase.SuppressionInsightsSummary

class SettingsSuppressionInsightSummaryBuilder {
    fun build(
        suppressEnabled: Boolean,
        insights: SuppressionInsightsSummary,
    ): String {
        if (!suppressEnabled) {
            return "꺼짐 · 원본 알림 숨김 시도 없음"
        }
        if (insights.selectedAppCount == 0) {
            return "숨길 앱을 아직 고르지 않았어요"
        }

        val topAppSummary = insights.topSelectedAppName?.let { appName ->
            "$appName ${insights.topSelectedAppFilteredCount}건"
        } ?: "상위 앱 없음"

        return "선택 앱 ${insights.selectedAppCount}개 · 숨김 시도 ${insights.selectedFilteredCount}건 · ${insights.selectedFilteredSharePercent}% · $topAppSummary"
    }
}
