package com.smartnoti.app.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingRequirementsTest {

    @Test
    fun listener_denied_blocks_onboarding() {
        val status = OnboardingStatus(
            notificationListenerGranted = false,
            postNotificationsGranted = true,
            postNotificationsRequired = true,
        )
        assertFalse(status.allRequirementsMet)
        assertEquals(
            listOf(OnboardingRequirement.NOTIFICATION_LISTENER),
            status.pendingRequirements,
        )
    }

    @Test
    fun post_notifications_not_required_is_ignored_on_older_android() {
        val status = OnboardingStatus(
            notificationListenerGranted = true,
            postNotificationsGranted = false,
            postNotificationsRequired = false,
        )
        assertTrue(status.allRequirementsMet)
        assertTrue(status.pendingRequirements.isEmpty())
    }

    @Test
    fun post_notifications_required_and_denied_blocks_onboarding() {
        val status = OnboardingStatus(
            notificationListenerGranted = true,
            postNotificationsGranted = false,
            postNotificationsRequired = true,
        )
        assertFalse(status.allRequirementsMet)
        assertEquals(
            listOf(OnboardingRequirement.POST_NOTIFICATIONS),
            status.pendingRequirements,
        )
    }

    @Test
    fun both_denied_reports_both_in_declaration_order() {
        val status = OnboardingStatus(
            notificationListenerGranted = false,
            postNotificationsGranted = false,
            postNotificationsRequired = true,
        )
        assertEquals(
            listOf(
                OnboardingRequirement.NOTIFICATION_LISTENER,
                OnboardingRequirement.POST_NOTIFICATIONS,
            ),
            status.pendingRequirements,
        )
    }

    @Test
    fun all_granted_completes_onboarding() {
        val status = OnboardingStatus(
            notificationListenerGranted = true,
            postNotificationsGranted = true,
            postNotificationsRequired = true,
        )
        assertTrue(status.allRequirementsMet)
    }
}
