package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.InboxSortMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 1.
 *
 * Pure helper that re-orders inbox rows / groups according to the user's
 * selected [InboxSortMode]. The DB query layer is untouched — callers feed
 * already-fetched lists in and get a new `List` back.
 *
 * Designed to be cheap on the hundreds-of-rows scale that the unified inbox
 * actually paints. Migrating to a DB-side ORDER BY is a separate plan should
 * row counts blow up.
 */
class InboxSortPlanner {

    /**
     * Re-orders a flat list of [NotificationUiModel].
     *
     * - [InboxSortMode.RECENT]: `postedAtMillis` descending.
     * - [InboxSortMode.IMPORTANCE]: status order PRIORITY=0, DIGEST=1, SILENT=2,
     *   IGNORE=3; ties broken by `postedAtMillis` descending.
     * - [InboxSortMode.BY_APP]: `appName.lowercase()` ascending; ties broken by
     *   `postedAtMillis` descending so the most recent of an app shows first.
     */
    fun sortFlatRows(
        rows: List<NotificationUiModel>,
        mode: InboxSortMode,
    ): List<NotificationUiModel> {
        if (rows.isEmpty()) return rows
        return when (mode) {
            InboxSortMode.RECENT -> rows.sortedByDescending { it.postedAtMillis }
            InboxSortMode.IMPORTANCE -> rows.sortedWith(
                compareBy<NotificationUiModel> { statusOrder(it.status) }
                    .thenByDescending { it.postedAtMillis }
            )
            InboxSortMode.BY_APP -> rows.sortedWith(
                compareBy<NotificationUiModel> { it.appName.lowercase() }
                    .thenByDescending { it.postedAtMillis }
            )
        }
    }

    /**
     * Re-orders a list of [DigestGroupUiModel].
     *
     * - [InboxSortMode.RECENT]: by each group's most-recent `postedAtMillis`
     *   descending. Empty groups (no items) sort to the bottom.
     * - [InboxSortMode.IMPORTANCE]: by the group's status (assuming all rows in
     *   a group share a single status, which is the contract of
     *   `observeDigestGroupsFiltered`); ties broken by group recency desc.
     * - [InboxSortMode.BY_APP]: by `appName.lowercase()` ascending; ties broken
     *   by group recency desc.
     */
    fun sortGroups(
        groups: List<DigestGroupUiModel>,
        mode: InboxSortMode,
    ): List<DigestGroupUiModel> {
        if (groups.isEmpty()) return groups
        return when (mode) {
            InboxSortMode.RECENT -> groups.sortedByDescending { groupRecency(it) }
            InboxSortMode.IMPORTANCE -> groups.sortedWith(
                compareBy<DigestGroupUiModel> { statusOrder(groupStatus(it)) }
                    .thenByDescending { groupRecency(it) }
            )
            InboxSortMode.BY_APP -> groups.sortedWith(
                compareBy<DigestGroupUiModel> { it.appName.lowercase() }
                    .thenByDescending { groupRecency(it) }
            )
        }
    }

    private fun groupRecency(group: DigestGroupUiModel): Long {
        if (group.items.isEmpty()) return Long.MIN_VALUE
        return group.items.maxOf { it.postedAtMillis }
    }

    /**
     * The status of a group is the status of any row in it (the repository
     * groups within a single status). Defaults to [NotificationStatusUi.SILENT]
     * for empty groups so they sort behind real content rather than crashing.
     */
    private fun groupStatus(group: DigestGroupUiModel): NotificationStatusUi {
        return group.items.firstOrNull()?.status ?: NotificationStatusUi.SILENT
    }

    private fun statusOrder(status: NotificationStatusUi): Int = when (status) {
        NotificationStatusUi.PRIORITY -> 0
        NotificationStatusUi.DIGEST -> 1
        NotificationStatusUi.SILENT -> 2
        NotificationStatusUi.IGNORE -> 3
    }
}
