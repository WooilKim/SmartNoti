package com.smartnoti.app.ui.screens.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuickStartPresetBuilderTest {

    private val builder = OnboardingQuickStartPresetBuilder()

    @Test
    fun buildDefaultPresets_returns_three_presets_in_expected_order() {
        val presets = builder.buildDefaultPresets()

        assertEquals(
            listOf(
                OnboardingQuickStartPresetId.PROMO_QUIETING,
                OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            ),
            presets.map { it.id },
        )
    }

    @Test
    fun promo_preset_clearly_contrasts_quieter_and_preserved_examples() {
        val preset = builder.buildDefaultPresets().first { it.id == OnboardingQuickStartPresetId.PROMO_QUIETING }

        assertEquals("프로모션 알림 조용히", preset.title)
        assertTrue(preset.defaultEnabled)
        assertEquals("조용해지는 알림", preset.reducedSection.title)
        assertTrue(preset.reducedSection.examples.contains("(광고) 오늘만 특가"))
        assertEquals("그대로 보이는 알림", preset.preservedSection.title)
        assertTrue(preset.preservedSection.examples.contains("결제가 완료됐어요"))
    }

    @Test
    fun repeat_preset_uses_before_after_style_copy() {
        val preset = builder.buildDefaultPresets().first { it.id == OnboardingQuickStartPresetId.REPEAT_BUNDLING }

        assertEquals("반복 알림 묶기", preset.title)
        assertEquals("이전엔", preset.reducedSection.title)
        assertTrue(preset.preservedSection.title.contains("적용 후"))
        assertTrue(preset.preservedSection.examples.contains("Digest 1건으로 모아보기"))
    }

    @Test
    fun important_preset_emphasizes_immediate_delivery() {
        val preset = builder.buildDefaultPresets().first { it.id == OnboardingQuickStartPresetId.IMPORTANT_PRIORITY }

        assertEquals("중요한 알림은 바로 전달", preset.title)
        assertEquals("바로 보여주는 알림", preset.preservedSection.title)
        assertTrue(preset.preservedSection.examples.contains("인증번호 482913"))
        assertTrue(preset.footerText.contains("놓치지"))
    }
}
