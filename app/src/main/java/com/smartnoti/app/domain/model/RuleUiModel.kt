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
}
