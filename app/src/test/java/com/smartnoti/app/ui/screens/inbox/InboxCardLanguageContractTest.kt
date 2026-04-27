package com.smartnoti.app.ui.screens.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding
 * **F4** — "three different card visual languages on one screen".
 *
 * Pins the inbox-unified card surface contract so a future PR cannot quietly
 * re-introduce a third corner-radius / border-width / padding combination
 * inside the same screen. Pure-helper test pattern mirrors
 * [InboxHeaderChromeContractTest], [InboxSortDropdownLabelTest], and
 * [DigestGroupHeaderBadgeStyleTest].
 *
 * Contract (F4):
 * - [InboxCardLanguage] exposes exactly **two** languages: [InboxCardLanguage.Primary]
 *   and [InboxCardLanguage.Subtle]. The visual distinction is in the fill;
 *   radius / border / padding are identical so the spacing rhythm is unbroken.
 * - Both languages share `cornerRadiusDp = 16`, `borderWidthDp = 1`,
 *   `paddingDp = 16` — the meta-plan's canonical inbox card spec.
 * - Tab row segmented control keeps a smaller `TAB_ROW_CORNER_RADIUS_DP = 12`
 *   so it does not read as another stacked card.
 *
 * Any future PR that introduces a third card language (e.g. picks a different
 * radius for the summary card vs the group card) will trip this test before
 * it can ship.
 */
class InboxCardLanguageContractTest {

    @Test
    fun there_are_exactly_two_card_languages() {
        // F4: consolidating four ad-hoc surface specs to two named languages
        // is the whole point. A third entry would mean a future PR re-opened
        // the rhythm-breaking we just closed.
        assertEquals(
            "F4: inbox-unified must expose exactly Primary + Subtle",
            2,
            InboxCardLanguage.values().size,
        )
    }

    @Test
    fun primary_and_subtle_share_the_same_corner_radius() {
        assertEquals(
            "F4: rhythm requires identical corner radii across languages",
            InboxCardLanguage.Primary.cornerRadiusDp,
            InboxCardLanguage.Subtle.cornerRadiusDp,
        )
    }

    @Test
    fun primary_and_subtle_share_the_same_border_width() {
        assertEquals(
            "F4: rhythm requires identical border widths across languages",
            InboxCardLanguage.Primary.borderWidthDp,
            InboxCardLanguage.Subtle.borderWidthDp,
        )
    }

    @Test
    fun primary_and_subtle_share_the_same_padding() {
        assertEquals(
            "F4: rhythm requires identical inner padding across languages",
            InboxCardLanguage.Primary.paddingDp,
            InboxCardLanguage.Subtle.paddingDp,
        )
    }

    @Test
    fun canonical_card_radius_is_pinned_to_meta_plan_value() {
        // Meta-plan F4 fix: "corner radius 16dp". A future PR that changes
        // this value should be a deliberate cross-screen design decision —
        // touching the constant trips this test and forces a rationale.
        assertEquals(16, InboxCardLanguage.CARD_CORNER_RADIUS_DP)
        assertEquals(16, InboxCardLanguage.Primary.cornerRadiusDp)
        assertEquals(16, InboxCardLanguage.Subtle.cornerRadiusDp)
    }

    @Test
    fun canonical_card_border_is_pinned_to_one_dp() {
        // 1dp BorderSubtle is the existing inbox border treatment shared by
        // SmartSurfaceCard, DigestGroupCard, and InboxTabRow. F4 codifies
        // it; flipping to 0dp or 2dp would break the calm/quiet aesthetic.
        assertEquals(1, InboxCardLanguage.CARD_BORDER_WIDTH_DP)
        assertEquals(1, InboxCardLanguage.Primary.borderWidthDp)
        assertEquals(1, InboxCardLanguage.Subtle.borderWidthDp)
    }

    @Test
    fun canonical_card_padding_is_pinned_to_sixteen_dp() {
        // 16dp inner padding matches SmartSurfaceCard's default and the
        // existing DigestGroupCard padding — F4 keeps it canonical so a
        // future "let's try 12dp here" tweak trips this test.
        assertEquals(16, InboxCardLanguage.CARD_PADDING_DP)
        assertEquals(16, InboxCardLanguage.Primary.paddingDp)
        assertEquals(16, InboxCardLanguage.Subtle.paddingDp)
    }

    @Test
    fun tab_row_corner_radius_stays_smaller_than_card_radius() {
        // Tab row segmented control is intentionally distinct from the card
        // surfaces — same family, different role. If they ever match the
        // tab row reads as another stacked card.
        assertTrue(
            "F4: tab row radius must stay smaller than card radius",
            InboxCardLanguage.TAB_ROW_CORNER_RADIUS_DP < InboxCardLanguage.CARD_CORNER_RADIUS_DP,
        )
        assertEquals(12, InboxCardLanguage.TAB_ROW_CORNER_RADIUS_DP)
    }

    @Test
    fun primary_and_subtle_are_distinct_languages() {
        // Belt-and-suspenders: even though they share the rhythm tokens, the
        // enum values themselves must be distinct so call sites can request
        // a fill treatment without collapsing to a single shared instance.
        assertNotEquals(InboxCardLanguage.Primary, InboxCardLanguage.Subtle)
    }
}
