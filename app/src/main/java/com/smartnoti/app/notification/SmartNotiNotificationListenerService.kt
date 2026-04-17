package com.smartnoti.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.usecase.NotificationCaptureProcessor
import com.smartnoti.app.domain.usecase.NotificationClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class SmartNotiNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val processor by lazy {
        NotificationCaptureProcessor(
            classifier = NotificationClassifier(
                vipSenders = setOf("엄마", "팀장"),
                priorityKeywords = setOf("인증번호", "OTP", "결제"),
                shoppingPackages = setOf("com.coupang.mobile"),
            )
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val repository = NotificationRepository.getInstance(applicationContext)
        serviceScope.launch {
            repository.observeAll().collect { notifications ->
                SmartNotiNotificationStore.setNotifications(notifications)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val sender = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val notification = processor.process(
            CapturedNotificationInput(
                packageName = sbn.packageName,
                appName = appName,
                sender = sender,
                title = title,
                body = body,
                postedAtMillis = sbn.postTime,
                quietHours = false,
                duplicateCountInWindow = 0,
            )
        )

        val repository = NotificationRepository.getInstance(applicationContext)
        serviceScope.launch {
            repository.save(notification, sbn.postTime)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
