package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleOrderingTest {

    @Test
    fun move_up_swaps_rule_with_previous_position() {
        val reordered = moveRule(
            rules = sampleRules(),
            ruleId = "r2",
            direction = RuleMoveDirection.UP,
        )

        assertEquals(listOf("r2", "r1", "r3"), reordered.map { it.id })
    }

    @Test
    fun move_down_at_bottom_keeps_order_unchanged() {
        val original = sampleRules()

        val reordered = moveRule(
            rules = original,
            ruleId = "r3",
            direction = RuleMoveDirection.DOWN,
        )

        assertEquals(original.map { it.id }, reordered.map { it.id })
    }

    private fun sampleRules(): List<RuleUiModel> = listOf(
        RuleUiModel("r1", "엄마", "항상 바로 보기", RuleTypeUi.PERSON, RuleActionUi.ALWAYS_PRIORITY, true, "엄마"),
        RuleUiModel("r2", "쿠팡", "Digest로 묶기", RuleTypeUi.APP, RuleActionUi.DIGEST, true, "com.coupang.mobile"),
        RuleUiModel("r3", "인증번호", "즉시 전달", RuleTypeUi.KEYWORD, RuleActionUi.ALWAYS_PRIORITY, true, "인증번호"),
    )
}
