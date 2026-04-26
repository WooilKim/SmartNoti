package com.smartnoti.app.domain.model

enum class NotificationDecision {
    PRIORITY,
    DIGEST,
    SILENT,

    /**
     * Rule-driven delete-level classification added by plan
     * `2026-04-21-ignore-tier-fourth-decision`. The classifier only reaches
     * this tier when a user rule with [com.smartnoti.app.domain.model.RuleActionUi.IGNORE]
     * matches — there is no automatic promotion. Downstream effects (tray
     * cancel without replacement alert, default-view filtering, opt-in
     * archive) land in Tasks 3–6 of that plan; Task 2 only guarantees the
     * enum value exists and round-trips through persistence.
     */
    IGNORE,
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
    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 3.
     *
     * User-tunable threshold for the duplicate-burst → DIGEST base heuristic.
     * Default = 3 to preserve historical behavior at every call site that has
     * not yet been migrated to thread the user's `SmartNotiSettings` value
     * through. The notifier's `processNotification` sets this from
     * `settings.duplicateDigestThreshold` so the dropdown takes effect on the
     * next notification.
     */
    val duplicateThreshold: Int = 3,
)
