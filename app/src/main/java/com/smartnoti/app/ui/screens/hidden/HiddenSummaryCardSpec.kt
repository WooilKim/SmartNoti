package com.smartnoti.app.ui.screens.hidden

import com.smartnoti.app.domain.model.SilentMode

/**
 * Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md` finding
 * **F5** — "summary card on 보관 중 / 처리됨 says the same thing twice".
 *
 * Single source of truth for whether [HiddenNotificationsScreen] renders the
 * full SmartSurfaceCard summary block (count restatement + helper text +
 * `전체 숨긴 알림 모두 지우기` outlined button) **above the group cards**.
 *
 * The audit (2026-04-28, emulator-5554) found that under
 * [HiddenScreenMode.Embedded] (the inbox-unified `보관 중` / `처리됨` sub-tabs),
 * the count `${visibleCount}건` was being repeated **three** times in the top
 * half of the screen:
 *
 * 1. The outer [InboxTabRow] segment (`보관 중 · 11건`).
 * 2. The summary card here (`1개 앱에서 11건을 보관 중이에요.`).
 * 3. The first group card row (`Shell 숨긴 알림 11건` + `11건` chip).
 *
 * Per `.claude/rules/ui-improvement.md` ("Improve typography hierarchy before
 * adding more color" + "Replace default-looking cards with more intentional
 * container styling"), the summary card was burning ~280px of premium
 * above-the-fold real estate to restate what the tab row already said.
 *
 * The fix collapses the embedded-mode summary into a compact caption + a
 * trailing text-button so the bulk-clear action is preserved without the
 * count repetition or the heavyweight surface. Standalone deep-link entries
 * (`Routes.Hidden`, tray group-summary contentIntent) keep the full summary
 * card because they arrive without the outer tab-row context that already
 * declares the count — collapsing there would lose the only count signal.
 *
 * The fields are intentionally pure data (no Compose imports) so a JVM unit
 * test (`HiddenSummaryCardSpecTest`) can pin the contract without a Compose
 * runtime — same pattern as [InboxHeaderChromeSpec] and
 * [com.smartnoti.app.ui.components.digestGroupHeaderBadgeStyle].
 */
object HiddenSummaryCardSpec {

    /**
     * True when the full SmartSurfaceCard summary (count restatement + helper
     * text + `전체 숨긴 알림 모두 지우기` outlined button) renders above the
     * group cards for the supplied host [mode].
     *
     * - [HiddenScreenMode.Standalone]: `true` — deep-link entries lack the
     *   outer tab-row context; the summary card is the only count signal.
     * - [HiddenScreenMode.Embedded]: `false` — outer [InboxTabRow] already
     *   declares the count, restating it is noise. F5 collapses this slot
     *   to [embeddedCaptionFor] + a single text-button.
     */
    fun fullSummaryCardVisible(mode: HiddenScreenMode): Boolean = when (mode) {
        is HiddenScreenMode.Standalone -> true
        is HiddenScreenMode.Embedded -> false
    }

    /**
     * Compact helper caption for the embedded mode (rendered below the outer
     * tab row, above the first group card). Holds the *contextual hint* part
     * of the legacy summary card without the count restatement. Returns
     * `null` for [HiddenScreenMode.Standalone] (the full card already shows
     * the same hint as its second line).
     */
    fun embeddedCaptionFor(
        mode: HiddenScreenMode,
        silentMode: SilentMode,
    ): String? = when (mode) {
        is HiddenScreenMode.Standalone -> null
        is HiddenScreenMode.Embedded -> when (silentMode) {
            SilentMode.ARCHIVED -> ARCHIVED_CAPTION
            SilentMode.PROCESSED -> PROCESSED_CAPTION
        }
    }

    /**
     * Trailing text-button label for the embedded mode (preserves the bulk
     * clear-all action that the full summary card used to host as an
     * outlined button). Returns `null` for standalone (the outlined button
     * is still rendered inside the full summary card there).
     */
    fun embeddedClearAllLabel(mode: HiddenScreenMode): String? = when (mode) {
        is HiddenScreenMode.Standalone -> null
        is HiddenScreenMode.Embedded -> CLEAR_ALL_LABEL
    }

    /**
     * Captions are kept verbatim from the legacy summary card's helper line
     * so users do not see a copy regression — only the count restatement and
     * the heavyweight surface are removed.
     */
    const val ARCHIVED_CAPTION: String =
        "같은 앱의 여러 알림은 한 카드로 모아서 보여줘요. 탭하면 최신 내용을 바로 확인할 수 있어요."

    const val PROCESSED_CAPTION: String =
        "이미 확인했거나 이전 버전에서 넘어온 알림이에요. 필요하면 한 번에 지울 수 있어요."

    const val CLEAR_ALL_LABEL: String = "전체 모두 지우기"
}
