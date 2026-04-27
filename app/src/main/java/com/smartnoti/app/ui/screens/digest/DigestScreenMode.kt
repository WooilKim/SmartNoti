package com.smartnoti.app.ui.screens.digest

/**
 * Host mode for [DigestScreen]. Plan
 * `docs/plans/2026-04-27-inbox-unified-double-header-collapse.md` Task 1.
 *
 * The same composable serves two entry points with different chrome:
 *
 * - [Standalone] — legacy `Routes.Digest` deep link (replacement notification
 *   contentIntent, onboarding flows). Renders the in-screen `ScreenHeader`
 *   + the `현재 N개의 묶음이 준비되어 있어요` summary card so the user gets
 *   page context when they arrive without the Inbox shell around them.
 * - [Embedded] — invoked from `InboxScreen`'s outer Digest sub-tab. The outer
 *   screen already shows a `ScreenHeader` + sort dropdown + tab row that
 *   together provide context; this mode suppresses the screen's own header
 *   and summary card so the user does not see the same chrome twice.
 *
 * Mirrors `HiddenScreenMode.Standalone | Embedded` so future mode-aware
 * params (e.g. an embed-only sort hint) can be added in the same shape.
 */
sealed class DigestScreenMode {
    data object Standalone : DigestScreenMode()
    data object Embedded : DigestScreenMode()

    /**
     * Boolean view contract — `true` if this mode wants the in-screen
     * `ScreenHeader` (and the matching summary `SmartSurfaceCard`) rendered.
     * Pinned by `DigestScreenModeContractTest`.
     */
    fun shouldRenderHeader(): Boolean = when (this) {
        is Standalone -> true
        is Embedded -> false
    }
}
