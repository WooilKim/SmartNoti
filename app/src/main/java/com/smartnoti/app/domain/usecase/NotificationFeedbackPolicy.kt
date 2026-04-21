package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.SilentMode

class NotificationFeedbackPolicy {
    fun applyAction(
        notification: NotificationUiModel,
        action: RuleActionUi,
    ): NotificationUiModel {
        return notification.copy(
            status = action.toStatus(notification.status),
            reasonTags = (notification.reasonTags + "사용자 규칙").distinct(),
        )
    }

    /**
     * "조용히 보관" 상태의 알림을 "조용히 처리됨" 으로 전이한다.
     *
     * SILENT 가 아니면 no-op (원본 인스턴스 반환).
     * 이미 PROCESSED 면 idempotent — tag 가 중복 추가되지 않는다.
     * 호출자는 이후 repository 에 persist 한 뒤 tray 원본 알림 cancel 을 수행해야 한다.
     */
    fun markSilentProcessed(notification: NotificationUiModel): NotificationUiModel {
        if (notification.status != NotificationStatusUi.SILENT) return notification
        return notification.copy(
            silentMode = SilentMode.PROCESSED,
            reasonTags = (notification.reasonTags + PROCESSED_REASON_TAG).distinct(),
        )
    }

    fun toRule(
        notification: NotificationUiModel,
        action: RuleActionUi,
    ): RuleUiModel {
        val usesSender = !notification.sender.isNullOrBlank()
        val type = if (usesSender) RuleTypeUi.PERSON else RuleTypeUi.APP
        val title = notification.sender ?: notification.appName
        val matchValue = notification.sender ?: notification.packageName

        return RuleUiModel(
            id = "${type.name.lowercase()}:$matchValue",
            title = title,
            subtitle = action.toSubtitle(),
            type = type,
            action = action,
            enabled = true,
            matchValue = matchValue,
        )
    }

    private fun RuleActionUi.toStatus(currentStatus: NotificationStatusUi): NotificationStatusUi = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> NotificationStatusUi.PRIORITY
        RuleActionUi.DIGEST -> NotificationStatusUi.DIGEST
        RuleActionUi.SILENT -> NotificationStatusUi.SILENT
        RuleActionUi.CONTEXTUAL -> currentStatus
        // Detail "무시" feedback button wiring lands in Task 6a of plan
        // `2026-04-21-ignore-tier-fourth-decision`; Task 2 only keeps the
        // enum `when` exhaustive so the build stays green.
        RuleActionUi.IGNORE -> NotificationStatusUi.IGNORE
    }

    private fun RuleActionUi.toSubtitle(): String = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> "항상 바로 보기"
        RuleActionUi.DIGEST -> "Digest로 묶기"
        RuleActionUi.SILENT -> "조용히 정리"
        RuleActionUi.CONTEXTUAL -> "상황에 따라 자동 분류"
        RuleActionUi.IGNORE -> "무시 (즉시 삭제)"
    }

    companion object {
        const val PROCESSED_REASON_TAG = "사용자 처리"
    }
}