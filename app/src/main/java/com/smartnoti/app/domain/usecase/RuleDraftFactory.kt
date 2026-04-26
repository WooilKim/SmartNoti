package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Builds [RuleUiModel] drafts from rule editor input.
 *
 * Plan `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md` Task 2:
 * the `action` parameter was removed. A Rule is a pure condition matcher;
 * the action lives on the owning [com.smartnoti.app.domain.model.Category]
 * and is selected after save via the post-save assignment sheet, not in the
 * editor. Subtitle is intentionally left blank — RuleRow renders the action
 * label by deriving it from the owning Category at presentation time.
 *
 * Plan `docs/plans/2026-04-26-rule-explicit-draft-flag.md` Task 4: the
 * optional `draft` parameter lets the rule editor pass `true` for brand-new
 * rules (so RulesScreen surfaces them in "작업 필요" until the user attaches
 * them to a Category) while preserving the existing rule's `draft` value on
 * edit. Default is `false` so existing call sites that auto-route the rule
 * into a Category (onboarding quick-start preset, etc.) keep producing
 * non-draft rules and are not impacted.
 */
class RuleDraftFactory {
    fun create(
        title: String,
        matchValue: String,
        type: RuleTypeUi,
        enabled: Boolean = true,
        existingId: String? = null,
        overrideOf: String? = null,
        draft: Boolean = false,
    ): RuleUiModel {
        val normalizedTitle = title.trim()
        val normalizedMatchValue = normalizeMatchValue(type, matchValue)
        // Plan rules-ux-v2-inbox-restructure Phase C Task 4: blank overrideOf
        // strings collapse to null so the dropdown's "(선택)" placeholder
        // doesn't sneak an invalid foreign key into storage.
        val normalizedOverrideOf = overrideOf?.takeIf { it.isNotBlank() }
        return RuleUiModel(
            id = existingId ?: "${type.name.lowercase()}:$normalizedMatchValue",
            title = normalizedTitle,
            subtitle = "",
            type = type,
            enabled = enabled,
            matchValue = normalizedMatchValue,
            overrideOf = normalizedOverrideOf,
            draft = draft,
        )
    }

    private fun normalizeMatchValue(type: RuleTypeUi, raw: String): String {
        val trimmed = raw.trim()
        return when (type) {
            RuleTypeUi.KEYWORD -> trimmed
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(",")
            RuleTypeUi.SCHEDULE -> normalizeSchedule(trimmed)
            RuleTypeUi.REPEAT_BUNDLE -> normalizeRepeatBundleThreshold(trimmed)
            else -> trimmed
        }
    }

    private fun normalizeSchedule(raw: String): String {
        return raw.replace(" ", "")
    }

    private fun normalizeRepeatBundleThreshold(raw: String): String {
        return raw.filter(Char::isDigit).trimStart('0').ifEmpty { "0" }
    }
}
