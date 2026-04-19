package com.smartnoti.app.ui.notificationaccess

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.smartnoti.app.onboarding.OnboardingPermissions
import com.smartnoti.app.onboarding.OnboardingStatus

internal const val NOTIFICATION_ACCESS_SETTINGS_MISSING_MESSAGE = "알림 접근 설정을 찾을 수 없어요."

fun notificationAccessLifecycleObserver(
    statusProvider: () -> OnboardingStatus,
    onStatusChanged: (OnboardingStatus) -> Unit,
): LifecycleEventObserver {
    return LifecycleEventObserver { _: LifecycleOwner, event: Lifecycle.Event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            onStatusChanged(statusProvider())
        }
    }
}

fun openNotificationAccessSettings(
    context: Context,
    intentProvider: () -> Intent = { OnboardingPermissions.notificationListenerSettingsIntent() },
    onActivityNotFound: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    },
): Boolean {
    return try {
        context.startActivity(intentProvider())
        true
    } catch (_: ActivityNotFoundException) {
        onActivityNotFound(NOTIFICATION_ACCESS_SETTINGS_MISSING_MESSAGE)
        false
    }
}