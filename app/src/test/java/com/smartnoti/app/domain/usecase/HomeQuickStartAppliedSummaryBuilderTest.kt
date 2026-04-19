package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
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
            rules = listOf(
                importantRule(),
                promoRule(),
                repeatRule(),
            ),
            notifications = emptyList(),
        )

        requireNotNull(summary)
        assertEquals("빠른 시작 추천이 적용되어 있어요", summary.title)
        assertTrue(summary.body.contains("프로모션·반복 알림"))
        assertTrue(summary.body.contains("중요한 알림"))
    }

    @Test
    fun recent_effects_summarize_promo_repeat_and_priority_outcomes() {
        val summary = builder.build(
            rules = listOf(
                importantRule(),
                promoRule(),
                repeatRule(),
            ),
            notifications = listOf(
                notification(
                    id = "1",
                    appName = "쿠팡",
                    title = "(광고) 오늘만 특가",
                    status = NotificationStatusUi.DIGEST,
                    reasonTags = listOf("프로모션 알림", "사용자 규칙"),
                ),
                notification(
                    id = "2",
                    appName = "쿠팡",
                    title = "쿠폰이 도착했어요",
                    status = NotificationStatusUi.DIGEST,
                    reasonTags = listOf("프로모션 알림", "사용자 규칙"),
                ),
                notification(
                    id = "3",
                    appName = "네이버",
                    title = "재입고 알림",
                    status = NotificationStatusUi.DIGEST,
                    reasonTags = listOf("반복 알림", "사용자 규칙"),
                ),
                notification(
                    id = "4",
                    appName = "토스",
                    title = "결제가 완료됐어요",
                    status = NotificationStatusUi.PRIORITY,
                    reasonTags = listOf("중요 알림", "사용자 규칙"),
                ),
            ),
        )

        requireNotNull(summary)
        val effectBody = requireNotNull(summary.effectBody)
        assertEquals("최근 효과", summary.effectTitle)
        assertTrue(effectBody.contains("쿠팡 프로모션 알림 2건"))
        assertTrue(effectBody.contains("반복 알림 1건"))
        assertTrue(effectBody.contains("중요 알림 1건"))
    }

    @Test
    fun partial_recent_effects_include_waiting_copy_for_other_applied_presets() {
        val summary = builder.build(
            rules = listOf(
                importantRule(),
                promoRule(),
                repeatRule(),
            ),
            notifications = listOf(
                notification(
                    id = "1",
                    appName = "쿠팡",
                    title = "(광고) 오늘만 특가",
                    status = NotificationStatusUi.DIGEST,
                    reasonTags = listOf("프로모션 알림", "사용자 규칙"),
                ),
            ),
        )

        requireNotNull(summary)
        val effectBody = requireNotNull(summary.effectBody)
        assertEquals("최근 효과", summary.effectTitle)
        assertTrue(effectBody.contains("쿠팡 프로모션 알림 1건"))
        assertTrue(effectBody.contains("반복 알림 효과는 아직 확인 중이에요"))
        assertTrue(effectBody.contains("중요 알림 효과는 아직 확인 중이에요"))
    }

    @Test
    fun important_only_returns_priority_focused_summary() {
        val summary = builder.build(
            rules = listOf(importantRule()),
            notifications = emptyList(),
        )

        requireNotNull(summary)
        assertEquals("결제·배송·인증 알림을 우선 전달하고 있어요", summary.body)
    }

    @Test
    fun non_starter_rules_return_null() {
        val summary = builder.build(
            rules = listOf(
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
            notifications = emptyList(),
        )

        assertNull(summary)
    }

    @Test
    fun no_matching_recent_effects_returns_waiting_effect_copy() {
        val summary = builder.build(
            rules = listOf(promoRule()),
            notifications = listOf(
                notification(
                    id = "1",
                    appName = "카카오톡",
                    title = "친구가 메시지를 보냈어요",
                    status = NotificationStatusUi.PRIORITY,
                    reasonTags = listOf("발신자 있음"),
                ),
            ),
        )

        requireNotNull(summary)
        assertEquals("효과를 확인하는 중", summary.effectTitle)
        assertEquals(
            "실제 알림이 더 쌓이면 어떤 알림이 정리되고 있는지 여기서 바로 보여드릴게요",
            summary.effectBody,
        )
    }

    private fun notification(
        id: String,
        appName: String,
        title: String,
        status: NotificationStatusUi,
        reasonTags: List<String>,
    ) = NotificationUiModel(
        id = id,
        appName = appName,
        packageName = "pkg.$id",
        sender = null,
        title = title,
        body = "",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = reasonTags,
    )

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
