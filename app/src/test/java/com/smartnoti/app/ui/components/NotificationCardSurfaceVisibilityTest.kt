package com.smartnoti.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic counterpart to plan
 * `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding **F1**
 * (preview rows no longer render an outlined surface inside a parent group
 * card — collapses the "cards inside cards" hierarchy that reads as cluttered).
 *
 * Contract (F1): when a [NotificationCard] is rendered **inside a
 * DigestGroupCard preview row** (`embeddedInCard = true`) the outlined card
 * surface — the rounded outer container with its own border + filled
 * background — is suppressed. The parent group card BE the only card
 * boundary; preview rows are flat divider-separated list items. Standalone
 * call sites (Home recent feed, IgnoredArchive, Detail-related list,
 * PriorityScreen) keep their own surface so cross-status feeds stay scannable.
 *
 * The pure helper [notificationCardSurfaceVisible] is the single decision
 * point for the surface visibility, so tests can pin the contract without
 * spinning up Compose. Mirrors the codebase pattern used by
 * [notificationCardStatusBadgeVisible] and [digestGroupCardPreviewState].
 */
class NotificationCardSurfaceVisibilityTest {

    @Test
    fun surface_visible_by_default() {
        // Standalone call sites (Home recent feed, IgnoredArchive, Detail list,
        // PriorityScreen) all rely on the default to keep the outlined
        // surface so the row reads as an independent tappable card.
        assertTrue(
            "Default must keep the outlined surface rendered",
            notificationCardSurfaceVisible(embeddedInCard = false),
        )
    }

    @Test
    fun surface_suppressed_when_embedded_in_card() {
        // DigestGroupCard preview rows (and any future nested-context
        // caller) call NotificationCard with embeddedInCard=true so the
        // parent group card BE the only card boundary — flat divider-rows
        // inside, no nested rectangles.
        assertFalse(
            "Embedded preview rows must not render their own outlined surface",
            notificationCardSurfaceVisible(embeddedInCard = true),
        )
    }
}
