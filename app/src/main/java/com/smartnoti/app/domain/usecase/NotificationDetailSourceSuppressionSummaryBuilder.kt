package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.SourceNotificationSuppressionState

class NotificationDetailSourceSuppressionSummaryBuilder {
    fun build(
        suppressionState: SourceNotificationSuppressionState,
        replacementNotificationIssued: Boolean,
    ): NotificationDetailSourceSuppressionSummary {
        return NotificationDetailSourceSuppressionSummary(
            statusLabel = suppressionState.toStatusLabel(),
            replacementLabel = suppressionState.toReplacementLabel(replacementNotificationIssued),
            overview = when (suppressionState) {
                SourceNotificationSuppressionState.CANCEL_ATTEMPTED -> {
                    "SmartNoti가 원본 알림 숨김을 시도했고 대체 알림도 표시했어요. 기기나 앱 정책에 따라 원본은 계속 남아있을 수 있어요."
                }
                SourceNotificationSuppressionState.APP_NOT_SELECTED -> {
                    "원본 알림 숨기기는 켜져 있지만 이 앱은 숨길 앱으로 선택되지 않아 원본을 그대로 남겨뒀어요."
                }
                SourceNotificationSuppressionState.PRIORITY_KEPT -> {
                    "이 알림은 중요 알림으로 분류돼 원본을 그대로 유지했어요."
                }
                SourceNotificationSuppressionState.PERSISTENT_PROTECTED -> {
                    "지속 알림이지만 통화·길안내·녹화처럼 보호 대상일 수 있어 원본을 남겨뒀어요."
                }
                SourceNotificationSuppressionState.NOT_CONFIGURED -> {
                    "원본 알림 숨김 조건이 충족되지 않아 SmartNoti가 원본을 건드리지 않았어요."
                }
            },
        )
    }
}

fun SourceNotificationSuppressionState.shouldShowDetailCard(
    replacementNotificationIssued: Boolean,
): Boolean {
    return replacementNotificationIssued || this != SourceNotificationSuppressionState.NOT_CONFIGURED
}

private fun SourceNotificationSuppressionState.toReplacementLabel(
    replacementNotificationIssued: Boolean,
): String {
    if (replacementNotificationIssued) return "표시됨"

    return when (this) {
        SourceNotificationSuppressionState.CANCEL_ATTEMPTED -> "표시 안 됨"
        SourceNotificationSuppressionState.APP_NOT_SELECTED,
        SourceNotificationSuppressionState.PRIORITY_KEPT,
        SourceNotificationSuppressionState.PERSISTENT_PROTECTED,
        SourceNotificationSuppressionState.NOT_CONFIGURED,
        -> "원본 유지로 생략됨"
    }
}


data class NotificationDetailSourceSuppressionSummary(
    val statusLabel: String,
    val replacementLabel: String,
    val overview: String,
)

private fun SourceNotificationSuppressionState.toStatusLabel(): String = when (this) {
    SourceNotificationSuppressionState.CANCEL_ATTEMPTED -> "원본 숨김 시도됨"
    SourceNotificationSuppressionState.APP_NOT_SELECTED,
    SourceNotificationSuppressionState.PRIORITY_KEPT,
    SourceNotificationSuppressionState.PERSISTENT_PROTECTED,
    SourceNotificationSuppressionState.NOT_CONFIGURED,
    -> "원본 유지됨"
}
