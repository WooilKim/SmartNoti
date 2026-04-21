package com.smartnoti.app.notification

import com.smartnoti.app.data.local.filterPersistent
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode

/**
 * Counts only the notifications the [SilentHiddenSummaryNotifier] summary badge should
 * represent — i.e. "보관 중" (silent archived) items that still occupy a slot in the
 * user's mental inbox.
 *
 * The summary must stay in sync with the "보관 중" tab of the Hidden inbox (plan
 * `silent-archive-vs-process-split`, Task 5). That means:
 *
 * - `NotificationStatusUi.SILENT` rows only — non-silent flows (Priority / Digest)
 *   are never surfaced by this summary.
 * - `silentMode == ARCHIVED` only. `PROCESSED` rows have already been acknowledged
 *   by the user and removed from the tray; `null` is legacy pre-migration SILENT
 *   (see `HiddenGroupsSilentModeFilterTest`) which we treat as PROCESSED so
 *   upgraded users don't see old rows re-appear in the 보관 중 bucket.
 * - `hidePersistentNotifications` is applied first so the summary's count matches
 *   what Home's StatPill and the Hidden screen header show.
 */
internal fun List<NotificationUiModel>.countSilentArchivedForSummary(
    hidePersistentNotifications: Boolean,
): Int = filterSilentArchivedForSummary(hidePersistentNotifications).size

/**
 * Same filter as [countSilentArchivedForSummary] but returns the row list so the listener's
 * per-sender grouping pipeline (plan `silent-tray-sender-grouping` Task 3) can reuse the
 * same "보관 중" contract as the root count. The pure [SilentGroupTrayPlanner] then diff's
 * this list against the previously-posted tray state.
 */
internal fun List<NotificationUiModel>.filterSilentArchivedForSummary(
    hidePersistentNotifications: Boolean,
): List<NotificationUiModel> = filterPersistent(hidePersistentNotifications)
    .filter { it.status == NotificationStatusUi.SILENT && it.silentMode == SilentMode.ARCHIVED }
