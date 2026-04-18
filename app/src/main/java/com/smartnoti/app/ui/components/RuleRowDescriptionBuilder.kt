package com.smartnoti.app.ui.components

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

data class RuleRowDescription(
    val primaryText: String,
    val secondaryText: String,
)

class RuleRowDescriptionBuilder {
    fun build(rule: RuleUiModel): RuleRowDescription {
        return when (rule.type) {
            RuleTypeUi.APP -> RuleRowDescription(
                primaryText = "${rule.title} 알림은 ${rule.action.toActionPhrase()}",
                secondaryText = "패키지 · ${rule.matchValue}",
            )
            RuleTypeUi.KEYWORD -> RuleRowDescription(
                primaryText = "'${rule.matchValue.toKeywordDisplay()}'가 들어오면 ${rule.action.toActionPhrase()}",
                secondaryText = "키워드 기준",
            )
            RuleTypeUi.SCHEDULE -> RuleRowDescription(
                primaryText = "${rule.matchValue.toScheduleDisplay()}에는 ${rule.action.toActionPhrase()}",
                secondaryText = "시간대 기준",
            )
            RuleTypeUi.REPEAT_BUNDLE -> RuleRowDescription(
                primaryText = "같은 알림이 ${rule.matchValue.filter(Char::isDigit)}회 이상 반복되면 ${rule.action.toActionPhrase()}",
                secondaryText = "반복 기준",
            )
            RuleTypeUi.PERSON -> RuleRowDescription(
                primaryText = "${rule.title} 연락은 ${rule.action.toActionPhrase()}",
                secondaryText = "발신자 기준",
            )
        }
    }

    private fun RuleActionUi.toActionPhrase(): String = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> "바로 보여줘요"
        RuleActionUi.DIGEST -> "Digest로 묶어요"
        RuleActionUi.SILENT -> "조용히 정리해요"
        RuleActionUi.CONTEXTUAL -> "상황에 따라 자동 분류해요"
    }

    private fun String.toKeywordDisplay(): String = split(',').joinToString(", ") { it.trim() }

    private fun String.toScheduleDisplay(): String {
        val parts = split('-')
        if (parts.size != 2) return this
        return "${parts[0].padStart(2, '0')}:00 ~ ${parts[1].padStart(2, '0')}:00"
    }
}
