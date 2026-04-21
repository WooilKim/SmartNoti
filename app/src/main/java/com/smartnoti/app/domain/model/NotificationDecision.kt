package com.smartnoti.app.domain.model

enum class NotificationDecision {
    PRIORITY,
    DIGEST,
    SILENT,
}

/**
 * Result of [com.smartnoti.app.domain.usecase.NotificationClassifier.classify].
 *
 * Bundles the routing [decision] with the ordered list of user rule ids
 * ([matchedRuleIds]) that produced it. Classifier-internal signals
 * ("VIP sender", "priority keyword", "quiet hours shopping", "duplicate burst")
 * are deliberately excluded from this list — those stay as free-form reason
 * tags on [NotificationUiModel] (plan `rules-ux-v2-inbox-restructure` Phase B
 * Task 2) so the Detail screen can split them from user-editable rule hits.
 *
 * When no user rule fired, [matchedRuleIds] is an empty list (never null).
 */
data class NotificationClassification(
    val decision: NotificationDecision,
    val matchedRuleIds: List<String> = emptyList(),
)

data class ClassificationInput(
    val sender: String? = null,
    val packageName: String,
    val title: String = "",
    val body: String = "",
    val quietHours: Boolean = false,
    val duplicateCountInWindow: Int = 0,
    val hourOfDay: Int? = null,
)
