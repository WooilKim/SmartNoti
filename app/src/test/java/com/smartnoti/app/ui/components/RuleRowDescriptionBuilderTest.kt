package com.smartnoti.app.ui.components

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleRowDescriptionBuilderTest {

    private val builder = RuleRowDescriptionBuilder()

    @Test
    fun app_rule_reads_like_user_facing_action_sentence() {
        val description = builder.build(
            rule(
                title = "쿠팡",
                type = RuleTypeUi.APP,
                action = RuleActionUi.DIGEST,
                matchValue = "com.coupang.mobile",
            )
        )

        assertEquals("쿠팡 알림은 Digest로 묶어요", description.primaryText)
        assertEquals("패키지 · com.coupang.mobile", description.secondaryText)
    }

    @Test
    fun keyword_rule_formats_keywords_as_human_sentence() {
        val description = builder.build(
            rule(
                title = "배포 키워드",
                type = RuleTypeUi.KEYWORD,
                action = RuleActionUi.ALWAYS_PRIORITY,
                matchValue = "배포,장애,긴급",
            )
        )

        assertEquals("'배포, 장애, 긴급'가 들어오면 바로 보여줘요", description.primaryText)
        assertEquals("키워드 기준", description.secondaryText)
    }

    @Test
    fun schedule_rule_formats_time_window_for_quick_scanning() {
        val description = builder.build(
            rule(
                title = "야간",
                type = RuleTypeUi.SCHEDULE,
                action = RuleActionUi.SILENT,
                matchValue = "23-7",
            )
        )

        assertEquals("23:00 ~ 07:00에는 조용히 정리해요", description.primaryText)
        assertEquals("시간대 기준", description.secondaryText)
    }

    @Test
    fun repeat_rule_explains_threshold_in_sentence_form() {
        val description = builder.build(
            rule(
                title = "반복 푸시",
                type = RuleTypeUi.REPEAT_BUNDLE,
                action = RuleActionUi.ALWAYS_PRIORITY,
                matchValue = "5",
            )
        )

        assertEquals("같은 알림이 5회 이상 반복되면 바로 보여줘요", description.primaryText)
        assertEquals("반복 기준", description.secondaryText)
    }

    private fun rule(
        title: String,
        type: RuleTypeUi,
        action: RuleActionUi,
        matchValue: String,
    ) = RuleUiModel(
        id = "rule:$title",
        title = title,
        subtitle = "unused",
        type = type,
        action = action,
        enabled = true,
        matchValue = matchValue,
    )
}
