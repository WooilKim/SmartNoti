package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.SilentMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceNotificationRoutingPolicyTest {

    @Test
    fun persistent_hidden_priority_keeps_source_notification_and_skips_replacement() {
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.PRIORITY,
            hidePersistentSourceNotification = true,
            suppressSourceNotification = false,
        )

        assertFalse(routing.cancelSourceNotification)
        assertFalse(routing.notifyReplacementNotification)
    }

    @Test
    fun digest_suppression_cancels_source_and_shows_replacement() {
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.DIGEST,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = true,
        )

        assertTrue(routing.cancelSourceNotification)
        assertTrue(routing.notifyReplacementNotification)
    }

    /**
     * Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
     * Task 3 (Option C invariant): DIGEST without the user's opt-in flags
     * still cancels and replaces — the suppress flag and per-app list have
     * been demoted to "next-time membership" state. They no longer gate
     * the post-then-cancel decision.
     */
    @Test
    fun digest_without_hide_or_suppress_still_cancels_and_replaces() {
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.DIGEST,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
        )

        assertTrue(routing.cancelSourceNotification)
        assertTrue(routing.notifyReplacementNotification)
    }

    /**
     * Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
     * Task 3 (Option C invariant): SILENT with no SilentMode hint posts the
     * replacement (silent_group tray entry + Hidden inbox row) AND cancels
     * the source. The previous SilentMode-gated `keepSourceInTray` branch
     * is gone.
     */
    @Test
    fun silent_without_mode_cancels_source_and_posts_replacement() {
        val suppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = true,
        )
        assertTrue(suppressed.cancelSourceNotification)
        assertTrue(suppressed.notifyReplacementNotification)

        val unsuppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
        )
        assertTrue(unsuppressed.cancelSourceNotification)
        assertTrue(unsuppressed.notifyReplacementNotification)
    }

    /**
     * Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
     * Task 3: SilentMode no longer gates the source-tray decision. ARCHIVED
     * still routes the persisted row into the Hidden inbox 보관 중 tab
     * downstream, but the source tray entry is cancelled regardless of
     * mode (Option C single invariant). PROCESSED behaves identically.
     */
    @Test
    fun silent_mode_does_not_gate_routing() {
        val archivedSuppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = true,
            silentMode = SilentMode.ARCHIVED,
        )
        assertTrue(archivedSuppressed.cancelSourceNotification)
        assertTrue(archivedSuppressed.notifyReplacementNotification)

        val archivedUnsuppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
            silentMode = SilentMode.ARCHIVED,
        )
        assertTrue(archivedUnsuppressed.cancelSourceNotification)
        assertTrue(archivedUnsuppressed.notifyReplacementNotification)

        val processedSuppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = true,
            silentMode = SilentMode.PROCESSED,
        )
        assertTrue(processedSuppressed.cancelSourceNotification)
        assertTrue(processedSuppressed.notifyReplacementNotification)

        val processedUnsuppressed = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
            silentMode = SilentMode.PROCESSED,
        )
        assertTrue(processedUnsuppressed.cancelSourceNotification)
        assertTrue(processedUnsuppressed.notifyReplacementNotification)
    }
}
