package com.smartnoti.app.ui.screens.hidden

import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.usecase.SilentGroupKey

/**
 * Host mode for [HiddenNotificationsScreen]. Plan
 * `docs/plans/2026-04-22-inbox-denest-and-home-recent-truncate.md` Task 2.
 *
 * The same composable serves two entry points with different chrome:
 *
 * - [Standalone] — legacy `Routes.Hidden` deep link (tray group-summary
 *   contentIntent + onboarding flows). Renders the back button, ScreenHeader,
 *   and the in-screen ARCHIVED/PROCESSED segment row. The selected segment is
 *   persisted across config changes via `rememberSaveable`. Optional
 *   [Standalone.initialFilter] snaps the user to the deep-link group.
 * - [Embedded] — invoked from `InboxScreen`'s outer 보관 중 / 처리됨 sub-tab.
 *   The outer screen already shows a header and outer tab selection; this mode
 *   suppresses the screen's own header + tab row and pre-resolves the
 *   [SilentMode] segment from the outer selection so the body matches the
 *   user's outer pick exactly. There is no nested choice to make.
 */
sealed class HiddenScreenMode {
    data class Standalone(val initialFilter: SilentGroupKey? = null) : HiddenScreenMode()
    data class Embedded(val silentMode: SilentMode) : HiddenScreenMode()
}
