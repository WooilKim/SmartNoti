package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.domain.usecase.SuppressionInsightsSummary

enum class SuppressionInsightState {
    Disabled,
    NeedsAppSelection,
    Active,
}

data class SuppressionInsightMetric(
    val label: String,
    val value: String,
)

data class SuppressionInsightTokens(
    val state: SuppressionInsightState,
    val metrics: List<SuppressionInsightMetric>,
    val supportingMessage: String,
)

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

    fun buildTokens(
        suppressEnabled: Boolean,
        insights: SuppressionInsightsSummary,
    ): SuppressionInsightTokens {
        if (!suppressEnabled) {
            return SuppressionInsightTokens(
                state = SuppressionInsightState.Disabled,
                metrics = emptyList(),
                supportingMessage = "기능을 켜고 앱을 선택하면 원본 알림 숨김 시도 상태를 여기서 확인할 수 있어요.",
            )
        }
        if (insights.selectedAppCount == 0) {
            return SuppressionInsightTokens(
                state = SuppressionInsightState.NeedsAppSelection,
                metrics = emptyList(),
                supportingMessage = "아래 앱 목록에서 숨기고 싶은 앱을 선택하면 원본 숨김 시도 요약이 여기에 표시돼요.",
            )
        }

        val metrics = buildList {
            add(SuppressionInsightMetric(label = "선택 앱", value = "${insights.selectedAppCount}개"))
            add(SuppressionInsightMetric(label = "숨김 시도", value = "${insights.selectedFilteredCount}건"))
            add(SuppressionInsightMetric(label = "비율", value = "${insights.selectedFilteredSharePercent}%"))
            insights.topSelectedAppName?.let { appName ->
                add(SuppressionInsightMetric(label = "상위 앱", value = "$appName ${insights.topSelectedAppFilteredCount}건"))
            }
        }

        val supportingMessage = insights.topSelectedAppName?.let { topAppName ->
            "선택한 앱 알림 중 ${insights.selectedFilteredSharePercent}%에 대해 원본 숨김을 시도했고, ${topAppName}에서 ${insights.topSelectedAppFilteredCount}건이 가장 많았어요. 기기/앱에 따라 원본은 계속 남아있을 수 있어요."
        } ?: "선택한 앱의 원본 숨김 시도 상태를 계속 집계하고 있어요."

        return SuppressionInsightTokens(
            state = SuppressionInsightState.Active,
            metrics = metrics,
            supportingMessage = supportingMessage,
        )
    }
}
