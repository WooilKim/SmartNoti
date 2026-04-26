package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi

/**
 * Bulk wrapper around [PassthroughReviewReclassifyDispatcher] for the
 * PriorityScreen multi-select ActionBar ("→ Digest" / "→ 조용히").
 *
 * Composition over duplication: every selected row is delegated to the existing
 * single-row dispatcher with `createRule = false`, so NOOP/IGNORED/UPDATED
 * accounting stays in one place. The bulk path **never** creates rules — bulk
 * rule creation is out of scope for this plan because it only makes sense when
 * every selected row shares a sender/app, which the UI does not currently
 * enforce.
 *
 * Throws are propagated, not swallowed: matching the single-row policy of
 * "no transaction across persistence calls". Already-persisted rows up to the
 * failure remain persisted; the caller decides how to surface the error
 * (current UI: snackbar via standard scope.launch error handling).
 *
 * Plan: `docs/plans/2026-04-26-priority-inbox-bulk-reclassify.md` Task 2.
 */
class BulkPassthroughReviewReclassifyDispatcher(
    private val single: PassthroughReviewReclassifyDispatcher,
) {
    suspend fun bulk(
        notifications: List<NotificationUiModel>,
        action: RuleActionUi,
    ): BulkReclassifyResult {
        if (notifications.isEmpty()) return BulkReclassifyResult.EMPTY

        var persisted = 0
        var noop = 0
        var ignored = 0
        for (notification in notifications) {
            val outcome = single.reclassify(
                notification = notification,
                action = action,
                createRule = false,
            )
            when (outcome) {
                ReclassifyOutcome.UPDATED,
                ReclassifyOutcome.UPDATED_WITH_RULE -> persisted++
                ReclassifyOutcome.NOOP -> noop++
                ReclassifyOutcome.IGNORED -> ignored++
            }
        }
        return BulkReclassifyResult(
            persistedCount = persisted,
            noopCount = noop,
            ignoredCount = ignored,
        )
    }
}

/**
 * Result of [BulkPassthroughReviewReclassifyDispatcher.bulk]. The UI uses
 * [persistedCount] for the snackbar message ("알림 N건을 …로 옮겼어요") and
 * suppresses the snackbar entirely when it is zero (every row was a NOOP — the
 * selection is preserved so the user can retry).
 */
data class BulkReclassifyResult(
    val persistedCount: Int,
    val noopCount: Int,
    val ignoredCount: Int,
) {
    companion object {
        val EMPTY = BulkReclassifyResult(persistedCount = 0, noopCount = 0, ignoredCount = 0)
    }
}
