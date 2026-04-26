package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietHoursExplainerBuilderTest {

    private val builder = QuietHoursExplainerBuilder()

    @Test
    fun overnight_window_with_quiet_hours_tag_and_digest_emits_explainer() {
        val explainer = builder.build(
            reasonTags = listOf("조용한 시간"),
            status = NotificationStatusUi.DIGEST,
            startHour = 23,
            endHour = 7,
        )

        assertNotNull(explainer)
        val message = explainer!!.message
        assertTrue("expected '23시' in message: $message", message.contains("23시"))
        assertTrue("expected '7시' in message: $message", message.contains("7시"))
        assertTrue("expected '익일' in overnight message: $message", message.contains("익일"))
        assertTrue("expected '자동으로 모아뒀' in message: $message", message.contains("자동으로 모아뒀"))
    }

    @Test
    fun same_day_window_omits_익일_label() {
        val explainer = builder.build(
            reasonTags = listOf("조용한 시간"),
            status = NotificationStatusUi.DIGEST,
            startHour = 14,
            endHour = 16,
        )

        assertNotNull(explainer)
        val message = explainer!!.message
        assertTrue("expected '14시' in message: $message", message.contains("14시"))
        assertTrue("expected '16시' in message: $message", message.contains("16시"))
        assertTrue(
            "expected same-day message to omit '익일': $message",
            !message.contains("익일"),
        )
        assertTrue("expected '자동으로 모아뒀' in message: $message", message.contains("자동으로 모아뒀"))
    }

    @Test
    fun priority_status_returns_null_even_when_tag_present() {
        val explainer = builder.build(
            reasonTags = listOf("조용한 시간"),
            status = NotificationStatusUi.PRIORITY,
            startHour = 23,
            endHour = 7,
        )

        assertNull(explainer)
    }

    @Test
    fun missing_quiet_hours_tag_returns_null() {
        val explainer = builder.build(
            reasonTags = listOf("발신자 있음"),
            status = NotificationStatusUi.DIGEST,
            startHour = 23,
            endHour = 7,
        )

        assertNull(explainer)
    }

    @Test
    fun empty_reason_tags_returns_null() {
        val explainer = builder.build(
            reasonTags = emptyList(),
            status = NotificationStatusUi.DIGEST,
            startHour = 23,
            endHour = 7,
        )

        assertNull(explainer)
    }

    @Test
    fun coexisting_user_rule_tag_suppresses_explainer() {
        // 사용자 규칙이 함께 매치된 케이스: 사용자 규칙이 결정적이므로 quiet-hours 는 부수 신호.
        // 규칙 매치를 더 정확히 표현하기 위해 본 explainer 는 null (Detail 의 다른 카드에서 규칙을 설명).
        val explainer = builder.build(
            reasonTags = listOf("조용한 시간", "사용자 규칙"),
            status = NotificationStatusUi.DIGEST,
            startHour = 23,
            endHour = 7,
        )

        assertNull(explainer)
    }
}
