package com.smartnoti.app.domain.model

/**
 * Pure condition-matcher representation of a rule, introduced by plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 2.
 *
 * This sits alongside the existing [RuleUiModel] during Phase P1. Task 4 will
 * remove the `action` field from [RuleUiModel] and make this `Rule` the only
 * rule-shaped type in the codebase. Until then, [RuleUiModel] stays the
 * storage / UI model and this `Rule` is used only by the Category tests and
 * future Category-driven classifier glue.
 *
 * @property id Stable unique id (same id space as [RuleUiModel.id]).
 * @property type Matcher type (person, app, keyword, schedule, repeat bundle).
 * @property matchValue Matcher value (e.g. a package name or keyword).
 * @property overrideOf Optional id of the base rule this rule overrides in the
 *   override chain introduced by the Phase C override validator. Null for
 *   base-tier rules.
 * @property draft Plan `docs/plans/2026-04-26-rule-explicit-draft-flag.md`.
 *   Mirrors [RuleUiModel.draft] so the two models stay shape-compatible
 *   while they coexist. `true` on freshly saved rules until the user attaches
 *   the rule to a Category or explicitly parks it; `false` otherwise. Pure
 *   UI/UX hint with no classifier impact.
 */
data class Rule(
    val id: String,
    val type: RuleTypeUi,
    val matchValue: String,
    val overrideOf: String? = null,
    val draft: Boolean = false,
)
