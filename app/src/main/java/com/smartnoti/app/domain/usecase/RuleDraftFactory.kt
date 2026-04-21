package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

class RuleDraftFactory {
    fun create(
        title: String,
        matchValue: String,
        type: RuleTypeUi,
        action: RuleActionUi,
        enabled: Boolean = true,
        existingId: String? = null,
        overrideOf: String? = null,
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
            subtitle = action.toSubtitle(),
            type = type,
            action = action,
            enabled = enabled,
            matchValue = normalizedMatchValue,
            overrideOf = normalizedOverrideOf,
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

    private fun RuleActionUi.toSubtitle(): String = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> "항상 바로 보기"
        RuleActionUi.DIGEST -> "Digest로 묶기"
        RuleActionUi.SILENT -> "조용히 정리"
        RuleActionUi.CONTEXTUAL -> "상황에 따라 자동 분류"
        // Copy finalized in Task 5 of plan
        // `2026-04-21-ignore-tier-fourth-decision`. Stub text keeps drafts
        // valid until the rule editor surfaces IGNORE to users.
        RuleActionUi.IGNORE -> "무시 (즉시 삭제)"
    }
}
