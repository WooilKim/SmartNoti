package com.smartnoti.app.domain.model

/**
 * UI-layer representation of a user rule.
 *
 * @property overrideOf Optional id of another rule that this rule overrides.
 * When set, [RuleConflictResolver][com.smartnoti.app.domain.usecase.RuleConflictResolver]
 * prefers this rule over the referenced base when both matched against the same
 * notification. `null` means the rule is a plain base-tier rule. Plan
 * `rules-ux-v2-inbox-restructure` Phase C Task 1.
 */
data class RuleUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: RuleTypeUi,
    @Deprecated("Moved to Category.action. Removed in Phase P1 Task 4 of plan docs/plans/2026-04-22-categories-split-rules-actions.md.")
    val action: RuleActionUi,
    val enabled: Boolean,
    val matchValue: String = "",
    val overrideOf: String? = null,
)

enum class RuleTypeUi {
    PERSON,
    APP,
    KEYWORD,
    SCHEDULE,
    REPEAT_BUNDLE,
}

enum class RuleActionUi {
    ALWAYS_PRIORITY,
    DIGEST,
    SILENT,
    CONTEXTUAL,

    /**
     * Delete-level rule action added by plan
     * `2026-04-21-ignore-tier-fourth-decision` Task 2. Rules carrying this
     * action route matched notifications to [NotificationDecision.IGNORE] —
     * the classifier wiring lands in Task 3, the editor UI in Task 5, and the
     * Detail feedback button in Task 6a. Task 2 only introduces the enum
     * value and its Room persistence round-trip.
     */
    IGNORE,
}
