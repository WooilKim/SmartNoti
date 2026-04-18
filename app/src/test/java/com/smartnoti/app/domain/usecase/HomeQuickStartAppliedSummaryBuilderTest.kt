package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQuickStartAppliedSummaryBuilderTest {

    private val builder = HomeQuickStartAppliedSummaryBuilder()

    @Test
    fun all_starter_rules_present_returns_combined_summary() {
        val summary = builder.build(
            listOf(
                importantRule(),
                promoRule(),
                repeatRule(),
            ),
        )

        requireNotNull(summary)
        assertEquals("빠른 시작 추천이 적용되어 있어요", summary.title)
        assertTrue(summary.body.contains("프로모션·반복 알림"))
        assertTrue(summary.body.contains("중요한 알림"))
    }

    @Test
    fun important_only_returns_priority_focused_summary() {
        val summary = builder.build(listOf(importantRule()))

        requireNotNull(summary)
        assertEquals("결제·배송·인증 알림을 우선 전달하고 있어요", summary.body)
    }

    @Test
    fun non_starter_rules_return_null() {
        val summary = builder.build(
            listOf(
                RuleUiModel(
                    id = "person:엄마",
                    title = "엄마",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.PERSON,
                    action = RuleActionUi.ALWAYS_PRIORITY,
                    enabled = true,
                    matchValue = "엄마",
                ),
            ),
        )

        assertNull(summary)
    }

    private fun promoRule() = RuleUiModel(
        id = "keyword:promo",
        title = "프로모션 알림",
        subtitle = "Digest로 묶기",
        type = RuleTypeUi.KEYWORD,
        action = RuleActionUi.DIGEST,
        enabled = true,
        matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택",
    )

    private fun repeatRule() = RuleUiModel(
        id = "repeat_bundle:3",
        title = "반복 알림",
        subtitle = "Digest로 묶기",
        type = RuleTypeUi.REPEAT_BUNDLE,
        action = RuleActionUi.DIGEST,
        enabled = true,
        matchValue = "3",
    )

    private fun importantRule() = RuleUiModel(
        id = "keyword:important",
        title = "중요 알림",
        subtitle = "항상 바로 보기",
        type = RuleTypeUi.KEYWORD,
        action = RuleActionUi.ALWAYS_PRIORITY,
        enabled = true,
        matchValue = "인증번호,결제,배송,출발",
    )
}
