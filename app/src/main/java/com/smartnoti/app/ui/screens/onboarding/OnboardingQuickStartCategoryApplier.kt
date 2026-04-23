package com.smartnoti.app.ui.screens.onboarding

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Pure mapper that turns the resolved quick-start [RuleUiModel] per
 * [OnboardingQuickStartPresetId] into a 1:1 list of [Category]s suitable for
 * persisting via `CategoriesRepository.upsertCategory`.
 *
 * Plan `docs/plans/2026-04-23-onboarding-quick-start-seed-categories.md` —
 * before this mapper, onboarding seeded `RulesRepository` only, leaving
 * `CategoriesRepository` empty after first run. Phase P3 of the
 * categories-split-rules-actions effort made Categories the user-facing entry
 * point, so an empty Categories tab on first launch is a UX break. This
 * applier closes that gap by producing one Category per quick-start preset
 * with deterministic ids (`cat-onboarding-<presetId.lowercase>`) so re-applying
 * the same selection upserts in place rather than appending duplicates.
 *
 * Mapping rules (kept as a `when` so adding a new preset enum value is a
 * compile-time prompt to define its CategoryAction):
 *
 *  - [OnboardingQuickStartPresetId.IMPORTANT_PRIORITY] → [CategoryAction.PRIORITY]
 *  - [OnboardingQuickStartPresetId.PROMO_QUIETING] → [CategoryAction.DIGEST]
 *  - [OnboardingQuickStartPresetId.REPEAT_BUNDLING] → [CategoryAction.DIGEST]
 *
 * The Category `name` is the Rule `title` (already user-facing copy:
 * `중요 알림`, `프로모션 알림`, `반복 알림`). `appPackageName = null` because
 * quick-start presets are KEYWORD/REPEAT_BUNDLE based, not app-pinned.
 */
class OnboardingQuickStartCategoryApplier {

    /**
     * Build deterministic [Category]s from the quick-start rule selection.
     *
     * Iteration order follows [orderedPresetIds] regardless of the input map's
     * iteration order, so the resulting `order` indices are stable across
     * platforms (LinkedHashMap quirks, etc.). Presets present in the map but
     * absent from [orderedPresetIds] are dropped — the canonical preset list
     * is the source of truth.
     */
    fun buildCategoriesByPresetId(
        rulesByPresetId: Map<OnboardingQuickStartPresetId, RuleUiModel>,
    ): List<Category> {
        val orderedPresets = orderedPresetIds.filter(rulesByPresetId::containsKey)
        return orderedPresets.mapIndexed { index, presetId ->
            val rule = rulesByPresetId.getValue(presetId)
            Category(
                id = categoryIdFor(presetId),
                name = rule.title,
                appPackageName = null,
                ruleIds = listOf(rule.id),
                action = categoryActionFor(presetId),
                order = index,
            )
        }
    }

    private fun categoryIdFor(presetId: OnboardingQuickStartPresetId): String {
        return "cat-onboarding-${presetId.name.lowercase()}"
    }

    private fun categoryActionFor(presetId: OnboardingQuickStartPresetId): CategoryAction {
        return when (presetId) {
            OnboardingQuickStartPresetId.IMPORTANT_PRIORITY -> CategoryAction.PRIORITY
            OnboardingQuickStartPresetId.PROMO_QUIETING -> CategoryAction.DIGEST
            OnboardingQuickStartPresetId.REPEAT_BUNDLING -> CategoryAction.DIGEST
        }
    }

    companion object {
        // Keep aligned with OnboardingQuickStartRuleApplier.orderedPresetIds —
        // both files describe the same canonical presentation order
        // (IMPORTANT first, then PROMO, then REPEAT).
        private val orderedPresetIds = listOf(
            OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            OnboardingQuickStartPresetId.PROMO_QUIETING,
            OnboardingQuickStartPresetId.REPEAT_BUNDLING,
        )
    }
}
