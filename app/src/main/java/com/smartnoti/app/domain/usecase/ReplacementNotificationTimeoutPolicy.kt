package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.NotificationDecision

/**
 * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 1.
 *
 * Pure helper that decides whether a SmartNoti-posted replacement / summary
 * notification should carry an auto-dismiss timeout, and if so for how many
 * milliseconds. The notifier wires the result into
 * `NotificationCompat.Builder.setTimeoutAfter(Long)` so Android's
 * NotificationManager cancels the tray entry after the timeout — the DB row
 * lifecycle is unaffected (timeout is "tray noise reduction" only).
 *
 * Returns `null` (no timeout — caller skips `setTimeoutAfter`) when:
 *  - the user's master toggle `replacementAutoDismissEnabled` is OFF,
 *  - the decision is `PRIORITY` (defense-in-depth — caller already early-returns
 *    on PRIORITY because SmartNoti never posts a replacement for original
 *    PRIORITY alerts),
 *  - the decision is `IGNORE` (same — IGNORE never posts a replacement),
 *  - the configured `replacementAutoDismissMinutes` is `<= 0` (defensive
 *    against any future settings UI that allows free-form input — the policy
 *    itself never trusts a non-positive value).
 *
 * Otherwise returns `minutes * 60_000L` so the same value applies uniformly
 * to DIGEST replacements, SILENT group summaries, and SILENT group children.
 */
class ReplacementNotificationTimeoutPolicy {
    fun timeoutMillisFor(
        settings: SmartNotiSettings,
        decision: NotificationDecision,
    ): Long? {
        if (!settings.replacementAutoDismissEnabled) return null
        if (decision == NotificationDecision.PRIORITY) return null
        if (decision == NotificationDecision.IGNORE) return null
        val minutes = settings.replacementAutoDismissMinutes
        if (minutes <= 0) return null
        return minutes.toLong() * 60_000L
    }
}
