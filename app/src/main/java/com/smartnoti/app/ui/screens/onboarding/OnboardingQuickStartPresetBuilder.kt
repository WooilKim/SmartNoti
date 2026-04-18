package com.smartnoti.app.ui.screens.onboarding

class OnboardingQuickStartPresetBuilder {
    fun buildDefaultPresets(): List<OnboardingQuickStartPresetUiModel> {
        return listOf(
            OnboardingQuickStartPresetUiModel(
                id = OnboardingQuickStartPresetId.PROMO_QUIETING,
                title = "프로모션 알림 조용히",
                description = "쿠폰·세일·이벤트 알림을 덜 방해되게 정리해요",
                defaultEnabled = true,
                reducedSection = OnboardingQuickStartImpactSection(
                    title = "조용해지는 알림",
                    examples = listOf(
                        "(광고) 오늘만 특가",
                        "쿠폰이 도착했어요",
                        "이번 주말 세일 안내",
                    ),
                ),
                preservedSection = OnboardingQuickStartImpactSection(
                    title = "그대로 보이는 알림",
                    examples = listOf(
                        "주문 상품이 배송 출발",
                        "결제가 완료됐어요",
                        "인증번호 482913",
                    ),
                ),
                footerText = "여러 쇼핑·멤버십 앱의 프로모션 알림을 한 번에 줄일 수 있어요",
            ),
            OnboardingQuickStartPresetUiModel(
                id = OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                title = "반복 알림 묶기",
                description = "같은 알림이 계속 오면 따로 울리지 않고 한 번에 모아 보여줘요",
                defaultEnabled = true,
                reducedSection = OnboardingQuickStartImpactSection(
                    title = "이전엔",
                    examples = listOf(
                        "비슷한 알림이 여러 번 따로 도착",
                    ),
                ),
                preservedSection = OnboardingQuickStartImpactSection(
                    title = "적용 후엔",
                    examples = listOf(
                        "Digest 1건으로 모아보기",
                        "필요할 때 한 번에 확인",
                    ),
                ),
                footerText = "반복되는 알림 소음을 줄이고 알림창이 덜 복잡해져요",
            ),
            OnboardingQuickStartPresetUiModel(
                id = OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
                title = "중요한 알림은 바로 전달",
                description = "결제·배송·인증처럼 놓치면 안 되는 알림은 바로 보여줘요",
                defaultEnabled = true,
                reducedSection = OnboardingQuickStartImpactSection(
                    title = "조용히 정리돼도 놓치지 않는 이유",
                    examples = listOf(
                        "조용히 정리 기능을 켜도 중요한 알림까지 같이 묻히지 않아요",
                    ),
                ),
                preservedSection = OnboardingQuickStartImpactSection(
                    title = "바로 보여주는 알림",
                    examples = listOf(
                        "인증번호 482913",
                        "배송이 시작됐어요",
                        "결제가 완료됐어요",
                    ),
                ),
                footerText = "덜 중요한 건 줄이고, 꼭 봐야 할 건 놓치지 않게 해줘요",
            ),
        )
    }
}
