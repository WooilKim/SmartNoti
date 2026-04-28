package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 7. One-tap conversion of "지금 본 발신자" → SENDER rule attached to the
 * user's PRIORITY Category, fired by [SenderRuleSuggestionCard]'s accept
 * button on the notification Detail screen.
 *
 * Resolution ladder for the destination Category:
 *   1. Category whose id literally equals `cat-onboarding-important_priority`
 *      (the seeded onboarding "중요 알림" Category — see
 *      `OnboardingQuickStartCategoryApplier`).
 *   2. First Category by ascending [Category.order] whose `action` is
 *      [CategoryAction.PRIORITY].
 *   3. Otherwise — return [Outcome.NoPriorityCategory] without writing.
 *      The caller is expected to surface a picker / inline guidance per
 *      Plan §Task 7. Returning early here keeps the rule store clean of
 *      categoryless SENDER rows that the user never explicitly chose to keep.
 *
 * Two side effects when [Outcome.Attached]:
 *  - [Ports.upsertRule] persists a SENDER rule built via [RuleDraftFactory.create].
 *  - [Ports.appendRuleIdToCategory] adds the rule id to the destination
 *    Category's `ruleIds` (dedup at the production sink).
 *
 * Both writes happen sequentially. A partial failure (rule persisted but
 * category attach throws) is acceptable — the next Detail entry's `shouldShow`
 * guard sees the SENDER rule and suppresses the suggestion card; the rule
 * itself surfaces in `RulesScreen` for follow-up. Per Plan §Risks.
 */
class AcceptSenderSuggestionUseCase(
    private val ports: Ports,
    private val ruleDraftFactory: RuleDraftFactory = RuleDraftFactory(),
) {

    interface Ports {
        suspend fun upsertRule(rule: RuleUiModel)
        suspend fun appendRuleIdToCategory(categoryId: String, ruleId: String)
    }

    sealed interface Outcome {
        /** Rule persisted + appended to [categoryId]. */
        data class Attached(val categoryId: String, val ruleId: String) : Outcome

        /** No PRIORITY Category exists; caller should surface a picker. */
        data object NoPriorityCategory : Outcome

        /** Title was blank after trim; nothing was written. */
        data object BlankTitle : Outcome
    }

    suspend fun accept(
        title: String,
        categories: List<Category>,
    ): Outcome {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return Outcome.BlankTitle

        val destination = resolvePriorityCategory(categories)
            ?: return Outcome.NoPriorityCategory

        val rule = ruleDraftFactory.create(
            title = "$trimmedTitle 발신자",
            matchValue = trimmedTitle,
            type = RuleTypeUi.SENDER,
            enabled = true,
            draft = false,
        )
        ports.upsertRule(rule)
        ports.appendRuleIdToCategory(destination.id, rule.id)
        return Outcome.Attached(categoryId = destination.id, ruleId = rule.id)
    }

    /**
     * Pick the destination PRIORITY Category. The seeded
     * `cat-onboarding-important_priority` wins by id even if its `order` is
     * later — the seeded default carries product intent. Otherwise the
     * earliest PRIORITY Category by `order` wins so the user's manual
     * reordering is respected. Falls back to `null` (→ NoPriorityCategory)
     * only when no PRIORITY Category exists at all.
     */
    private fun resolvePriorityCategory(categories: List<Category>): Category? {
        val pinned = categories.firstOrNull { it.id == IMPORTANT_PRIORITY_ID && it.action == CategoryAction.PRIORITY }
        if (pinned != null) return pinned
        return categories
            .filter { it.action == CategoryAction.PRIORITY }
            .minByOrNull { it.order }
    }

    companion object {
        /**
         * Stable id of the seeded "중요 알림" onboarding Category. Mirrored
         * from `OnboardingQuickStartCategoryApplier` — kept here as a string
         * constant so tests don't need to import the onboarding module. If
         * the user deleted the seeded Category (or it was never seeded), the
         * fallback branch picks the first PRIORITY Category by `order`.
         */
        const val IMPORTANT_PRIORITY_ID: String = "cat-onboarding-important_priority"
    }
}
