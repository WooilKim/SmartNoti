package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PassthroughReviewReclassifyDispatcher] coordinates the "review queue" inline
 * actions on the passthrough (PRIORITY) review screen:
 *
 * - `reclassify(notification, action)` must apply the existing
 *   [NotificationFeedbackPolicy] to update the notification status + reason
 *   tags, persist the updated notification through the repository hook, and
 *   (when `createRule = true`) also upsert a matching rule through the rules
 *   repository hook.
 * - `buildRuleDraft(notification, action)` returns the rule that would be
 *   created without persisting it, so the UI can navigate to a rule-editor
 *   flow without side effects.
 *
 * The dispatcher is deliberately framework-free so these tests stay pure JVM.
 */
class PassthroughReviewReclassifyDispatcherTest {

    private class FakeNotificationSink {
        val updated = mutableListOf<NotificationUiModel>()

        suspend fun update(notification: NotificationUiModel) {
            updated += notification
        }
    }

    private class FakeRuleSink {
        val upserted = mutableListOf<RuleUiModel>()

        suspend fun upsert(rule: RuleUiModel) {
            upserted += rule
        }
    }

    @Test
    fun reclassify_to_digest_persists_updated_notification_and_rule() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = notifications::update,
            upsertRule = rules::upsert,
        )

        val outcome = dispatcher.reclassify(
            notification = samplePassthrough(),
            action = RuleActionUi.DIGEST,
            createRule = true,
        )

        assertEquals(ReclassifyOutcome.UPDATED_WITH_RULE, outcome)
        assertEquals(1, notifications.updated.size)
        val updated = notifications.updated.single()
        assertEquals(NotificationStatusUi.DIGEST, updated.status)
        assertTrue(updated.reasonTags.contains("사용자 규칙"))
        assertEquals(1, rules.upserted.size)
        assertEquals(RuleActionUi.DIGEST, rules.upserted.single().action)
    }

    @Test
    fun reclassify_to_silent_persists_updated_notification_and_rule() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = notifications::update,
            upsertRule = rules::upsert,
        )

        val outcome = dispatcher.reclassify(
            notification = samplePassthrough(),
            action = RuleActionUi.SILENT,
            createRule = true,
        )

        assertEquals(ReclassifyOutcome.UPDATED_WITH_RULE, outcome)
        assertEquals(NotificationStatusUi.SILENT, notifications.updated.single().status)
        assertEquals(RuleActionUi.SILENT, rules.upserted.single().action)
    }

    @Test
    fun reclassify_without_rule_only_updates_notification() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = notifications::update,
            upsertRule = rules::upsert,
        )

        val outcome = dispatcher.reclassify(
            notification = samplePassthrough(),
            action = RuleActionUi.DIGEST,
            createRule = false,
        )

        assertEquals(ReclassifyOutcome.UPDATED, outcome)
        assertEquals(1, notifications.updated.size)
        assertTrue(
            "rule upsert should not fire when createRule=false",
            rules.upserted.isEmpty(),
        )
    }

    @Test
    fun rule_draft_is_pure_and_does_not_persist() {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = notifications::update,
            upsertRule = rules::upsert,
        )

        val draft = dispatcher.buildRuleDraft(
            notification = samplePassthrough(sender = "엄마"),
            action = RuleActionUi.ALWAYS_PRIORITY,
        )

        assertNotNull(draft)
        assertEquals(RuleTypeUi.PERSON, draft.type)
        assertEquals("엄마", draft.matchValue)
        assertEquals(RuleActionUi.ALWAYS_PRIORITY, draft.action)
        assertTrue(
            "buildRuleDraft must not write to repositories",
            notifications.updated.isEmpty() && rules.upserted.isEmpty(),
        )
    }

    @Test
    fun reclassify_rejects_contextual_action() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = notifications::update,
            upsertRule = rules::upsert,
        )

        val outcome = dispatcher.reclassify(
            notification = samplePassthrough(),
            action = RuleActionUi.CONTEXTUAL,
            createRule = true,
        )

        assertEquals(ReclassifyOutcome.IGNORED, outcome)
        assertTrue(notifications.updated.isEmpty())
        assertTrue(rules.upserted.isEmpty())
    }

    @Test
    fun reclassify_is_noop_when_notification_already_matches_target_status() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = notifications::update,
            upsertRule = rules::upsert,
        )

        // Notification is PRIORITY (passthrough); asking to "keep as priority" is a no-op.
        val outcome = dispatcher.reclassify(
            notification = samplePassthrough(),
            action = RuleActionUi.ALWAYS_PRIORITY,
            createRule = false,
        )

        assertEquals(ReclassifyOutcome.NOOP, outcome)
        assertTrue(notifications.updated.isEmpty())
        assertTrue(rules.upserted.isEmpty())
    }

    private fun samplePassthrough(sender: String? = "엄마") = NotificationUiModel(
        id = "n1",
        appName = "카카오톡",
        packageName = if (sender == null) "com.news.app" else "com.kakao.talk",
        sender = sender,
        title = sender ?: "뉴스 속보",
        body = "알림 본문",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.PRIORITY,
        reasonTags = listOf("발신자 있음"),
        score = null,
        isBundled = false,
    )

}
