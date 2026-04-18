package com.smartnoti.app.ui.screens.onboarding

enum class OnboardingQuickStartPresetId {
    PROMO_QUIETING,
    REPEAT_BUNDLING,
    IMPORTANT_PRIORITY,
}

data class OnboardingQuickStartImpactSection(
    val title: String,
    val examples: List<String>,
)

data class OnboardingQuickStartPresetUiModel(
    val id: OnboardingQuickStartPresetId,
    val title: String,
    val description: String,
    val defaultEnabled: Boolean,
    val reducedSection: OnboardingQuickStartImpactSection,
    val preservedSection: OnboardingQuickStartImpactSection,
    val footerText: String,
)
