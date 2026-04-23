package com.smartnoti.app.ui.screens.onboarding

import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-first contract for plan
 * `docs/plans/2026-04-23-onboarding-quick-start-seed-categories.md` Task 1.
 *
 * Pins the deterministic mapping from a quick-start preset selection (already
 * resolved into [RuleUiModel]s by `OnboardingQuickStartRuleApplier`) to a
 * 1:1 list of [com.smartnoti.app.domain.model.Category]s that the onboarding
 * flow will persist alongside the rules. Deterministic ids
 * (`cat-onboarding-<presetId.lowercase>`) keep the operation idempotent so a
 * future "re-apply quick-start" flow upserts in place rather than appending
 * duplicates.
 */
class OnboardingQuickStartCategoryApplierTest {

    private val applier = OnboardingQuickStartCategoryApplier()

    @Test
    fun empty_input_produces_empty_list() {
        val categories = applier.buildCategoriesByPresetId(emptyMap())
        assertTrue(categories.isEmpty())
    }

    @Test
    fun important_preset_alone_maps_to_priority_category_with_deterministic_id() {
        val categories = applier.buildCategoriesByPresetId(
            mapOf(
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY to fakeRule(
                    id = "rule-imp",
                    title = "중요 알림",
                ),
            ),
        )

        assertEquals(1, categories.size)
        val only = categories.single()
        assertEquals("cat-onboarding-important_priority", only.id)
        assertEquals("중요 알림", only.name)
        assertEquals(CategoryAction.PRIORITY, only.action)
        assertEquals(listOf("rule-imp"), only.ruleIds)
        assertEquals(0, only.order)
        assertNull(only.appPackageName)
    }

    @Test
    fun all_three_presets_map_in_canonical_preset_order() {
        val categories = applier.buildCategoriesByPresetId(
            mapOf(
                // Intentionally out of canonical order — applier must reorder.
                OnboardingQuickStartPresetId.REPEAT_BUNDLING to fakeRule(
                    id = "rule-repeat",
                    title = "반복 알림",
                ),
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY to fakeRule(
                    id = "rule-imp",
                    title = "중요 알림",
                ),
                OnboardingQuickStartPresetId.PROMO_QUIETING to fakeRule(
                    id = "rule-promo",
                    title = "프로모션 알림",
                ),
            ),
        )

        assertEquals(
            listOf(
                "cat-onboarding-important_priority",
                "cat-onboarding-promo_quieting",
                "cat-onboarding-repeat_bundling",
            ),
            categories.map { it.id },
        )
        assertEquals(listOf(0, 1, 2), categories.map { it.order })
        assertEquals(
            listOf(
                CategoryAction.PRIORITY,
                CategoryAction.DIGEST,
                CategoryAction.DIGEST,
            ),
            categories.map { it.action },
        )
        assertEquals(
            listOf("중요 알림", "프로모션 알림", "반복 알림"),
            categories.map { it.name },
        )
        assertEquals(
            listOf(
                listOf("rule-imp"),
                listOf("rule-promo"),
                listOf("rule-repeat"),
            ),
            categories.map { it.ruleIds },
        )
        assertTrue(categories.all { it.appPackageName == null })
    }

    @Test
    fun mapping_is_deterministic_across_repeat_invocations() {
        val input = mapOf(
            OnboardingQuickStartPresetId.IMPORTANT_PRIORITY to fakeRule(
                id = "rule-imp",
                title = "중요 알림",
            ),
            OnboardingQuickStartPresetId.PROMO_QUIETING to fakeRule(
                id = "rule-promo",
                title = "프로모션 알림",
            ),
        )

        val first = applier.buildCategoriesByPresetId(input)
        val second = applier.buildCategoriesByPresetId(input)

        assertEquals(first, second)
    }

    private fun fakeRule(
        id: String,
        title: String,
    ): RuleUiModel = RuleUiModel(
        id = id,
        title = title,
        subtitle = "",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "stub",
        overrideOf = null,
    )
}
