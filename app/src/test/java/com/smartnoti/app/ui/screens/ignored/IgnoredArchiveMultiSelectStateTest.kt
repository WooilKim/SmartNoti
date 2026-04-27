package com.smartnoti.app.ui.screens.ignored

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md` Task 3 —
 * pure-state tests for the multi-select model that drives the bulk-delete
 * ActionBar on `IgnoredArchiveScreen`. Mirrors
 * [com.smartnoti.app.ui.screens.priority.PriorityScreenMultiSelectState]: a
 * single-bucket list with `isActive` + selected notification IDs. Long-press
 * enters; subsequent body taps toggle while active; removing the last
 * selected ID auto-cancels.
 */
class IgnoredArchiveMultiSelectStateTest {

    @Test
    fun initial_state_is_empty_and_inactive() {
        val state = IgnoredArchiveMultiSelectState()

        assertFalse(state.isActive)
        assertEquals(emptySet<String>(), state.selectedNotificationIds)
        assertEquals(0, state.count)
    }

    @Test
    fun enter_selection_seeds_active_state_with_single_notification() {
        val state = IgnoredArchiveMultiSelectState()

        val next = state.enterSelection("noti-a")

        assertTrue(next.isActive)
        assertEquals(setOf("noti-a"), next.selectedNotificationIds)
        assertEquals(1, next.count)
    }

    @Test
    fun toggle_while_active_adds_then_removes_notification_ids() {
        val state = IgnoredArchiveMultiSelectState().enterSelection("noti-a")

        val withTwo = state.toggle("noti-b")
        assertEquals(setOf("noti-a", "noti-b"), withTwo.selectedNotificationIds)
        assertTrue(withTwo.isActive)
        assertEquals(2, withTwo.count)

        val backToOne = withTwo.toggle("noti-b")
        assertEquals(setOf("noti-a"), backToOne.selectedNotificationIds)
        assertTrue(backToOne.isActive)
    }

    @Test
    fun toggle_removing_last_notification_auto_cancels_selection_mode() {
        val state = IgnoredArchiveMultiSelectState().enterSelection("noti-a")

        val cleared = state.toggle("noti-a")

        assertFalse(cleared.isActive)
        assertEquals(emptySet<String>(), cleared.selectedNotificationIds)
    }

    @Test
    fun toggle_when_inactive_is_ignored() {
        val state = IgnoredArchiveMultiSelectState()

        val attempted = state.toggle("noti-a")

        assertFalse(attempted.isActive)
        assertEquals(emptySet<String>(), attempted.selectedNotificationIds)
    }

    @Test
    fun cancel_resets_to_initial_state() {
        val state = IgnoredArchiveMultiSelectState()
            .enterSelection("noti-a")
            .toggle("noti-b")

        val cleared = state.cancel()

        assertFalse(cleared.isActive)
        assertEquals(emptySet<String>(), cleared.selectedNotificationIds)
    }

    @Test
    fun clear_is_alias_of_cancel() {
        val state = IgnoredArchiveMultiSelectState()
            .enterSelection("noti-a")
            .toggle("noti-b")
            .toggle("noti-c")

        val cleared = state.clear()

        assertFalse(cleared.isActive)
        assertEquals(emptySet<String>(), cleared.selectedNotificationIds)
    }
}
