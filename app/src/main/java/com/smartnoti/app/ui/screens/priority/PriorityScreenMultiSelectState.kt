package com.smartnoti.app.ui.screens.priority

/**
 * Pure-Kotlin holder for the PriorityScreen multi-select interaction (plan
 * `docs/plans/2026-04-26-priority-inbox-bulk-reclassify.md` Tasks 3 + 4).
 *
 * PriorityScreen is a single-bucket list — every PRIORITY row lives in one
 * `LazyColumn`, so the state is simpler than the two-bucket variant in
 * `RulesScreenMultiSelectState`: just `isActive` + the selected notification
 * IDs. Long-press on a row enters selection mode; subsequent body taps within
 * the screen toggle membership; removing the last selected row auto-cancels
 * (mirrors the rules-management Gmail-style pattern).
 *
 * Kept Compose-free so the state machine stays unit-test friendly — the
 * PriorityScreen wires this via `remember { mutableStateOf(...) }` and
 * replaces the value on each transition.
 */
data class PriorityScreenMultiSelectState(
    val isActive: Boolean = false,
    val selectedNotificationIds: Set<String> = emptySet(),
) {
    /**
     * Enter selection mode seeded with [seedNotificationId]. The call site
     * usually guards this with `isActive == false` (long-press is wired only
     * when not already in selection mode), but the state itself stays
     * predictable either way: a fresh seed always replaces the prior set.
     */
    fun enterSelection(seedNotificationId: String): PriorityScreenMultiSelectState =
        copy(isActive = true, selectedNotificationIds = setOf(seedNotificationId))

    /**
     * Toggle [notificationId] in the selection set. Ignored when not in
     * selection mode. Removing the last selected notificationId auto-cancels
     * selection mode (returns the initial state) so the chrome disappears
     * without an extra explicit cancel — same simplification as
     * `RulesScreenMultiSelectState`.
     */
    fun toggle(notificationId: String): PriorityScreenMultiSelectState {
        if (!isActive) return this
        val nextSelected = if (notificationId in selectedNotificationIds) {
            selectedNotificationIds - notificationId
        } else {
            selectedNotificationIds + notificationId
        }
        return if (nextSelected.isEmpty()) {
            PriorityScreenMultiSelectState()
        } else {
            copy(selectedNotificationIds = nextSelected)
        }
    }

    /** Reset to the initial inactive state. Used by the explicit "취소" CTA. */
    fun cancel(): PriorityScreenMultiSelectState = PriorityScreenMultiSelectState()

    /**
     * Alias of [cancel]. Used by call sites after a successful bulk action —
     * semantic distinction (user-initiated cancel vs post-success cleanup)
     * lives in the call site, not in the state.
     */
    fun clear(): PriorityScreenMultiSelectState = cancel()
}
