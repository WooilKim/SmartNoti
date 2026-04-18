package com.smartnoti.app.ui.screens.onboarding

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.RuleDraftFactory

class OnboardingQuickStartRuleApplier(
    private val ruleDraftFactory: RuleDraftFactory,
) {
    fun buildRules(selectedPresetIds: Set<OnboardingQuickStartPresetId>): List<RuleUiModel> {
        return orderedPresetIds
            .filter(selectedPresetIds::contains)
            .map(::toRule)
    }

    private fun toRule(presetId: OnboardingQuickStartPresetId): RuleUiModel {
        return when (presetId) {
            OnboardingQuickStartPresetId.IMPORTANT_PRIORITY -> ruleDraftFactory.create(
                title = "중요 알림",
                matchValue = "인증번호,결제,배송,출발",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.ALWAYS_PRIORITY,
            )
            OnboardingQuickStartPresetId.PROMO_QUIETING -> ruleDraftFactory.create(
                title = "프로모션 알림",
                matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.DIGEST,
            )
            OnboardingQuickStartPresetId.REPEAT_BUNDLING -> ruleDraftFactory.create(
                title = "반복 알림",
                matchValue = "3",
                type = RuleTypeUi.REPEAT_BUNDLE,
                action = RuleActionUi.DIGEST,
            )
        }
    }

    companion object {
        private val orderedPresetIds = listOf(
            OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            OnboardingQuickStartPresetId.PROMO_QUIETING,
            OnboardingQuickStartPresetId.REPEAT_BUNDLING,
        )
    }
}
