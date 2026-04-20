package com.smartnoti.app.notification

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Signals taken from a posted notification that indicate the source notification must
 * not be cancelled by SmartNoti. Cancelling a media / call / navigation / foreground-service
 * notification via NotificationListenerService breaks the associated MediaSession or
 * foreground service contract (for example it stops YouTube Music playback), so these
 * notifications always bypass suppression regardless of user settings.
 */
internal data class ProtectedSourceNotificationSignals(
    val category: String?,
    val templateName: String?,
    val hasMediaSessionExtra: Boolean,
    val isForegroundService: Boolean,
)

internal object ProtectedSourceNotificationDetector {

    fun isProtected(signals: ProtectedSourceNotificationSignals): Boolean {
        if (signals.category != null && signals.category in PROTECTED_CATEGORIES) return true
        if (signals.hasMediaSessionExtra) return true
        if (signals.templateName != null && signals.templateName.isMediaTemplate()) return true
        if (signals.isForegroundService) return true
        return false
    }

    fun signalsFrom(sbn: StatusBarNotification): ProtectedSourceNotificationSignals {
        val notification = sbn.notification
        return ProtectedSourceNotificationSignals(
            category = notification.category,
            templateName = notification.extras?.getString(Notification.EXTRA_TEMPLATE),
            hasMediaSessionExtra = notification.extras?.containsKey(EXTRA_MEDIA_SESSION) == true,
            isForegroundService = notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
        )
    }

    private fun String.isMediaTemplate(): Boolean {
        return MEDIA_TEMPLATE_SUFFIXES.any { suffix -> endsWith(suffix) }
    }

    // Hardcoded strings instead of Notification.CATEGORY_* so this policy stays testable
    // without the Android framework. These match the platform constants.
    private val PROTECTED_CATEGORIES = setOf(
        "call",
        "transport",
        "navigation",
        "alarm",
        "progress",
    )

    private val MEDIA_TEMPLATE_SUFFIXES = setOf(
        "MediaStyle",
        "BigMediaStyle",
        "DecoratedMediaCustomViewStyle",
    )

    // MediaStyle.EXTRA_MEDIA_SESSION equivalent; kept as a string constant so the detector
    // test does not pull in the Android framework.
    private const val EXTRA_MEDIA_SESSION = "android.mediaSession"
}
