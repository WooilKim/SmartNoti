package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationClassification
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

class NotificationClassifier(
    private val vipSenders: Set<String>,
    private val priorityKeywords: Set<String>,
    private val shoppingPackages: Set<String>,
    private val ruleConflictResolver: RuleConflictResolver = RuleConflictResolver(),
    private val categoryConflictResolver: CategoryConflictResolver = CategoryConflictResolver(),
) {
    /**
     * Classify [input] against the user's Rule / Category graph.
     *
     * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1
     * Task 4 rewired this to delegate action selection to
     * [CategoryConflictResolver]: matched Rules are lifted to their owning
     * Categories and the winning Category's action drives the decision.
     * The per-rule tie-break (via [RuleConflictResolver]) is still run
     * first so a base-vs-override duel resolves to a single Rule before we
     * ask "which Category owns that rule?" — avoiding a mid-flight swap
     * between unrelated Categories.
     *
     * When no user rule matches, the legacy heuristic chain (VIP /
     * priority keywords / quiet-hours shopping / duplicate burst / SILENT
     * default) still runs. Phase P2 will collapse these into Category-level
     * signals; Phase P1 keeps the behaviour unchanged so the migration is
     * invisible to users who haven't yet built a Category graph.
     */
    fun classify(
        input: ClassificationInput,
        rules: List<RuleUiModel> = emptyList(),
        categories: List<Category> = emptyList(),
    ): NotificationClassification {
        findMatchingRule(input, rules)?.let { rule ->
            val decision = resolveCategoryDecision(rule.id, categories)
            return NotificationClassification(
                decision = decision ?: NotificationDecision.SILENT,
                matchedRuleIds = listOf(rule.id),
            )
        }

        if (input.sender != null && input.sender in vipSenders) {
            return NotificationClassification(NotificationDecision.PRIORITY)
        }

        val content = listOf(input.title, input.body).joinToString(" ")
        if (priorityKeywords.any { keyword -> content.contains(keyword, ignoreCase = true) }) {
            return NotificationClassification(NotificationDecision.PRIORITY)
        }

        if (input.packageName in shoppingPackages && input.quietHours) {
            return NotificationClassification(NotificationDecision.DIGEST)
        }

        if (input.duplicateCountInWindow >= 3) {
            return NotificationClassification(NotificationDecision.DIGEST)
        }

        return NotificationClassification(NotificationDecision.SILENT)
    }

    /**
     * Lift the matched [ruleId] to its owning Category (via
     * [CategoryConflictResolver]) and map the winning action to a
     * [NotificationDecision]. Returns null when no Category owns the rule —
     * the classifier falls back to SILENT in that case so an orphaned rule
     * cannot crash the notifier pipeline.
     */
    private fun resolveCategoryDecision(
        ruleId: String,
        categories: List<Category>,
    ): NotificationDecision? {
        val owning = categories.filter { it.ruleIds.contains(ruleId) }
        val winner = categoryConflictResolver.resolve(
            matched = owning,
            allCategories = categories,
        ) ?: return null
        return winner.action.toDecision()
    }

    /**
     * Picks the user rule that applies to [input], delegating conflict
     * resolution to [RuleConflictResolver].
     */
    private fun findMatchingRule(
        input: ClassificationInput,
        rules: List<RuleUiModel>,
    ): RuleUiModel? {
        val content = listOf(input.title, input.body).joinToString(" ")
        val matched = rules.filter { rule -> rule.enabled && matches(rule, input, content) }
        return ruleConflictResolver.resolve(matched = matched, allRules = rules)
    }

    private fun matches(rule: RuleUiModel, input: ClassificationInput, content: String): Boolean =
        when (rule.type) {
            RuleTypeUi.PERSON -> !input.sender.isNullOrBlank() &&
                input.sender.equals(rule.matchValue, ignoreCase = true)
            RuleTypeUi.APP -> input.packageName.equals(rule.matchValue, ignoreCase = true)
            RuleTypeUi.KEYWORD -> rule.matchValue
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .any { keyword -> content.contains(keyword, ignoreCase = true) }
            RuleTypeUi.SCHEDULE -> input.hourOfDay != null &&
                matchesSchedule(rule.matchValue, input.hourOfDay)
            RuleTypeUi.REPEAT_BUNDLE -> matchesRepeatBundleThreshold(
                rule.matchValue,
                input.duplicateCountInWindow,
            )
        }

    private fun matchesSchedule(schedule: String, hourOfDay: Int): Boolean {
        val parts = schedule.split('-')
        if (parts.size != 2) return false

        val start = parts[0].toIntOrNull() ?: return false
        val end = parts[1].toIntOrNull() ?: return false

        return if (start <= end) {
            hourOfDay in start until end
        } else {
            hourOfDay >= start || hourOfDay < end
        }
    }

    private fun matchesRepeatBundleThreshold(matchValue: String, duplicateCountInWindow: Int): Boolean {
        val threshold = matchValue.toIntOrNull() ?: return false
        return threshold > 0 && duplicateCountInWindow >= threshold
    }

    private fun CategoryAction.toDecision(): NotificationDecision = when (this) {
        CategoryAction.PRIORITY -> NotificationDecision.PRIORITY
        CategoryAction.DIGEST -> NotificationDecision.DIGEST
        CategoryAction.SILENT -> NotificationDecision.SILENT
        CategoryAction.IGNORE -> NotificationDecision.IGNORE
    }
}
