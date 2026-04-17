package com.smartnoti.app.onboarding

enum class OnboardingRequirement {
    NOTIFICATION_LISTENER,
    POST_NOTIFICATIONS,
}

data class OnboardingStatus(
    val notificationListenerGranted: Boolean,
    val postNotificationsGranted: Boolean,
    val postNotificationsRequired: Boolean,
) {
    val pendingRequirements: List<OnboardingRequirement> = buildList {
        if (!notificationListenerGranted) add(OnboardingRequirement.NOTIFICATION_LISTENER)
        if (postNotificationsRequired && !postNotificationsGranted) {
            add(OnboardingRequirement.POST_NOTIFICATIONS)
        }
    }

    val allRequirementsMet: Boolean = pendingRequirements.isEmpty()
}
