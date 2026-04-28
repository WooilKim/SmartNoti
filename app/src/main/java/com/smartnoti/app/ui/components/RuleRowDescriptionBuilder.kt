package com.smartnoti.app.ui.components

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

data class RuleRowDescription(
    val primaryText: String,
    val secondaryText: String,
    val emphasisLabel: String? = null,
)

class RuleRowDescriptionBuilder {
    fun build(rule: RuleUiModel, action: RuleActionUi): RuleRowDescription {
        val emphasisLabel = if (rule.title in onboardingRecommendationTitles) {
            "온보딩 추천"
        } else {
            null
        }
        return when (rule.type) {
            RuleTypeUi.APP -> RuleRowDescription(
                primaryText = "${rule.title} 알림은 ${action.toActionPhrase()}",
                secondaryText = "패키지 · ${rule.matchValue}",
                emphasisLabel = emphasisLabel,
            )
            RuleTypeUi.KEYWORD -> RuleRowDescription(
                primaryText = "'${rule.matchValue.toKeywordDisplay()}'가 들어오면 ${action.toActionPhrase()}",
                secondaryText = "키워드 기준",
                emphasisLabel = emphasisLabel,
            )
            RuleTypeUi.SCHEDULE -> RuleRowDescription(
                primaryText = "${rule.matchValue.toScheduleDisplay()}에는 ${action.toActionPhrase()}",
                secondaryText = "시간대 기준",
                emphasisLabel = emphasisLabel,
            )
            RuleTypeUi.REPEAT_BUNDLE -> RuleRowDescription(
                primaryText = "같은 알림이 ${rule.matchValue.filter(Char::isDigit)}회 이상 반복되면 ${action.toActionPhrase()}",
                secondaryText = "반복 기준",
                emphasisLabel = emphasisLabel,
            )
            RuleTypeUi.PERSON -> RuleRowDescription(
                primaryText = "${rule.title} 연락은 ${action.toActionPhrase()}",
                secondaryText = "발신자 기준",
                emphasisLabel = emphasisLabel,
            )
            // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
            // Task 2 — natural-default description. SENDER differs from PERSON
            // in the matcher (substring on title vs exact on sender) but the
            // user-facing summary is intentionally similar so existing rule
            // rows render coherently. Task 6 may refine the copy.
            RuleTypeUi.SENDER -> RuleRowDescription(
                primaryText = "'${rule.matchValue}' 발신자는 ${action.toActionPhrase()}",
                secondaryText = "발신자 이름 기준",
                emphasisLabel = emphasisLabel,
            )
        }
    }

    private fun RuleActionUi.toActionPhrase(): String = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> "바로 보여줘요"
        RuleActionUi.DIGEST -> "Digest로 묶어요"
        RuleActionUi.SILENT -> "조용히 정리해요"
        RuleActionUi.CONTEXTUAL -> "상황에 따라 자동 분류해요"
        // Final copy landed in Task 5 of plan
        // `2026-04-21-ignore-tier-fourth-decision`.
        RuleActionUi.IGNORE -> "알림센터에서 즉시 삭제하고 앱에서도 숨겨요"
    }

    private fun String.toKeywordDisplay(): String = split(',').joinToString(", ") { it.trim() }

    private fun String.toScheduleDisplay(): String {
        val parts = split('-')
        if (parts.size != 2) return this
        return "${parts[0].padStart(2, '0')}:00 ~ ${parts[1].padStart(2, '0')}:00"
    }

    private companion object {
        val onboardingRecommendationTitles = setOf("프로모션 알림", "반복 알림", "중요 알림")
    }
}
