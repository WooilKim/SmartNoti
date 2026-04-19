package com.smartnoti.app.notification

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartnoti.app.data.settings.SettingsRepository

class OnboardingActiveNotificationBootstrapCoordinator internal constructor(
    private val settingsRepository: SettingsRepository,
    private val signalConnectedListener: () -> Unit = {},
) {
    suspend fun requestBootstrapForFirstOnboardingCompletion(): Boolean {
        val requested = settingsRepository.requestOnboardingActiveNotificationBootstrap()
        if (requested) {
            signalConnectedListener()
        }
        return requested
    }

    suspend fun consumePendingBootstrapRequest(): Boolean {
        return settingsRepository.consumeOnboardingActiveNotificationBootstrapRequest()
    }

    companion object {
        fun create(context: Context): OnboardingActiveNotificationBootstrapCoordinator {
            val appContext = context.applicationContext
            return OnboardingActiveNotificationBootstrapCoordinator(
                settingsRepository = SettingsRepository.getInstance(appContext),
                signalConnectedListener = {
                    val handledByConnectedService =
                        SmartNotiNotificationListenerService.triggerOnboardingBootstrapIfConnected()
                    if (!handledByConnectedService) {
                        runCatching {
                            NotificationListenerService.requestRebind(
                                ComponentName(appContext, SmartNotiNotificationListenerService::class.java)
                            )
                        }
                    }
                },
            )
        }
    }
}

class OnboardingActiveNotificationBootstrapper<T> internal constructor(
    private val appPackageName: String,
    private val packageNameOf: (T) -> String,
    private val titleOf: (T) -> String,
    private val bodyOf: (T) -> String,
    private val notificationFlagsOf: (T) -> Int,
    private val processNotification: suspend (T) -> Unit,
) {
    suspend fun bootstrap(activeNotifications: Iterable<T>) {
        activeNotifications.forEach { notification ->
            if (shouldProcess(notification)) {
                processNotification(notification)
            }
        }
    }

    fun shouldProcess(notification: T): Boolean {
        if (packageNameOf(notification) == appPackageName) return false

        return !NotificationCapturePolicy.shouldIgnoreCapture(
            title = titleOf(notification),
            body = bodyOf(notification),
            notificationFlags = notificationFlagsOf(notification),
        )
    }
}

class ActiveStatusBarNotificationBootstrapper internal constructor(
    appPackageName: String,
    processNotification: suspend (StatusBarNotification) -> Unit,
) {
    private val delegate = OnboardingActiveNotificationBootstrapper(
        appPackageName = appPackageName,
        packageNameOf = { it.packageName },
        titleOf = { sbn ->
            sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        },
        bodyOf = { sbn ->
            sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        },
        notificationFlagsOf = { it.notification.flags },
        processNotification = processNotification,
    )

    suspend fun bootstrap(activeNotifications: Array<StatusBarNotification>) {
        delegate.bootstrap(activeNotifications.asIterable())
    }

    fun shouldProcess(sbn: StatusBarNotification): Boolean {
        return delegate.shouldProcess(sbn)
    }
}
