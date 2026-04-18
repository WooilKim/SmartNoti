package com.smartnoti.app.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuickStartSelectionSummaryBuilderTest {

    private val builder = OnboardingQuickStartSelectionSummaryBuilder()

    @Test
    fun all_three_selected_summarizes_quieter_promos_and_preserved_important_alerts() {
        val summary = builder.build(
            setOf(
                OnboardingQuickStartPresetId.PROMO_QUIETING,
                OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            ),
        )

        assertTrue(summary.contains("쿠폰·세일"))
        assertTrue(summary.contains("결제·배송·인증"))
    }

    @Test
    fun only_repeat_selected_focuses_on_grouping_repeated_notifications() {
        val summary = builder.build(setOf(OnboardingQuickStartPresetId.REPEAT_BUNDLING))

        assertEquals("같은 알림이 반복되면 따로 울리지 않고 한 번에 모아 보여드릴게요", summary)
    }

    @Test
    fun no_selection_returns_manual_configuration_fallback() {
        val summary = builder.build(emptySet())

        assertEquals("필요한 규칙만 나중에 직접 고를 수 있어요", summary)
    }
}
