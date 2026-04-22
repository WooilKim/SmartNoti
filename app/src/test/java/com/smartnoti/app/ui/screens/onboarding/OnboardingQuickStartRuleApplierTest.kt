package com.smartnoti.app.ui.screens.onboarding

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuickStartRuleApplierTest {

    private val applier = OnboardingQuickStartRuleApplier(RuleDraftFactory())

    @Test
    fun promo_selection_creates_keyword_digest_rule() {
        val rules = applier.buildRules(setOf(OnboardingQuickStartPresetId.PROMO_QUIETING))

        assertEquals(1, rules.size)
        assertEquals(RuleTypeUi.KEYWORD, rules.single().type)
        assertEquals("광고,프로모션,쿠폰,세일,특가,이벤트,혜택", rules.single().matchValue)
    }

    @Test
    fun mergeRules_prepends_selected_quick_start_rules_and_preserves_existing_rules() {
        val existingRules = listOf(
            RuleDraftFactory().create(
                title = "엄마",
                matchValue = "엄마",
                type = RuleTypeUi.PERSON,
                action = RuleActionUi.ALWAYS_PRIORITY,
            ),
        )

        val mergedRules = applier.mergeRules(
            existingRules = existingRules,
            selectedPresetIds = setOf(
                OnboardingQuickStartPresetId.PROMO_QUIETING,
                OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            ),
        )

        assertEquals(
            listOf(
                "중요 알림",
                "프로모션 알림",
                "반복 알림",
                "엄마",
            ),
            mergedRules.map { it.title },
        )
    }

    @Test
    fun mergeRules_reuses_existing_matching_quick_start_rule_id_without_duplicating_it() {
        val existingRules = listOf(
            RuleDraftFactory().create(
                title = "예전 프로모션 규칙",
                matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.DIGEST,
                enabled = false,
            ),
        )

        val mergedRules = applier.mergeRules(
            existingRules = existingRules,
            selectedPresetIds = setOf(OnboardingQuickStartPresetId.PROMO_QUIETING),
        )

        assertEquals(1, mergedRules.size)
        assertEquals(existingRules.single().id, mergedRules.single().id)
        assertEquals("프로모션 알림", mergedRules.single().title)
        assertTrue(mergedRules.single().enabled)
    }

    @Test
    fun mergeRules_with_no_selection_and_no_configured_rules_returns_empty_list() {
        val mergedRules = applier.mergeRules(
            existingRules = emptyList(),
            selectedPresetIds = emptySet(),
        )

        assertTrue(mergedRules.isEmpty())
    }

    @Test
    fun mergeRules_with_no_selection_returns_existing_rules_unchanged() {
        val existingRules = listOf(
            RuleDraftFactory().create(
                title = "엄마",
                matchValue = "엄마",
                type = RuleTypeUi.PERSON,
                action = RuleActionUi.ALWAYS_PRIORITY,
            ),
        )

        val mergedRules = applier.mergeRules(
            existingRules = existingRules,
            selectedPresetIds = emptySet(),
        )

        assertEquals(existingRules, mergedRules)
    }

    @Test
    fun repeat_selection_creates_repeat_bundle_digest_rule() {
        val rules = applier.buildRules(setOf(OnboardingQuickStartPresetId.REPEAT_BUNDLING))

        assertEquals(1, rules.size)
        assertEquals(RuleTypeUi.REPEAT_BUNDLE, rules.single().type)
        assertEquals("3", rules.single().matchValue)
    }

    @Test
    fun important_selection_creates_keyword_priority_rule() {
        val rules = applier.buildRules(setOf(OnboardingQuickStartPresetId.IMPORTANT_PRIORITY))

        assertEquals(1, rules.size)
        assertEquals(RuleTypeUi.KEYWORD, rules.single().type)
        assertEquals("인증번호,결제,배송,출발", rules.single().matchValue)
    }

    @Test
    fun all_selected_returns_rules_in_priority_safe_order() {
        val rules = applier.buildRules(
            setOf(
                OnboardingQuickStartPresetId.PROMO_QUIETING,
                OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            ),
        )

        assertEquals(
            listOf(
                "중요 알림",
                "프로모션 알림",
                "반복 알림",
            ),
            rules.map { it.title },
        )
    }

    @Test
    fun no_selection_returns_empty_rule_list() {
        assertTrue(applier.buildRules(emptySet()).isEmpty())
    }
}
