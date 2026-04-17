package com.smartnoti.app.domain.model

data class RuleUiModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: RuleTypeUi,
    val action: RuleActionUi,
    val enabled: Boolean,
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
