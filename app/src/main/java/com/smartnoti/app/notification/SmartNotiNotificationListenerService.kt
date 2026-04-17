package com.smartnoti.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.withContext
import com.smartnoti.app.domain.usecase.DuplicateNotificationPolicy
import com.smartnoti.app.domain.usecase.NotificationCaptureProcessor
import com.smartnoti.app.domain.usecase.NotificationClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class SmartNotiNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val duplicatePolicy = DuplicateNotificationPolicy()
    private val notifier by lazy { SmartNotiNotifier(applicationContext) }

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
        notifier.ensureChannels()
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

        val repository = NotificationRepository.getInstance(applicationContext)
        val rulesRepository = RulesRepository.getInstance(applicationContext)
        val settingsRepository = SettingsRepository.getInstance(applicationContext)

        serviceScope.launch {
            val rules = rulesRepository.currentRules()
            val settings = settingsRepository.observeSettings().first()
            val contentSignature = duplicatePolicy.contentSignature(title = title, body = body)
            val duplicateCount = repository.countRecentDuplicates(
                packageName = sbn.packageName,
                contentSignature = contentSignature,
                sinceMillis = duplicatePolicy.windowStart(sbn.postTime),
            ) + 1
            val captureInput = CapturedNotificationInput(
                packageName = sbn.packageName,
                appName = appName,
                sender = sender,
                title = title,
                body = body,
                postedAtMillis = sbn.postTime,
                quietHours = false,
                duplicateCountInWindow = 0,
            ).withContext(settingsRepository.currentNotificationContext(duplicateCount))

            val notification = processor.process(captureInput, rules)
            repository.save(notification, sbn.postTime, contentSignature)

            val decision = when (notification.status) {
                com.smartnoti.app.domain.model.NotificationStatusUi.PRIORITY -> com.smartnoti.app.domain.model.NotificationDecision.PRIORITY
                com.smartnoti.app.domain.model.NotificationStatusUi.DIGEST -> com.smartnoti.app.domain.model.NotificationDecision.DIGEST
                com.smartnoti.app.domain.model.NotificationStatusUi.SILENT -> com.smartnoti.app.domain.model.NotificationDecision.SILENT
            }
            if (NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                    suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                    suppressedApps = settings.suppressedSourceApps,
                    packageName = sbn.packageName,
                    decision = decision,
                )
            ) {
                withContext(Dispatchers.Main) {
                    cancelNotification(sbn.key)
                }
                notifier.notifySuppressedNotification(
                    decision = decision,
                    packageName = sbn.packageName,
                    appName = appName,
                    title = notification.title,
                    body = notification.body,
                )
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
