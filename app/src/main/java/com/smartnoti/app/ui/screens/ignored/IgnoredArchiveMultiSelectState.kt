package com.smartnoti.app.ui.screens.ignored

/**
 * Pure-Kotlin holder for the IgnoredArchiveScreen multi-select interaction
 * (plan `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md`
 * Task 3).
 *
 * IgnoredArchiveScreen renders a single plain list (every IGNORE row in one
 * `LazyColumn`), so the state mirrors
 * [com.smartnoti.app.ui.screens.priority.PriorityScreenMultiSelectState] —
 * `isActive` + the selected notification IDs. Long-press on a row enters
 * selection mode; subsequent body taps toggle membership; removing the last
 * selected row auto-cancels (Gmail-style affordance).
 *
 * Kept Compose-free so the state machine stays unit-test friendly — the
 * IgnoredArchiveScreen wires this via `remember { mutableStateOf(...) }` and
 * replaces the value on each transition.
 */
data class IgnoredArchiveMultiSelectState(
    val isActive: Boolean = false,
    val selectedNotificationIds: Set<String> = emptySet(),
) {
    /** Convenience: the number of currently selected notification IDs. */
    val count: Int get() = selectedNotificationIds.size

    /**
     * Enter selection mode seeded with [seedNotificationId]. The call site
     * usually guards this with `isActive == false` (long-press is wired only
     * when not already in selection mode), but the state itself stays
     * predictable either way: a fresh seed always replaces the prior set.
     */
    fun enterSelection(seedNotificationId: String): IgnoredArchiveMultiSelectState =
        copy(isActive = true, selectedNotificationIds = setOf(seedNotificationId))

    /**
     * Toggle [notificationId] in the selection set. Ignored when not in
     * selection mode. Removing the last selected notificationId auto-cancels
     * selection mode (returns the initial state) so the chrome disappears
     * without an extra explicit cancel.
     */
    fun toggle(notificationId: String): IgnoredArchiveMultiSelectState {
        if (!isActive) return this
        val nextSelected = if (notificationId in selectedNotificationIds) {
            selectedNotificationIds - notificationId
        } else {
            selectedNotificationIds + notificationId
        }
        return if (nextSelected.isEmpty()) {
            IgnoredArchiveMultiSelectState()
        } else {
            copy(selectedNotificationIds = nextSelected)
        }
    }

    /** Reset to the initial inactive state. Used by the explicit "취소" CTA. */
    fun cancel(): IgnoredArchiveMultiSelectState = IgnoredArchiveMultiSelectState()

    /**
     * Alias of [cancel]. Used by call sites after a successful bulk action —
     * semantic distinction (user-initiated cancel vs post-success cleanup)
     * lives in the call site, not in the state.
     */
    fun clear(): IgnoredArchiveMultiSelectState = cancel()
}
