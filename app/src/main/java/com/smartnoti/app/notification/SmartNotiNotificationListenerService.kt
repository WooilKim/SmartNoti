package com.smartnoti.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.CapturedNotificationInput
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val reconnectSweepCoordinator by lazy {
        ListenerReconnectActiveNotificationSweepCoordinator<StatusBarNotification>(
            appPackageName = packageName,
            packageNameOf = { it.packageName },
            titleOf = { sbn ->
                sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            },
            bodyOf = { sbn ->
                sbn.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
            },
            notificationFlagsOf = { it.notification.flags },
            dedupKeyOf = ::dedupKeyFor,
            existsInStore = { key ->
                NotificationRepository.getInstance(applicationContext).existsByContentSignature(
                    packageName = key.packageName,
                    contentSignature = key.contentSignature,
                    postedAtMillis = key.postTimeMillis,
                )
            },
            onboardingBootstrapPending = {
                SettingsRepository.getInstance(applicationContext).isOnboardingBootstrapPending()
            },
            processNotification = ::processNotification,
        )
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
            // The summary now represents only the "보관 중" tab of the Hidden inbox
            // (plan silent-archive-vs-process-split, Task 5). Already-processed rows
            // are excluded so the count reflects what the user still needs to act on.
            // filterPersistent is applied so this matches Home's StatPill / Hidden
            // screen header. We repost only on count transitions so a swipe-dismissed
            // summary stays dismissed until the archived count actually moves.
            val settingsRepository = SettingsRepository.getInstance(applicationContext)
            var lastCount = -1
            combine(
                repository.observeAll(),
                settingsRepository.observeSettings(),
            ) { notifications, settings ->
                notifications.countSilentArchivedForSummary(
                    hidePersistentNotifications = settings.hidePersistentNotifications,
                )
            }.distinctUntilChanged().collect { count ->
                if (count == lastCount) return@collect
                lastCount = count
                withContext(Dispatchers.Main) {
                    silentSummaryNotifier.post(count)
                }
            }
        }
        enqueueOnboardingBootstrapCheck()
        enqueueReconnectSweep()
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
        val autoExpandedApps = if (!isProtectedSourceNotification) {
            SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
                decision = decision,
                suppressSourceForDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                packageName = sbn.packageName,
                currentApps = settings.suppressedSourceApps,
            )
        } else {
            null
        }
        if (autoExpandedApps != null) {
            settingsRepository.setSuppressedSourceApps(autoExpandedApps)
        }
        val effectiveSuppressedApps = autoExpandedApps ?: settings.suppressedSourceApps
        val shouldSuppressSourceNotification = !isProtectedSourceNotification &&
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                suppressedApps = effectiveSuppressedApps,
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
                    processNotification = { sbn ->
                        // Record the dedup key first so a concurrent reconnect
                        // sweep (or the one scheduled right after bootstrap in
                        // onListenerConnected) never re-enqueues the same item.
                        reconnectSweepCoordinator.recordProcessedByBootstrap(dedupKeyFor(sbn))
                        processNotification(sbn)
                    },
                ).bootstrap(activeNotifications ?: emptyArray())
            }
        }
    }

    /**
     * After the onboarding bootstrap has had its chance to consume the pending
     * flag, sweep the current active-notifications tray to recover anything
     * posted while the listener was disconnected. The coordinator itself
     * defers while [SettingsRepository.isOnboardingBootstrapPending] is still
     * true, so this is safe to enqueue unconditionally after bootstrap.
     */
    private fun enqueueReconnectSweep() {
        serviceScope.launch {
            val actives = runCatching { activeNotifications }.getOrNull() ?: return@launch
            reconnectSweepCoordinator.sweep(actives.asIterable())
        }
    }

    private fun dedupKeyFor(sbn: StatusBarNotification): SweepDedupKey {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        val isPersistent = persistentNotificationPolicy.shouldTreatAsPersistent(
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
        )
        val signature = duplicatePolicy.contentSignature(title, body) +
            if (isPersistent) "|persistent:${sbn.packageName}:${sbn.id}" else ""
        return SweepDedupKey(
            packageName = sbn.packageName,
            contentSignature = signature,
            postTimeMillis = sbn.postTime,
        )
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
