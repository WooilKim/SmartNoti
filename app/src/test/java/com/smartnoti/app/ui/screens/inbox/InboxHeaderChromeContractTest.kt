package com.smartnoti.app.ui.screens.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` F2 —
 * "header chrome eats 25% of first screen".
 *
 * Pins the inbox-unified header chrome composition so a future PR cannot
 * silently re-introduce the explanation tax (subtitle line) or split the sort
 * dropdown into its own row below the title (each broke F2's first-impression
 * payoff). The journey `inbox-unified.md` Observable steps cite this contract
 * verbatim.
 *
 * Project convention (mirroring `InboxSortDropdownLabelTest`): the contract
 * is exposed as a pure helper [InboxHeaderChromeSpec] outside the Composable
 * so a JVM unit test can exercise it without spinning up Compose. Adding a
 * new chrome slot is a deliberate, type-checked decision here.
 */
class InboxHeaderChromeContractTest {

    @Test
    fun eyebrow_is_pinned_to_정리함() {
        assertEquals("정리함", InboxHeaderChromeSpec.eyebrow)
    }

    @Test
    fun title_is_pinned_to_알림_정리함() {
        assertEquals("알림 정리함", InboxHeaderChromeSpec.title)
    }

    @Test
    fun subtitle_is_dropped_on_inbox_screen() {
        // F2 fix: the subtitle was a doc string repeating the eyebrow + title
        // and pushed first content below the fold. Returning users do not need
        // the explanation re-rendered every visit. Eyebrow + title alone
        // already establishes context.
        assertNull(InboxHeaderChromeSpec.subtitle)
    }

    @Test
    fun sort_dropdown_renders_inline_with_title_not_in_its_own_row() {
        // F2 fix: sort row used to occupy a dedicated 48dp row directly below
        // the header (right-aligned). Promoting it to the title row's trailing
        // slot reclaims that row for content. If a future PR demotes it back
        // to its own row this assertion flips.
        assertTrue(
            "sort dropdown must render inside the title row",
            InboxHeaderChromeSpec.sortDropdownIsInlineWithTitle,
        )
        assertFalse(
            "sort dropdown must NOT occupy a dedicated row below the header",
            InboxHeaderChromeSpec.sortDropdownHasDedicatedRow,
        )
    }

    @Test
    fun sort_dropdown_inline_and_dedicated_row_are_mutually_exclusive() {
        // Defense-in-depth — the two booleans describe one decision; flipping
        // both true would mean the dropdown renders twice. A single source of
        // truth would be safer but the boolean pair makes the journey-doc
        // contract scannable per-claim.
        assertFalse(
            "inline + dedicated-row are mutually exclusive",
            InboxHeaderChromeSpec.sortDropdownIsInlineWithTitle &&
                InboxHeaderChromeSpec.sortDropdownHasDedicatedRow,
        )
    }
}
