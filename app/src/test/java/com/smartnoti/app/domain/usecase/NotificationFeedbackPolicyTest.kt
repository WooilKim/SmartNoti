package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.SilentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFeedbackPolicyTest {

    private val policy = NotificationFeedbackPolicy()

    @Test
    fun feedback_action_updates_notification_status_and_reason_tags() {
        val updated = policy.applyAction(
            notification = sampleNotification(),
            action = RuleActionUi.DIGEST,
        )

        assertEquals(NotificationStatusUi.DIGEST, updated.status)
        assertTrue(updated.reasonTags.contains("사용자 규칙"))
    }

    @Test
    fun sender_feedback_creates_person_rule() {
        val rule = policy.toRule(
            notification = sampleNotification(sender = "엄마"),
            action = RuleActionUi.ALWAYS_PRIORITY,
        )

        assertEquals(RuleTypeUi.PERSON, rule.type)
        assertEquals("엄마", rule.matchValue)
    }

    @Test
    fun app_feedback_without_sender_creates_app_rule() {
        val rule = policy.toRule(
            notification = sampleNotification(sender = null),
            action = RuleActionUi.SILENT,
        )

        assertEquals(RuleTypeUi.APP, rule.type)
        assertEquals("com.news.app", rule.matchValue)
    }

    @Test
    fun mark_silent_processed_transitions_archived_to_processed_mode() {
        val archived = sampleNotification().copy(
            status = NotificationStatusUi.SILENT,
            silentMode = SilentMode.ARCHIVED,
        )

        val processed = policy.markSilentProcessed(archived)

        assertEquals(NotificationStatusUi.SILENT, processed.status)
        assertEquals(SilentMode.PROCESSED, processed.silentMode)
        assertTrue(processed.reasonTags.contains("사용자 처리"))
    }

    @Test
    fun mark_silent_processed_is_idempotent_when_already_processed() {
        val alreadyProcessed = sampleNotification().copy(
            status = NotificationStatusUi.SILENT,
            silentMode = SilentMode.PROCESSED,
            reasonTags = listOf("원본", "사용자 처리"),
        )

        val result = policy.markSilentProcessed(alreadyProcessed)

        assertEquals(SilentMode.PROCESSED, result.silentMode)
        assertEquals(1, result.reasonTags.count { it == "사용자 처리" })
    }

    @Test
    fun mark_silent_processed_is_a_noop_for_non_silent_notifications() {
        val priority = sampleNotification().copy(
            status = NotificationStatusUi.PRIORITY,
            silentMode = null,
        )

        val result = policy.markSilentProcessed(priority)

        assertSame(priority, result)
        assertNull(result.silentMode)
    }

    private fun sampleNotification(sender: String? = "엄마") = NotificationUiModel(
        id = "n1",
        appName = "카카오톡",
        packageName = if (sender == null) "com.news.app" else "com.kakao.talk",
        sender = sender,
        title = sender ?: "뉴스 속보",
        body = "알림 본문",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = listOf("원본"),
        score = null,
        isBundled = false,
    )
}
