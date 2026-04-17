package com.smartnoti.app.domain.usecase

class PersistentNotificationPolicy {
    fun shouldTreatAsPersistent(
        isOngoing: Boolean,
        isClearable: Boolean,
    ): Boolean {
        return isOngoing || !isClearable
    }

    fun shouldBypassPersistentHiding(
        packageName: String,
        title: String,
        body: String,
        protectCriticalPersistentNotifications: Boolean,
    ): Boolean {
        if (!protectCriticalPersistentNotifications) return false

        val normalizedText = listOf(packageName, title, body)
            .joinToString(" ")
            .lowercase()

        return BYPASS_KEYWORDS.any { keyword -> keyword in normalizedText }
    }

    companion object {
        private val BYPASS_KEYWORDS = setOf(
            "통화",
            "전화",
            "call",
            "dialer",
            "길안내",
            "내비",
            "navigation",
            "maps",
            "녹화",
            "recording",
            "screen record",
            "마이크 사용 중",
            "camera in use",
            "camera access",
            "microphone in use",
        )
    }
}
