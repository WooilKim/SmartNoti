package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

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
    }

    private fun RuleActionUi.toSubtitle(): String = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> "항상 바로 보기"
        RuleActionUi.DIGEST -> "Digest로 묶기"
        RuleActionUi.SILENT -> "조용히 정리"
        RuleActionUi.CONTEXTUAL -> "상황에 따라 자동 분류"
    }
}