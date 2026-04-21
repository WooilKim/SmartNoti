package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationClassification
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

class NotificationClassifier(
    private val vipSenders: Set<String>,
    private val priorityKeywords: Set<String>,
    private val shoppingPackages: Set<String>,
    private val ruleConflictResolver: RuleConflictResolver = RuleConflictResolver(),
) {
    fun classify(
        input: ClassificationInput,
        rules: List<RuleUiModel> = emptyList(),
    ): NotificationClassification {
        findMatchingRule(input, rules)?.let { rule ->
            return NotificationClassification(
                decision = rule.action.toDecision(),
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
     * Picks the user rule that applies to [input], delegating conflict
     * resolution to [RuleConflictResolver].
     *
     * Plan `rules-ux-v2-inbox-restructure` Phase C Task 2: the flat
     * first-match-wins loop was replaced with a collect-all-matches pass +
     * resolver call so that when a base rule AND its override both fire the
     * override wins. Same-tier ties still break by list order (the resolver
     * consults [rules] directly).
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

    private fun RuleActionUi.toDecision(): NotificationDecision = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> NotificationDecision.PRIORITY
        RuleActionUi.DIGEST -> NotificationDecision.DIGEST
        RuleActionUi.SILENT -> NotificationDecision.SILENT
        RuleActionUi.CONTEXTUAL -> NotificationDecision.SILENT
        // Rule-driven IGNORE routing — plan
        // `2026-04-21-ignore-tier-fourth-decision`. Task 2 wired the enum
        // value through persistence; Task 3 locks in the classifier contract
        // via `NotificationClassifierTest`: IGNORE is *only* reachable through
        // a matching user rule. The VIP / priority-keyword / quiet-hours /
        // repeat-burst / default branches above intentionally never produce
        // IGNORE. Overrides still apply — a base IGNORE rule may be overridden
        // by an ALWAYS_PRIORITY override via `RuleConflictResolver`.
        RuleActionUi.IGNORE -> NotificationDecision.IGNORE
    }
}
