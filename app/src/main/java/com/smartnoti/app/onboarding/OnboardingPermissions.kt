package com.smartnoti.app.onboarding

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.smartnoti.app.notification.SmartNotiNotificationListenerService

object OnboardingPermissions {

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val target = ComponentName(
            context,
            SmartNotiNotificationListenerService::class.java,
        )
        return flat.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == target
        }
    }

    fun isPostNotificationsRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun isPostNotificationsGranted(context: Context): Boolean {
        if (!isPostNotificationsRequired()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun currentStatus(context: Context): OnboardingStatus {
        return OnboardingStatus(
            notificationListenerGranted = isNotificationListenerEnabled(context),
            postNotificationsGranted = isPostNotificationsGranted(context),
            postNotificationsRequired = isPostNotificationsRequired(),
        )
    }

    fun notificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
