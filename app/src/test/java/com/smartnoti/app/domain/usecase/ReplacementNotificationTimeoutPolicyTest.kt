package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.NotificationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 1.
 *
 * Pins [ReplacementNotificationTimeoutPolicy]'s contract for the six branches
 * called out in the plan so the notifier wiring (Task 2) cannot accidentally
 * leak a stale or non-positive timeout into Android's NotificationManager.
 */
class ReplacementNotificationTimeoutPolicyTest {

    private val policy = ReplacementNotificationTimeoutPolicy()

    @Test
    fun returns_null_when_master_toggle_is_off_for_every_decision() {
        val disabled = SmartNotiSettings(
            replacementAutoDismissEnabled = false,
            replacementAutoDismissMinutes = 30,
        )

        NotificationDecision.values().forEach { decision ->
            assertNull(
                "decision=$decision must yield null when toggle is OFF",
                policy.timeoutMillisFor(disabled, decision),
            )
        }
    }

    @Test
    fun returns_null_for_priority_even_when_enabled() {
        val enabled = SmartNotiSettings(
            replacementAutoDismissEnabled = true,
            replacementAutoDismissMinutes = 30,
        )

        assertNull(policy.timeoutMillisFor(enabled, NotificationDecision.PRIORITY))
    }

    @Test
    fun returns_null_for_ignore_even_when_enabled() {
        val enabled = SmartNotiSettings(
            replacementAutoDismissEnabled = true,
            replacementAutoDismissMinutes = 30,
        )

        assertNull(policy.timeoutMillisFor(enabled, NotificationDecision.IGNORE))
    }

    @Test
    fun returns_thirty_minutes_in_millis_for_digest_when_enabled() {
        val enabled = SmartNotiSettings(
            replacementAutoDismissEnabled = true,
            replacementAutoDismissMinutes = 30,
        )

        assertEquals(
            30L * 60_000L,
            policy.timeoutMillisFor(enabled, NotificationDecision.DIGEST),
        )
    }

    @Test
    fun returns_five_minutes_in_millis_for_silent_when_enabled() {
        val enabled = SmartNotiSettings(
            replacementAutoDismissEnabled = true,
            replacementAutoDismissMinutes = 5,
        )

        assertEquals(
            5L * 60_000L,
            policy.timeoutMillisFor(enabled, NotificationDecision.SILENT),
        )
    }

    @Test
    fun returns_null_when_minutes_is_non_positive_even_when_enabled() {
        val zero = SmartNotiSettings(
            replacementAutoDismissEnabled = true,
            replacementAutoDismissMinutes = 0,
        )
        val negative = SmartNotiSettings(
            replacementAutoDismissEnabled = true,
            replacementAutoDismissMinutes = -10,
        )

        assertNull(policy.timeoutMillisFor(zero, NotificationDecision.DIGEST))
        assertNull(policy.timeoutMillisFor(negative, NotificationDecision.SILENT))
    }
}
