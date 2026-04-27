package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.SilentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 1 of `docs/plans/2026-04-20-silent-archive-drift-fix.md`.
 *
 * Pins the contract that the listener capture path selects
 * `silentMode = SilentMode.ARCHIVED` for fresh SILENT classifications, and
 * leaves persistent / protected branches on the legacy `null` (cancel) behavior.
 *
 * These tests are expected to fail against the Task 1 stub of
 * [SilentCaptureRoutingSelector] and pass once Task 2 threads the ARCHIVED mode
 * through the capture path.
 */
class SilentArchivedCapturePathTest {

    @Test
    fun fresh_silent_capture_selects_archived_mode() {
        val mode = SilentCaptureRoutingSelector.silentModeFor(
            decision = NotificationDecision.SILENT,
            isPersistent = false,
            shouldBypassPersistentHiding = false,
            isProtectedSourceNotification = false,
        )

        assertEquals(SilentMode.ARCHIVED, mode)
    }

    @Test
    fun fresh_silent_capture_routes_cancel_source_and_post_replacement() {
        // Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
        // Task 3 (Option C invariant): the selector still picks ARCHIVED so
        // the persisted row lands in the Hidden inbox "보관 중" tab, but
        // SourceNotificationRoutingPolicy no longer gates the source-tray
        // decision on SilentMode. The source is cancelled and the
        // SmartNoti replacement (silent_group child + Hidden inbox row) is
        // posted — Issue #511 fix.
        val mode = SilentCaptureRoutingSelector.silentModeFor(
            decision = NotificationDecision.SILENT,
            isPersistent = false,
            shouldBypassPersistentHiding = false,
            isProtectedSourceNotification = false,
        )

        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
            silentMode = mode,
        )

        assertTrue(routing.cancelSourceNotification)
        assertTrue(routing.notifyReplacementNotification)
        // Selector mode is still ARCHIVED — that drives DB persistence, not
        // tray routing.
        assertEquals(SilentMode.ARCHIVED, mode)
    }

    @Test
    fun protected_silent_capture_stays_on_legacy_null_mode() {
        // Protected notifications short-circuit routing upstream, so this value is
        // never consumed — but we pin null so future refactors that collapse the
        // short-circuit do not accidentally cancel media/call/foreground-service
        // source notifications.
        val mode = SilentCaptureRoutingSelector.silentModeFor(
            decision = NotificationDecision.SILENT,
            isPersistent = false,
            shouldBypassPersistentHiding = false,
            isProtectedSourceNotification = true,
        )

        assertNull(mode)
    }

    @Test
    fun persistent_silent_capture_stays_on_legacy_null_mode() {
        // Persistent notifications are hidden via the dedicated
        // `hidePersistentSourceNotification` path; they must not be pulled into the
        // ARCHIVED split.
        val mode = SilentCaptureRoutingSelector.silentModeFor(
            decision = NotificationDecision.SILENT,
            isPersistent = true,
            shouldBypassPersistentHiding = false,
            isProtectedSourceNotification = false,
        )

        assertNull(mode)
    }

    @Test
    fun persistent_silent_capture_with_bypass_is_treated_as_fresh_capture() {
        // `shouldBypassPersistentHiding = true` means the listener already decided
        // to treat this persistent notification as a normal capture (for example, a
        // critical persistent alert the user opted to keep hidden). That branch
        // should participate in ARCHIVED just like any other fresh SILENT.
        val mode = SilentCaptureRoutingSelector.silentModeFor(
            decision = NotificationDecision.SILENT,
            isPersistent = true,
            shouldBypassPersistentHiding = true,
            isProtectedSourceNotification = false,
        )

        assertEquals(SilentMode.ARCHIVED, mode)
    }

    @Test
    fun non_silent_decisions_never_select_a_silent_mode() {
        listOf(NotificationDecision.PRIORITY, NotificationDecision.DIGEST).forEach { decision ->
            val mode = SilentCaptureRoutingSelector.silentModeFor(
                decision = decision,
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )

            assertNull("decision=$decision should not select a SilentMode", mode)
        }
    }

    @Test
    fun null_silent_mode_still_cancels_and_now_also_posts_replacement() {
        // Plan `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
        // Task 3: SILENT with no mode hint also follows the unified Option C
        // invariant — cancel + replace. The previous "legacy null cancels
        // without replacement" shape is gone; SmartNoti always posts the
        // replacement when it owns SILENT routing.
        val routing = SourceNotificationRoutingPolicy.route(
            decision = NotificationDecision.SILENT,
            hidePersistentSourceNotification = false,
            suppressSourceNotification = false,
            silentMode = null,
        )

        assertTrue(routing.cancelSourceNotification)
        assertTrue(routing.notifyReplacementNotification)
    }
}
