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

    @Test
    fun keeps_call_related_persistent_notifications_visible() {
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.android.dialer",
                title = "통화 중",
                body = "00:31",
            )
        )
    }

    @Test
    fun keeps_recording_and_navigation_persistent_notifications_visible() {
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.google.android.apps.maps",
                title = "길안내 중",
                body = "다음 교차로에서 우회전",
            )
        )
        assertTrue(
            policy.shouldBypassPersistentHiding(
                packageName = "com.android.systemui",
                title = "화면 녹화 중",
                body = "탭하여 중지",
            )
        )
    }

    @Test
    fun allows_charging_notifications_to_be_hidden() {
        assertFalse(
            policy.shouldBypassPersistentHiding(
                packageName = "android",
                title = "충전 중",
                body = "배터리 보호",
            )
        )
    }
}
