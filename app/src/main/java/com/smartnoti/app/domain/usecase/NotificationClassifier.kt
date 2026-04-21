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

    private fun findMatchingRule(
        input: ClassificationInput,
        rules: List<RuleUiModel>,
    ): RuleUiModel? {
        val content = listOf(input.title, input.body).joinToString(" ")
        return rules.firstOrNull { rule ->
            rule.enabled && when (rule.type) {
                RuleTypeUi.PERSON -> !input.sender.isNullOrBlank() && input.sender.equals(rule.matchValue, ignoreCase = true)
                RuleTypeUi.APP -> input.packageName.equals(rule.matchValue, ignoreCase = true)
                RuleTypeUi.KEYWORD -> rule.matchValue
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .any { keyword -> content.contains(keyword, ignoreCase = true) }
                RuleTypeUi.SCHEDULE -> input.hourOfDay != null && matchesSchedule(rule.matchValue, input.hourOfDay)
                RuleTypeUi.REPEAT_BUNDLE -> matchesRepeatBundleThreshold(rule.matchValue, input.duplicateCountInWindow)
            }
        }
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
    }
}
