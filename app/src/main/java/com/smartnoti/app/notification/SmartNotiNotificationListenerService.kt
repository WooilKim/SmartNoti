package com.smartnoti.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.toDecision
import com.smartnoti.app.domain.model.toDeliveryProfileOrDefault
import com.smartnoti.app.domain.model.withContext
import com.smartnoti.app.domain.usecase.DeliveryProfilePolicy
import com.smartnoti.app.domain.usecase.DuplicateNotificationPolicy
import com.smartnoti.app.domain.usecase.LiveDuplicateCountTracker
import com.smartnoti.app.domain.usecase.NotificationCaptureProcessor
import com.smartnoti.app.domain.usecase.NotificationClassifier
import com.smartnoti.app.domain.usecase.PersistentNotificationPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class SmartNotiNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val duplicatePolicy = DuplicateNotificationPolicy()
    private val liveDuplicateCountTracker = LiveDuplicateCountTracker()
    private val persistentNotificationPolicy = PersistentNotificationPolicy()
    private val notifier by lazy { SmartNotiNotifier(applicationContext) }
    private val silentSummaryNotifier by lazy { SilentHiddenSummaryNotifier(applicationContext) }
    private var storeSyncJob: Job? = null
    private var silentSummaryJob: Job? = null
    private val onboardingBootstrapCoordinator by lazy {
        OnboardingActiveNotificationBootstrapCoordinator.create(applicationContext)
    }

    private val processor by lazy {
        NotificationCaptureProcessor(
            classifier = NotificationClassifier(
                vipSenders = setOf("엄마", "팀장"),
                priorityKeywords = setOf("인증번호", "OTP", "결제"),
                shoppingPackages = setOf("com.coupang.mobile"),
            ),
            deliveryProfilePolicy = DeliveryProfilePolicy(),
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeService = this
        notifier.ensureChannels()
        silentSummaryNotifier.ensureChannel()
        val repository = NotificationRepository.getInstance(applicationContext)
        storeSyncJob?.cancel()
        storeSyncJob = serviceScope.launch {
            repository.observeAll().collect { notifications ->
                SmartNotiNotificationStore.setNotifications(notifications)
            }
        }
        silentSummaryJob?.cancel()
        silentSummaryJob = serviceScope.launch {
            // Only repost when the count changes so we do not churn the tray on every
            // unrelated list update. If the user swipes the summary away, it stays
            // dismissed until the next classification changes the hidden count, which
            // matches the user's expected "I acknowledged this" behavior.
            var lastCount = -1
            repository.observeAll().collect { notifications ->
                val count = notifications.count { it.status == NotificationStatusUi.SILENT }
                if (count == lastCount) return@collect
                lastCount = count
                withContext(Dispatchers.Main) {
                    silentSummaryNotifier.post(count)
                }
            }
        }
        enqueueOnboardingBootstrapCheck()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val bootstrapper = ActiveStatusBarNotificationBootstrapper(
            appPackageName = packageName,
            processNotification = ::processNotification,
        )
        if (!bootstrapper.shouldProcess(sbn)) return

        serviceScope.launch {
            processNotification(sbn)
        }
    }

    private suspend fun processNotification(sbn: StatusBarNotification) {
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

        val rules = rulesRepository.currentRules()
        val settings = settingsRepository.observeSettings().first()
        val isPersistent = persistentNotificationPolicy.shouldTreatAsPersistent(
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
        )
        val shouldBypassPersistentHiding = if (isPersistent) {
            persistentNotificationPolicy.shouldBypassPersistentHiding(
                packageName = sbn.packageName,
                title = title,
                body = body,
                protectCriticalPersistentNotifications = settings.protectCriticalPersistentNotifications,
            )
        } else {
            false
        }
        val contentSignature = duplicatePolicy.contentSignature(
            title = title,
            body = body,
        ) + if (isPersistent) "|persistent:${sbn.packageName}:${sbn.id}" else ""
        val duplicateWindowStart = duplicatePolicy.windowStart(sbn.postTime)
        val duplicateCount = liveDuplicateCountTracker.recordAndCount(
            packageName = sbn.packageName,
            contentSignature = contentSignature,
            sourceEntryKey = sbn.key,
            postedAtMillis = sbn.postTime,
            windowStartMillis = duplicateWindowStart,
            persistedDuplicateCount = repository.countRecentDuplicates(
                packageName = sbn.packageName,
                contentSignature = contentSignature,
                sinceMillis = duplicateWindowStart,
            ),
        )

        val captureInput = CapturedNotificationInput(
            packageName = sbn.packageName,
            appName = appName,
            sender = sender,
            title = title,
            body = body,
            postedAtMillis = sbn.postTime,
            quietHours = false,
            duplicateCountInWindow = if (isPersistent) 1 else duplicateCount,
            isPersistent = isPersistent && !shouldBypassPersistentHiding,
            sourceEntryKey = sbn.key,
        ).withContext(settingsRepository.currentNotificationContext(if (isPersistent) 1 else duplicateCount))

        val baseNotification = processor.process(
            input = captureInput,
            rules = rules,
            settings = settings,
        )

        val decision = baseNotification.status.toDecision()
        val deliveryProfile = baseNotification.toDeliveryProfileOrDefault()
        val shouldHidePersistentSourceNotification =
            (isPersistent && !shouldBypassPersistentHiding) && settings.hidePersistentSourceNotifications
        val isProtectedSourceNotification = ProtectedSourceNotificationDetector.isProtected(
            ProtectedSourceNotificationDetector.signalsFrom(sbn),
        )
        val shouldSuppressSourceNotification = !isProtectedSourceNotification &&
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                suppressedApps = settings.suppressedSourceApps,
                packageName = sbn.packageName,
                decision = decision,
            )
        val sourceRouting = if (isProtectedSourceNotification) {
            // Media / call / navigation / foreground-service notifications back a live
            // MediaSession or foreground service. Cancelling them breaks playback
            // (for example YouTube Music stops) or tears down the service, so we never
            // route them through suppression regardless of user settings.
            SourceNotificationRouting(
                cancelSourceNotification = false,
                notifyReplacementNotification = false,
            )
        } else {
            SourceNotificationRoutingPolicy.route(
                decision = decision,
                hidePersistentSourceNotification = shouldHidePersistentSourceNotification,
                suppressSourceNotification = shouldSuppressSourceNotification,
            )
        }
        val suppressionState = SourceNotificationSuppressionStateResolver.resolve(
            decision = decision,
            suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
            suppressedApps = settings.suppressedSourceApps,
            packageName = sbn.packageName,
            hidePersistentSourceNotifications = settings.hidePersistentSourceNotifications,
            isPersistent = isPersistent,
            bypassPersistentHiding = shouldBypassPersistentHiding,
            sourceRouting = sourceRouting,
        )
        var replacementNotificationPosted = false
        if (sourceRouting.cancelSourceNotification) {
            withContext(Dispatchers.Main) {
                cancelNotification(sbn.key)
            }
        }
        if (sourceRouting.notifyReplacementNotification) {
            notifier.notifySuppressedNotification(
                decision = decision,
                packageName = sbn.packageName,
                appName = appName,
                title = baseNotification.title,
                body = baseNotification.body,
                notificationId = baseNotification.id,
                reasonTags = baseNotification.reasonTags,
                deliveryProfile = deliveryProfile,
            )
            replacementNotificationPosted = true
        }
        val notification = baseNotification.copy(
            sourceSuppressionState = suppressionState,
            replacementNotificationIssued = SourceNotificationSuppressionStateResolver.replacementNotificationRecorded(
                sourceRouting = sourceRouting,
                replacementNotificationPosted = replacementNotificationPosted,
            ),
            isPersistent = isPersistent,
        )
        repository.save(notification, sbn.postTime, contentSignature)
    }

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        storeSyncJob?.cancel()
        silentSummaryJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        if (activeService === this) {
            activeService = null
        }
        super.onListenerDisconnected()
    }

    private fun enqueueOnboardingBootstrapCheck() {
        serviceScope.launch {
            if (onboardingBootstrapCoordinator.consumePendingBootstrapRequest()) {
                ActiveStatusBarNotificationBootstrapper(
                    appPackageName = packageName,
                    processNotification = ::processNotification,
                ).bootstrap(activeNotifications ?: emptyArray())
            }
        }
    }

    companion object {
        @Volatile
        private var activeService: SmartNotiNotificationListenerService? = null

        fun triggerOnboardingBootstrapIfConnected(): Boolean {
            val service = activeService ?: return false
            service.enqueueOnboardingBootstrapCheck()
            return true
        }
    }
}
