package com.smartnoti.app.ui.screens.onboarding

class OnboardingQuickStartSelectionSummaryBuilder {
    fun build(selectedPresetIds: Set<OnboardingQuickStartPresetId>): String {
        return when {
            selectedPresetIds.isEmpty() -> "필요한 규칙만 나중에 직접 고를 수 있어요"
            selectedPresetIds.containsAll(
                setOf(
                    OnboardingQuickStartPresetId.PROMO_QUIETING,
                    OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                    OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
                ),
            ) -> "쿠폰·세일 같은 알림은 덜 방해되게 정리하고, 결제·배송·인증은 바로 보여드릴게요"
            selectedPresetIds == setOf(OnboardingQuickStartPresetId.REPEAT_BUNDLING) -> {
                "같은 알림이 반복되면 따로 울리지 않고 한 번에 모아 보여드릴게요"
            }
            selectedPresetIds.contains(OnboardingQuickStartPresetId.IMPORTANT_PRIORITY) &&
                selectedPresetIds.contains(OnboardingQuickStartPresetId.PROMO_QUIETING) -> {
                "프로모션 알림은 덜 방해되게 정리하고, 중요한 알림은 바로 보여드릴게요"
            }
            selectedPresetIds.contains(OnboardingQuickStartPresetId.PROMO_QUIETING) -> {
                "쿠폰·세일 같은 알림은 덜 방해되게 정리해드릴게요"
            }
            selectedPresetIds.contains(OnboardingQuickStartPresetId.IMPORTANT_PRIORITY) -> {
                "결제·배송·인증처럼 놓치면 안 되는 알림은 바로 보여드릴게요"
            }
            else -> "반복되는 알림은 한 번에 모아 보여드릴게요"
        }
    }
}
