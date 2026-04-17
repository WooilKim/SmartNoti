package com.smartnoti.app.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentNotificationPolicyTest {

    private val policy = PersistentNotificationPolicy()

    @Test
    fun treats_ongoing_notifications_as_persistent() {
        assertTrue(policy.shouldTreatAsPersistent(isOngoing = true, isClearable = false))
    }

    @Test
    fun treats_non_clearable_notifications_as_persistent() {
        assertTrue(policy.shouldTreatAsPersistent(isOngoing = false, isClearable = false))
    }

    @Test
    fun ignores_normal_clearable_notifications() {
        assertFalse(policy.shouldTreatAsPersistent(isOngoing = false, isClearable = true))
    }
}
