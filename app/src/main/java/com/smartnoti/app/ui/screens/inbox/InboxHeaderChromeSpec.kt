package com.smartnoti.app.ui.screens.inbox

/**
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` F2 —
 * "header chrome eats 25% of first screen".
 *
 * Single source of truth for the inbox-unified header chrome composition.
 * The fields are intentionally pure data (no Compose imports) so a JVM unit
 * test (`InboxHeaderChromeContractTest`) can pin the contract without a
 * Compose runtime — same pattern as [labelFor] for [InboxSortDropdown].
 *
 * The journey doc `docs/journeys/inbox-unified.md` Observable steps cite
 * these strings verbatim; renaming any field is a deliberate, type-checked
 * decision that must accompany a journey edit and a contract-test update.
 *
 * Decisions encoded here:
 * - `subtitle = null` — the legacy 2-line subtitle ("Digest 묶음과 숨긴 알림을
 *   한 화면에서 훑어볼 수 있어요.") was a doc string repeating the eyebrow +
 *   title. F2 dropped it on Inbox specifically (other screens still pass a
 *   subtitle through `ScreenHeader`).
 * - `sortDropdownIsInlineWithTitle = true` — the [InboxSortDropdown] now
 *   renders as the title row's trailing slot (right-aligned), so the
 *   dedicated 48dp `Row { ... }` below the header collapsed.
 * - `sortDropdownHasDedicatedRow = false` — kept as a separate boolean so
 *   the contract test can guard against a regression PR that re-adds the
 *   dedicated row without removing the inline placement (which would render
 *   the dropdown twice).
 */
object InboxHeaderChromeSpec {

    /** Eyebrow line above the title. Pinned in the journey Observable steps. */
    const val eyebrow: String = "정리함"

    /** Header title. Pinned in the journey Observable steps. */
    const val title: String = "알림 정리함"

    /**
     * Optional subtitle line below the title. F2 dropped it on Inbox — see
     * the file-level KDoc for the rationale. Future PRs that want to bring
     * back a contextual hint should prefer a transient empty-state snackbar
     * or a one-time onboarding banner over re-adding a permanent row.
     */
    val subtitle: String? = null

    /** True when the sort dropdown renders inside the title row (F2 fix). */
    const val sortDropdownIsInlineWithTitle: Boolean = true

    /** True if the sort dropdown still occupies a dedicated row (regression). */
    const val sortDropdownHasDedicatedRow: Boolean = false
}
