package com.smartnoti.app.notification

/**
 * Pure, Android-framework-free orchestrator for Detail's "처리 완료로 표시" flow.
 *
 * The Detail screen calls [run] with a `notificationId`; the chain then:
 * 1. Runs the repository DB flip (`markSilentProcessed`). On false (legacy
 *    PROCESSED rows, missing rows, non-SILENT rows) the chain short-circuits
 *    to [ChainOutcome.DB_NOOP] and touches nothing else.
 * 2. Looks up the persisted `sourceEntryKey`. If the row was saved before the
 *    column existed (or no tray entry was captured), returns
 *    [ChainOutcome.FLIPPED_WITHOUT_KEY] — DB transition stands, tray stays.
 * 3. Asks the listener service's companion helper to cancel the tray entry.
 *    If the listener is not currently connected the chain reports
 *    [ChainOutcome.LISTENER_DISCONNECTED]; otherwise
 *    [ChainOutcome.CANCELLED].
 *
 * Keeping this class off Android types lets the behaviour be tested with pure
 * JVM unit tests. The Detail composable supplies repository methods and the
 * service companion helper as function references.
 *
 * Related plan: `docs/plans/2026-04-20-silent-archive-drift-fix.md` Task 3.
 */
class MarkSilentProcessedTrayCancelChain(
    private val markSilentProcessed: suspend (String) -> Boolean,
    private val sourceEntryKeyForId: suspend (String) -> String?,
    private val cancelSourceEntryIfConnected: (String) -> Boolean,
) {
    suspend fun run(notificationId: String): ChainOutcome {
        val flipped = markSilentProcessed(notificationId)
        if (!flipped) return ChainOutcome.DB_NOOP

        val key = sourceEntryKeyForId(notificationId)
            ?: return ChainOutcome.FLIPPED_WITHOUT_KEY

        return if (cancelSourceEntryIfConnected(key)) {
            ChainOutcome.CANCELLED
        } else {
            ChainOutcome.LISTENER_DISCONNECTED
        }
    }
}

/**
 * Result of running [MarkSilentProcessedTrayCancelChain].
 *
 * [flipped] is a convenience for the UI so it can decide to show a "이미 처리됨"
 * style toast only when the DB was actually a no-op.
 */
enum class ChainOutcome(val flipped: Boolean) {
    DB_NOOP(flipped = false),
    FLIPPED_WITHOUT_KEY(flipped = true),
    CANCELLED(flipped = true),
    LISTENER_DISCONNECTED(flipped = true),
}
