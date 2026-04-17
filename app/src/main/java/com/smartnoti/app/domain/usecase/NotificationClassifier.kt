package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision

class NotificationClassifier(
    private val vipSenders: Set<String>,
    private val priorityKeywords: Set<String>,
    private val shoppingPackages: Set<String>,
) {
    fun classify(input: ClassificationInput): NotificationDecision {
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
}
