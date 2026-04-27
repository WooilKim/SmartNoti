package com.smartnoti.app.ui.screens.inbox

/**
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding
 * **F4** — "three different card visual languages on one screen". The
 * inbox-unified screen historically rendered four different card surface
 * specs in a single LazyColumn:
 *
 * 1. Tab row segmented control — `Surface(shape = RoundedCornerShape(12dp), border = 1dp BorderSubtle, color = surface)`.
 * 2. Hidden/ARCHIVED-PROCESSED summary card — `SmartSurfaceCard` (default
 *    `Card` shape `MaterialTheme.shapes.medium = 20dp`, 1dp `BorderSubtle`,
 *    `surface` fill, 16dp padding).
 * 3. Digest group card outer — `Card` (20dp default, 1dp `BorderSubtle`,
 *    `surfaceVariant.copy(alpha = 0.72f)` fill, 16dp padding).
 * 4. Per-row notification card — `Card` (20dp default, 1dp tinted border,
 *    status-tinted fill, 16dp padding).
 *
 * The eye had to recalibrate at every section because the corner radii alone
 * varied 12dp / 20dp / 20dp / 20dp, fills varied surface / surface-variant /
 * status-tinted, and there was no shared rhythm. `.claude/rules/ui-improvement.md`
 * is explicit: *"Use consistent corner radii, spacing, and card elevation /
 * shadow treatment"* and *"Favor reusable polished containers over one-off
 * styling"*.
 *
 * F4 consolidates the cards on inbox-unified to **two** canonical languages
 * sharing one rhythm (corner radius + border width + padding):
 *
 * - [Primary] — large container with the surface color (used for the Hidden
 *   summary card).
 * - [Subtle] — container with a slight surface-variant fill (used for
 *   `DigestGroupCard` outer and the Hidden archived/processed group cards
 *   that wrap it). Provides a soft visual distinction between summary
 *   information and grouped row containers without breaking the rhythm.
 *
 * The tab row segmented control keeps a deliberately smaller [TAB_ROW_CORNER_RADIUS_DP]
 * (12dp) — segmented controls have their own visual convention and rendering
 * them at the same 16dp as the section cards would read as another stacked
 * card rather than a control.
 *
 * Per-row notification cards inside `DigestGroupCard` previews are NOT in
 * scope of F4 — they retain status-tinted fills today and will be flattened
 * to dividers under finding F1 (separate PR). The contract here is only
 * about the *outer* container surfaces that frame the inbox sections.
 *
 * The contract is exposed as pure data with no Compose imports so a JVM
 * unit test ([InboxCardLanguageContractTest]) can pin radii / borders /
 * padding without spinning up a Compose runtime — same pattern as
 * [InboxHeaderChromeSpec] and [labelFor] for [InboxSortDropdown]. Any future
 * PR that introduces a new card on inbox-unified should call
 * [InboxCardLanguage.Primary.cornerRadiusDp] etc. instead of literal numbers
 * so the rhythm is enforced by construction.
 */
enum class InboxCardLanguage {
    /**
     * Large container with the screen surface as fill. Used for the Hidden
     * summary card (`보관 중` / `처리됨` 탭의 요약 카드).
     */
    Primary,

    /**
     * Group container with a slight surface-variant tint as fill. Used for
     * `DigestGroupCard` outer and the Hidden archived/processed group cards
     * that wrap it.
     */
    Subtle;

    /**
     * Canonical corner radius for the inbox-unified card surfaces. The same
     * value applies to both [Primary] and [Subtle] — the visual distinction
     * is in the fill, not in the corners. F4 picks 16dp per the meta-plan
     * recommendation; deliberately distinct from the 20dp `Card` default
     * (`MaterialTheme.shapes.medium`) to give the inbox a tighter rhythm.
     */
    val cornerRadiusDp: Int get() = CARD_CORNER_RADIUS_DP

    /**
     * Border width for the inbox-unified card surfaces. 1dp `BorderSubtle`
     * (`ui/theme/Color.kt`). Both languages share the same border treatment
     * — the visual distinction is in the fill.
     */
    val borderWidthDp: Int get() = CARD_BORDER_WIDTH_DP

    /**
     * Inner padding for the inbox-unified card surfaces. 16dp on every side.
     * Identical between [Primary] and [Subtle] so the spacing rhythm is
     * unbroken when the eye scans down the screen.
     */
    val paddingDp: Int get() = CARD_PADDING_DP

    companion object {
        /**
         * Canonical corner radius shared by [Primary] and [Subtle]. Exposed
         * as a constant for direct call sites (Compose composables) that
         * don't have a [InboxCardLanguage] enum value handy — for example,
         * the `DigestGroupCard` itself which is reused outside the inbox
         * (legacy `Routes.Digest` deep-link entry) but should still match
         * the inbox rhythm when hosted there.
         */
        const val CARD_CORNER_RADIUS_DP: Int = 16

        /** See [borderWidthDp]. */
        const val CARD_BORDER_WIDTH_DP: Int = 1

        /** See [paddingDp]. */
        const val CARD_PADDING_DP: Int = 16

        /**
         * Tab row segmented control corner radius. Intentionally distinct
         * from [CARD_CORNER_RADIUS_DP] because segmented controls have
         * their own visual convention (smaller, tighter pill-like shape)
         * and rendering the tab row at 16dp would read as another stacked
         * card rather than a control. The 12dp value matches the existing
         * `Hidden` standalone tab row so the two segmented controls stay
         * consistent with each other.
         */
        const val TAB_ROW_CORNER_RADIUS_DP: Int = 12
    }
}
