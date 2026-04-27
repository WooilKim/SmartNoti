package com.smartnoti.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic counterpart to plan
 * `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding **F3**
 * (orange accent restraint on inbox-unified). The Composable [DigestGroupCard]
 * itself is exercised manually via the verification recipe; this test pins the
 * style contract for the per-group `${count}건` count badge so a future PR
 * cannot quietly re-introduce the bright orange filled treatment that competes
 * with the sub-tab indicator and per-row status chips on the same screen.
 *
 * Contract (F3):
 * - The count badge is **outlined-neutral**, not filled-orange. Background
 *   token is the surface itself (no fill); the border uses `BorderSubtle`
 *   tint and the text uses `onSurfaceVariant`. This frees the orange
 *   accent for **status/selection** signals only — the selected sub-tab
 *   indicator and (when shown) the per-row [StatusBadge] keep accent ownership
 *   per `.claude/rules/ui-improvement.md` ("Use accent color sparingly for
 *   selection, status, and primary actions").
 */
class DigestGroupHeaderBadgeStyleTest {

    @Test
    fun count_badge_is_outlined_not_filled() {
        val style = digestGroupHeaderBadgeStyle()

        assertFalse(
            "F3: count badge must not use the orange filled treatment",
            style.isFilledAccent,
        )
        assertTrue(
            "F3: count badge uses an outline border instead of fill",
            style.useOutlineBorder,
        )
    }

    @Test
    fun count_label_template_is_unchanged() {
        // The visual demotion must not change copy — reviewers / journey doc
        // still describe the "${count}건" badge.
        val style = digestGroupHeaderBadgeStyle()

        assertEquals("4건", style.formatCountLabel(4))
        assertEquals("0건", style.formatCountLabel(0))
        assertEquals("12건", style.formatCountLabel(12))
    }
}
