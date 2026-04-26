package com.smartnoti.app.domain.model

/**
 * UI-layer representation of a user rule.
 *
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1
 * Task 4: the `action` field was removed from this model. A rule is a pure
 * condition matcher — the action (PRIORITY / DIGEST / SILENT / IGNORE) now
 * lives on the owning [Category]. UI / classifier sites that need an
 * action per rule compose it from the Category graph via
 * [com.smartnoti.app.domain.usecase.RuleCategoryActionIndex].
 *
 * @property overrideOf Optional id of another rule that this rule overrides.
 *   When set, [RuleConflictResolver][com.smartnoti.app.domain.usecase.RuleConflictResolver]
 *   prefers this rule over the referenced base when both matched against the
 *   same notification. `null` means the rule is a plain base-tier rule.
 * @property draft Plan `docs/plans/2026-04-26-rule-explicit-draft-flag.md`.
 *   `true` immediately after the rule editor saves a brand-new rule and the
 *   user has not yet picked a Category in the post-save sheet — RulesScreen
 *   surfaces these in the loud "작업 필요" sub-bucket. Flipped to `false`
 *   the first time the rule is attached to a Category (the flip is sticky:
 *   removing the rule from every Category later does NOT bring `draft`
 *   back to `true`) or when the user explicitly chooses "분류 없이 보류" in
 *   the assignment sheet. Pure UI/UX hint — the classifier does not read
 *   this field, so the contract for unassigned rules (SILENT fall-through)
 *   stays identical regardless of `draft` value. Default `false` so legacy
 *   7-column DataStore rows decode into the quieter "보류" sub-bucket
 *   instead of suddenly shouting "작업 필요" at every existing user.
 */
data class RuleUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: RuleTypeUi,
    val enabled: Boolean,
    val matchValue: String = "",
    val overrideOf: String? = null,
    val draft: Boolean = false,
)

enum class RuleTypeUi {
    PERSON,
    APP,
    KEYWORD,
    SCHEDULE,
    REPEAT_BUNDLE,
}

/**
 * Historically stored on every [RuleUiModel]; as of plan Phase P1 Task 4 it
 * now lives only on [Category] and is used by UI surfaces that still want
 * to present an action label next to a rule (list grouping, filter chips,
 * editor draft). The enum itself remains so those UI sites keep compiling —
 * they look the action up via [com.smartnoti.app.domain.usecase.RuleCategoryActionIndex]
 * instead of reading it from the rule row.
 */
enum class RuleActionUi {
    ALWAYS_PRIORITY,
    DIGEST,
    SILENT,
    CONTEXTUAL,

    /**
     * Delete-level action. Category-level IGNORE is the canonical home
     * (plan `2026-04-22-categories-split-rules-actions.md` Phase P1); this
     * enum value is kept so the editor can still offer "무시" in the action
     * dropdown and the group/filter builders can still surface IGNORE as a
     * bucket label.
     */
    IGNORE,
}
