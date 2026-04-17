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
    ): RuleUiModel {
        val normalizedTitle = title.trim()
        val normalizedMatchValue = normalizeMatchValue(type, matchValue)
        return RuleUiModel(
            id = existingId ?: "${type.name.lowercase()}:$normalizedMatchValue",
            title = normalizedTitle,
            subtitle = action.toSubtitle(),
            type = type,
            action = action,
            enabled = enabled,
            matchValue = normalizedMatchValue,
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
            else -> trimmed
        }
    }

    private fun RuleActionUi.toSubtitle(): String = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> "항상 바로 보기"
        RuleActionUi.DIGEST -> "Digest로 묶기"
        RuleActionUi.SILENT -> "조용히 정리"
        RuleActionUi.CONTEXTUAL -> "상황에 따라 자동 분류"
    }
}
