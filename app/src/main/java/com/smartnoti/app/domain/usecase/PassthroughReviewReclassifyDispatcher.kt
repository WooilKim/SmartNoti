package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Pure, Android-framework-free orchestrator for the Passthrough Review screen's
 * inline reclassify actions ("→ Digest", "→ 조용히", "→ 규칙 만들기").
 *
 * The Priority review screen calls [reclassify] with a notification + target
 * [RuleActionUi]; the dispatcher then delegates status/reason-tag computation
 * to the existing [NotificationFeedbackPolicy] (re-using the same path as the
 * Detail "이 알림 학습시키기" block) and persists the result through the
 * function-reference hooks the caller supplies.
 *
 * [buildRuleDraft] exposes the "would-be" rule so the UI can surface a
 * "→ 규칙 만들기" navigation target without mutating anything — the caller is
 * responsible for following that up with an editor flow or a later persist.
 *
 * Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase A Task 3.
 */
class PassthroughReviewReclassifyDispatcher(
    private val feedbackPolicy: NotificationFeedbackPolicy,
    private val updateNotification: suspend (NotificationUiModel) -> Unit,
    private val upsertRule: suspend (RuleUiModel) -> Unit,
) {
    /**
     * Apply [action] to [notification] via [NotificationFeedbackPolicy],
     * persist the updated notification, and — when [createRule] is true —
     * upsert a matching rule.
     *
     * Returns:
     * - [ReclassifyOutcome.UPDATED_WITH_RULE] — notification + rule persisted.
     * - [ReclassifyOutcome.UPDATED] — notification only (no rule requested).
     * - [ReclassifyOutcome.NOOP] — the action would not change status (e.g.
     *   asking a PRIORITY card to stay PRIORITY). Nothing is persisted.
     * - [ReclassifyOutcome.IGNORED] — [RuleActionUi.CONTEXTUAL] is rejected
     *   (the review UI should never offer it; defense-in-depth).
     */
    suspend fun reclassify(
        notification: NotificationUiModel,
        action: RuleActionUi,
        createRule: Boolean,
    ): ReclassifyOutcome {
        if (action == RuleActionUi.CONTEXTUAL) return ReclassifyOutcome.IGNORED

        val targetStatus = action.toTargetStatus()
            ?: return ReclassifyOutcome.IGNORED
        if (notification.status == targetStatus) return ReclassifyOutcome.NOOP

        val updated = feedbackPolicy.applyAction(notification = notification, action = action)
        updateNotification(updated)

        return if (createRule) {
            upsertRule(feedbackPolicy.toRule(notification = notification, action = action))
            ReclassifyOutcome.UPDATED_WITH_RULE
        } else {
            ReclassifyOutcome.UPDATED
        }
    }

    /**
     * Produce the rule that would be created for [notification] + [action]
     * without persisting anything. Useful for "규칙 만들기" navigation where a
     * downstream editor takes ownership of the upsert.
     */
    fun buildRuleDraft(
        notification: NotificationUiModel,
        action: RuleActionUi,
    ): RuleUiModel = feedbackPolicy.toRule(notification = notification, action = action)

    private fun RuleActionUi.toTargetStatus(): NotificationStatusUi? = when (this) {
        RuleActionUi.ALWAYS_PRIORITY -> NotificationStatusUi.PRIORITY
        RuleActionUi.DIGEST -> NotificationStatusUi.DIGEST
        RuleActionUi.SILENT -> NotificationStatusUi.SILENT
        RuleActionUi.CONTEXTUAL -> null
    }
}

/**
 * Result of [PassthroughReviewReclassifyDispatcher.reclassify].
 *
 * `persisted` is a convenience for the UI so it can suppress "변경됨" toasts
 * when the action was a no-op.
 */
enum class ReclassifyOutcome(val persisted: Boolean) {
    UPDATED_WITH_RULE(persisted = true),
    UPDATED(persisted = true),
    NOOP(persisted = false),
    IGNORED(persisted = false),
}
