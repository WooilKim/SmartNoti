package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacementNotificationTextFormatterTest {

    @Test
    fun digest_explanation_mentions_digest_handling_and_top_reasons() {
        val text = ReplacementNotificationTextFormatter.explanationText(
            decision = NotificationDecision.DIGEST,
            reasonTags = listOf("쇼핑 앱", "반복 알림", "조용한 시간"),
        )

        assertEquals("원본 알림은 숨기고 Digest에 모아뒀어요 · 쇼핑 앱 · 반복 알림", text)
    }

    @Test
    fun silent_explanation_filters_generic_reason_tags() {
        val text = ReplacementNotificationTextFormatter.explanationText(
            decision = NotificationDecision.SILENT,
            reasonTags = listOf("발신자 있음", "사용자 규칙", "중요 키워드"),
        )

        assertEquals("원본 알림은 숨기고 조용히 보관했어요 · 사용자 규칙 · 중요 키워드", text)
    }

    @Test
    fun highlight_reasons_trims_deduplicates_and_limits_to_two_tags() {
        val reasons = ReplacementNotificationTextFormatter.highlightReasons(
            listOf(" 사용자 규칙 ", "사용자 규칙", "", "반복 알림", "조용한 시간"),
        )

        assertEquals(listOf("사용자 규칙", "반복 알림"), reasons)
    }

    @Test
    fun explanation_falls_back_to_handling_summary_when_no_useful_reasons_exist() {
        val text = ReplacementNotificationTextFormatter.explanationText(
            decision = NotificationDecision.DIGEST,
            reasonTags = listOf("발신자 있음", " "),
        )

        assertEquals("원본 알림은 숨기고 Digest에 모아뒀어요", text)
    }
}