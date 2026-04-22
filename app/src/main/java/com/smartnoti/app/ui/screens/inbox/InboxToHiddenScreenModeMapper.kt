package com.smartnoti.app.ui.screens.inbox

import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.ui.screens.hidden.HiddenScreenMode

/**
 * Pure-function mapper from an [InboxTab] to the embed mode that
 * [com.smartnoti.app.ui.screens.hidden.HiddenNotificationsScreen] should run in
 * when hosted by the InboxScreen. Plan
 * `docs/plans/2026-04-22-inbox-denest-and-home-recent-truncate.md` Task 2.
 *
 * Inbox renders DigestScreen directly for [InboxTab.Digest] — that branch must
 * never reach this mapper. Calling `mapToMode(Digest)` is a programmer error
 * and surfaces an [IllegalStateException].
 */
internal object InboxToHiddenScreenModeMapper {
    fun mapToMode(tab: InboxTab): HiddenScreenMode.Embedded = when (tab) {
        InboxTab.Archived -> HiddenScreenMode.Embedded(SilentMode.ARCHIVED)
        InboxTab.Processed -> HiddenScreenMode.Embedded(SilentMode.PROCESSED)
        InboxTab.Digest -> error("Digest is not delegated to HiddenNotificationsScreen")
    }
}
