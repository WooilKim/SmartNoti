package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

class HomeQuickStartAppliedSummaryBuilder {
    fun build(rules: List<RuleUiModel>): HomeQuickStartAppliedSummary? {
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

        return HomeQuickStartAppliedSummary(
            title = "빠른 시작 추천이 적용되어 있어요",
            body = body,
            label = label,
        )
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
)
