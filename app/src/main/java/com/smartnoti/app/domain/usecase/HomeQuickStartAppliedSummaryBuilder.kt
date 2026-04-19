package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

class HomeQuickStartAppliedSummaryBuilder {
    fun build(
        rules: List<RuleUiModel>,
        notifications: List<NotificationUiModel> = emptyList(),
    ): HomeQuickStartAppliedSummary? {
        val appliedPresetIds = mutableSetOf<String>()
        rules.filter(RuleUiModel::enabled).forEach { rule ->
            when {
                rule.title == "중요 알림" &&
                    rule.type == RuleTypeUi.KEYWORD &&
                    rule.action == RuleActionUi.ALWAYS_PRIORITY &&
                    rule.matchValue == "인증번호,결제,배송,출발" -> appliedPresetIds += IMPORTANT
                rule.title == "프로모션 알림" &&
                    rule.type == RuleTypeUi.KEYWORD &&
                    rule.action == RuleActionUi.DIGEST &&
                    rule.matchValue == "광고,프로모션,쿠폰,세일,특가,이벤트,혜택" -> appliedPresetIds += PROMO
                rule.title == "반복 알림" &&
                    rule.type == RuleTypeUi.REPEAT_BUNDLE &&
                    rule.action == RuleActionUi.DIGEST &&
                    rule.matchValue == "3" -> appliedPresetIds += REPEAT
            }
        }

        if (appliedPresetIds.isEmpty()) return null

        val body = when {
            appliedPresetIds.containsAll(setOf(IMPORTANT, PROMO, REPEAT)) -> {
                "프로모션·반복 알림은 정리하고, 중요한 알림은 바로 보여주고 있어요"
            }
            appliedPresetIds.contains(IMPORTANT) && appliedPresetIds.contains(PROMO) -> {
                "프로모션 알림은 정리하고, 중요한 알림은 바로 보여주고 있어요"
            }
            appliedPresetIds.contains(IMPORTANT) -> {
                "결제·배송·인증 알림을 우선 전달하고 있어요"
            }
            appliedPresetIds.contains(PROMO) && appliedPresetIds.contains(REPEAT) -> {
                "프로모션·반복 알림을 덜 방해되게 정리하고 있어요"
            }
            appliedPresetIds.contains(PROMO) -> {
                "프로모션 알림을 덜 방해되게 정리하고 있어요"
            }
            else -> {
                "반복되는 알림을 한 번에 묶어 보여주고 있어요"
            }
        }

        val label = when {
            appliedPresetIds.containsAll(setOf(IMPORTANT, PROMO, REPEAT)) -> "추천 3개 적용됨"
            appliedPresetIds.contains(IMPORTANT) && appliedPresetIds.contains(PROMO) -> "중요 보호 + 프로모션 정리"
            appliedPresetIds.contains(IMPORTANT) -> "중요 알림 보호"
            appliedPresetIds.contains(PROMO) && appliedPresetIds.contains(REPEAT) -> "소음 줄이기 적용됨"
            appliedPresetIds.contains(PROMO) -> "프로모션 정리 적용됨"
            else -> "반복 알림 정리 적용됨"
        }

        val effectBody = buildEffectBody(appliedPresetIds, notifications)
        val resolvedEffectTitle = if (effectBody == null) {
            "효과를 확인하는 중"
        } else {
            "최근 효과"
        }
        val resolvedEffectBody = effectBody ?: "실제 알림이 더 쌓이면 어떤 알림이 정리되고 있는지 여기서 바로 보여드릴게요"
        return HomeQuickStartAppliedSummary(
            title = "빠른 시작 추천이 적용되어 있어요",
            body = body,
            label = label,
            effectTitle = resolvedEffectTitle,
            effectBody = resolvedEffectBody,
        )
    }

    private fun buildEffectBody(
        appliedPresetIds: Set<String>,
        notifications: List<NotificationUiModel>,
    ): String? {
        val actualEffects = mutableListOf<String>()
        val pendingEffects = mutableListOf<String>()
        if (PROMO in appliedPresetIds) {
            val promoNotifications = notifications.filter { notification ->
                notification.status == NotificationStatusUi.DIGEST &&
                    notification.reasonTags.contains("프로모션 알림")
            }
            if (promoNotifications.isNotEmpty()) {
                val topPromoApp = promoNotifications
                    .groupingBy(NotificationUiModel::appName)
                    .eachCount()
                    .maxByOrNull { (_, count) -> count }
                if (topPromoApp != null) {
                    actualEffects += "${topPromoApp.key} 프로모션 알림 ${topPromoApp.value}건이 정리됐어요"
                }
            } else {
                pendingEffects += "프로모션 알림 효과는 아직 확인 중이에요"
            }
        }
        if (REPEAT in appliedPresetIds) {
            val repeatCount = notifications.count { notification ->
                notification.status == NotificationStatusUi.DIGEST &&
                    notification.reasonTags.contains("반복 알림")
            }
            if (repeatCount > 0) {
                actualEffects += "반복 알림 ${repeatCount}건이 Digest로 묶였어요"
            } else {
                pendingEffects += "반복 알림 효과는 아직 확인 중이에요"
            }
        }
        if (IMPORTANT in appliedPresetIds) {
            val importantCount = notifications.count { notification ->
                notification.status == NotificationStatusUi.PRIORITY &&
                    notification.reasonTags.contains("중요 알림")
            }
            if (importantCount > 0) {
                actualEffects += "중요 알림 ${importantCount}건은 그대로 바로 보여줬어요"
            } else {
                pendingEffects += "중요 알림 효과는 아직 확인 중이에요"
            }
        }
        if (actualEffects.isEmpty()) return null
        return (actualEffects + pendingEffects).joinToString(" · ")
    }

    private companion object {
        const val IMPORTANT = "important"
        const val PROMO = "promo"
        const val REPEAT = "repeat"
    }
}

data class HomeQuickStartAppliedSummary(
    val title: String,
    val body: String,
    val label: String,
    val effectTitle: String? = null,
    val effectBody: String? = null,
)
