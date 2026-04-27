package com.smartnoti.app.ui.components

import com.smartnoti.app.domain.model.NotificationStatusUi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic counterpart to plan
 * `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding **F3**
 * (orange accent restraint on inbox-unified).
 *
 * Contract (F3): when a [NotificationCard] is rendered **inside a
 * DigestGroupCard preview row** the per-row orange `Digest` [StatusBadge] is
 * suppressed — the sub-tab name + the parent group card already declare the
 * status so the chip is redundant noise. Standalone `NotificationCard` call
 * sites (Home recent feed, IgnoredArchive, Detail-related list, PriorityScreen)
 * keep the chip on so cross-status feeds stay scannable.
 *
 * The pure helper [notificationCardStatusBadgeVisible] is the single decision
 * point for the visibility, so tests can pin the contract without spinning up
 * Compose. Mirrors the codebase pattern used by `digestGroupCardPreviewState`.
 */
class NotificationCardStatusBadgeVisibilityTest {

    @Test
    fun status_badge_visible_by_default() {
        // Standalone call sites (Home recent feed, IgnoredArchive, Detail list,
        // PriorityScreen) all rely on the default to keep the chip visible.
        for (status in NotificationStatusUi.values()) {
            assertTrue(
                "Default visibility must be on for status=$status",
                notificationCardStatusBadgeVisible(
                    status = status,
                    showStatusBadge = true,
                ),
            )
        }
    }

    @Test
    fun status_badge_suppressed_when_caller_opts_out() {
        // DigestGroupCard previews + (future) HiddenNotificationsScreen group
        // previews call NotificationCard with showStatusBadge=false because the
        // parent context (sub-tab + group header) already declares status.
        for (status in NotificationStatusUi.values()) {
            assertFalse(
                "Caller opt-out must suppress chip for status=$status",
                notificationCardStatusBadgeVisible(
                    status = status,
                    showStatusBadge = false,
                ),
            )
        }
    }
}
