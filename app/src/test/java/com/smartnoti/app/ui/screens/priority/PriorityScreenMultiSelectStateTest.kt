package com.smartnoti.app.ui.screens.priority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `2026-04-26-priority-inbox-bulk-reclassify.md` Task 3 — pure-state tests
 * for the multi-select model that drives the bulk-reclassify ActionBar on
 * [PriorityScreen]. Keeps the multi-select decisions outside Compose so the
 * regression cost stays low.
 *
 * PriorityScreen has a single bucket (every PRIORITY row in one list), so the
 * model is simpler than rules-management's two-bucket variant: no `Bucket`
 * enum, just an `isActive` flag + the selected notification IDs.
 */
class PriorityScreenMultiSelectStateTest {

    @Test
    fun initial_state_is_empty_and_inactive() {
        val state = PriorityScreenMultiSelectState()

        assertFalse(state.isActive)
        assertEquals(emptySet<String>(), state.selectedNotificationIds)
    }

    @Test
    fun enter_selection_seeds_active_state_with_single_notification() {
        val state = PriorityScreenMultiSelectState()

        val next = state.enterSelection("noti-a")

        assertTrue(next.isActive)
        assertEquals(setOf("noti-a"), next.selectedNotificationIds)
    }

    @Test
    fun toggle_while_active_adds_then_removes_notification_ids() {
        val state = PriorityScreenMultiSelectState().enterSelection("noti-a")

        val withTwo = state.toggle("noti-b")
        assertEquals(setOf("noti-a", "noti-b"), withTwo.selectedNotificationIds)
        assertTrue(withTwo.isActive)

        val backToOne = withTwo.toggle("noti-b")
        assertEquals(setOf("noti-a"), backToOne.selectedNotificationIds)
        assertTrue(backToOne.isActive)
    }

    @Test
    fun toggle_removing_last_notification_auto_cancels_selection_mode() {
        val state = PriorityScreenMultiSelectState().enterSelection("noti-a")

        val cleared = state.toggle("noti-a")

        assertFalse(cleared.isActive)
        assertEquals(emptySet<String>(), cleared.selectedNotificationIds)
    }

    @Test
    fun toggle_when_inactive_is_ignored() {
        val state = PriorityScreenMultiSelectState()

        val attempted = state.toggle("noti-a")

        assertFalse(attempted.isActive)
        assertEquals(emptySet<String>(), attempted.selectedNotificationIds)
    }

    @Test
    fun cancel_resets_to_initial_state() {
        val state = PriorityScreenMultiSelectState()
            .enterSelection("noti-a")
            .toggle("noti-b")

        val cleared = state.cancel()

        assertFalse(cleared.isActive)
        assertEquals(emptySet<String>(), cleared.selectedNotificationIds)
    }

    @Test
    fun clear_is_alias_of_cancel() {
        // Used by call sites after a successful bulk action — semantic
        // distinction lives in the call site, not in the state.
        val state = PriorityScreenMultiSelectState()
            .enterSelection("noti-a")
            .toggle("noti-b")
            .toggle("noti-c")

        val cleared = state.clear()

        assertFalse(cleared.isActive)
        assertEquals(emptySet<String>(), cleared.selectedNotificationIds)
    }
}
