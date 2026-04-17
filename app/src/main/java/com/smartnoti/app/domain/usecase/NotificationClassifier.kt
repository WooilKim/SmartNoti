package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.ClassificationInput
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
    ): NotificationDecision {
        findMatchingRule(input, rules)?.let { rule ->
            return rule.action.toDecision()
        }

        if (input.sender != null && input.sender in vipSenders) {
            return NotificationDecision.PRIORITY
        }

        val content = listOf(input.title, input.body).joinToString(" ")
        if (priorityKeywords.any { keyword -> content.contains(keyword, ignoreCase = true) }) {
            return NotificationDecision.PRIORITY
        }

        if (input.packageName in shoppingPackages && input.quietHours) {
            return NotificationDecision.DIGEST
        }

        if (input.duplicateCountInWindow >= 3) {
            return NotificationDecision.DIGEST
        }

        return NotificationDecision.SILENT
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
                RuleTypeUi.KEYWORD -> content.contains(rule.matchValue, ignoreCase = true)
                else -> false
            }
        }
    }

    private fun RuleActionUi.toDecision(): NotificationDecision = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> NotificationDecision.PRIORITY
        RuleActionUi.DIGEST -> NotificationDecision.DIGEST
        RuleActionUi.SILENT -> NotificationDecision.SILENT
        RuleActionUi.CONTEXTUAL -> NotificationDecision.SILENT
    }
}
