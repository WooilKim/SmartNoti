package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * [BulkPassthroughReviewReclassifyDispatcher] is a thin loop wrapper around the
 * single-row [PassthroughReviewReclassifyDispatcher] used by the PriorityScreen
 * multi-select ActionBar ("→ Digest" / "→ 조용히").
 *
 * The contract this test file pins down:
 * - Bulk path NEVER creates rules (`createRule = false` for every delegated call).
 * - Already-matching rows are reported via the `noopCount` channel rather than
 *   re-persisted.
 * - `RuleActionUi.CONTEXTUAL` is rejected via the single dispatcher's IGNORED
 *   path (defense-in-depth — the UI must never send it).
 * - Empty input short-circuits to [BulkReclassifyResult.EMPTY].
 * - Mid-loop failures propagate; persisted rows up to the failure remain
 *   persisted (single-row policy is not transactional).
 *
 * Plan: `docs/plans/2026-04-26-priority-inbox-bulk-reclassify.md` Task 1.
 */
class BulkPassthroughReviewReclassifyDispatcherTest {

    private class FakeNotificationSink {
        val updated = mutableListOf<NotificationUiModel>()
        var failOnId: String? = null

        suspend fun update(notification: NotificationUiModel) {
            updated += notification
            if (notification.id == failOnId) {
                error("simulated persistence failure for ${notification.id}")
            }
        }
    }

    private class FakeRuleSink {
        val upserted = mutableListOf<RuleUiModel>()

        suspend fun upsert(rule: RuleUiModel) {
            upserted += rule
        }
    }

    private fun newDispatcher(
        notifications: FakeNotificationSink,
        rules: FakeRuleSink,
    ): BulkPassthroughReviewReclassifyDispatcher {
        val single = PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = notifications::update,
            upsertRule = rules::upsert,
        )
        return BulkPassthroughReviewReclassifyDispatcher(single)
    }

    @Test
    fun bulk_digest_persists_each_priority_row_without_creating_rules() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = newDispatcher(notifications, rules)

        val result = dispatcher.bulk(
            notifications = listOf(
                samplePriority("n1"),
                samplePriority("n2"),
                samplePriority("n3"),
            ),
            action = RuleActionUi.DIGEST,
        )

        assertEquals(BulkReclassifyResult(persistedCount = 3, noopCount = 0, ignoredCount = 0), result)
        assertEquals(listOf("n1", "n2", "n3"), notifications.updated.map { it.id })
        assertTrue(notifications.updated.all { it.status == NotificationStatusUi.DIGEST })
        assertTrue(
            "bulk path must not create rules",
            rules.upserted.isEmpty(),
        )
    }

    @Test
    fun bulk_skips_rows_already_in_target_status_via_noop_channel() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = newDispatcher(notifications, rules)

        val result = dispatcher.bulk(
            notifications = listOf(
                samplePriority("n1"),
                samplePriority("n2", status = NotificationStatusUi.DIGEST),
                samplePriority("n3"),
            ),
            action = RuleActionUi.DIGEST,
        )

        assertEquals(BulkReclassifyResult(persistedCount = 2, noopCount = 1, ignoredCount = 0), result)
        assertEquals(listOf("n1", "n3"), notifications.updated.map { it.id })
    }

    @Test
    fun bulk_with_empty_list_returns_EMPTY_and_persists_nothing() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = newDispatcher(notifications, rules)

        val result = dispatcher.bulk(notifications = emptyList(), action = RuleActionUi.DIGEST)

        assertEquals(BulkReclassifyResult.EMPTY, result)
        assertTrue(notifications.updated.isEmpty())
        assertTrue(rules.upserted.isEmpty())
    }

    @Test
    fun bulk_rejects_contextual_action_via_ignored_count() = runBlocking {
        val notifications = FakeNotificationSink()
        val rules = FakeRuleSink()
        val dispatcher = newDispatcher(notifications, rules)

        val result = dispatcher.bulk(
            notifications = listOf(samplePriority("n1")),
            action = RuleActionUi.CONTEXTUAL,
        )

        assertEquals(BulkReclassifyResult(persistedCount = 0, noopCount = 0, ignoredCount = 1), result)
        assertTrue(notifications.updated.isEmpty())
        assertTrue(rules.upserted.isEmpty())
    }

    @Test
    fun bulk_propagates_mid_loop_failure_and_keeps_already_persisted_rows() = runBlocking {
        val notifications = FakeNotificationSink().apply { failOnId = "n2" }
        val rules = FakeRuleSink()
        val dispatcher = newDispatcher(notifications, rules)

        val input = listOf(
            samplePriority("n1"),
            samplePriority("n2"),
            samplePriority("n3"),
        )

        try {
            dispatcher.bulk(notifications = input, action = RuleActionUi.DIGEST)
            fail("expected mid-loop failure to propagate")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message?.contains("n2") == true)
        }

        // n1 persisted before failure; n2 attempted (in updated list pre-throw); n3 never touched.
        assertEquals(listOf("n1", "n2"), notifications.updated.map { it.id })
    }

    private fun samplePriority(
        id: String,
        status: NotificationStatusUi = NotificationStatusUi.PRIORITY,
    ) = NotificationUiModel(
        id = id,
        appName = "카카오톡",
        packageName = "com.kakao.talk",
        sender = "엄마",
        title = "엄마",
        body = "알림 본문 $id",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = listOf("발신자 있음"),
        score = null,
        isBundled = false,
    )
}
