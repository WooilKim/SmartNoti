package com.smartnoti.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartnoti.app.BuildConfig
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationEntity
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.local.toEntity
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationUiModel
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
    // Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 4.
    // `processNotification` now builds a fresh `DuplicateNotificationPolicy`
    // from `SmartNotiSettings.duplicateWindowMinutes` on every call so the
    // user-tunable dropdown takes effect on the next notification. The
    // tracker / repository themselves are still long-lived; only the policy
    // facade (signature + windowStart) is rebuilt — both methods are pure
    // and cheap. The legacy field-level instance is kept for the
    // `dedupKeyFor` helper which only consumes `contentSignature` (no window
    // dependency) and runs from coordinator paths that do not have a
    // settings snapshot in scope.
    private val duplicatePolicy = DuplicateNotificationPolicy(windowMillis = 10 * 60 * 1000L)
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
                // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md`
                // Task 4: the constructor value is now a fallback used only
                // when the processor passes `shoppingPackagesOverride = null`.
                // The hot path (NotificationCaptureProcessor.process) always
                // forwards `settings.quietHoursPackages` so this default never
                // wins in production — but pointing at the SSOT keeps the
                // safety net aligned with the Settings default if a future
                // call site forgets the override.
                shoppingPackages = com.smartnoti.app.data.settings.SmartNotiSettings
                    .DEFAULT_QUIET_HOURS_PACKAGES,
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
        // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md`
        // Task 4: kick the suppress-source v1 migration before any
        // observable starts. Idempotent — gated by the
        // `suppress_source_migration_v1_applied` key. Running it here
        // (in addition to MainActivity) covers the "listener boots before
        // the activity is opened post-upgrade" path so the first
        // post-upgrade notification sees the migrated value.
        serviceScope.launch {
            SettingsRepository.getInstance(applicationContext).applyPendingMigrations()
        }
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
            //
            // Task 3 of `silent-tray-sender-grouping` layers a per-sender (fallback:
            // per-app) group summary surface on top of the legacy root summary. The
            // pure [SilentGroupTrayPlanner] turns the current list of SILENT ARCHIVED
            // rows into concrete post/cancel actions, which we dispatch through the
            // same notifier. Plan Q3-A ("SILENT never occupies tray alone") is kept
            // because the planner cancels the lone child whenever a group drops below
            // the 2-row threshold.
            val settingsRepository = SettingsRepository.getInstance(applicationContext)
            val planner = SilentGroupTrayPlanner()
            var lastCount = -1
            var trayState = SilentGroupTrayState.EMPTY
            combine(
                repository.observeAll(),
                settingsRepository.observeSettings(),
            ) { notifications, settings ->
                val archivedRows = notifications.filterSilentArchivedForSummary(
                    hidePersistentNotifications = settings.hidePersistentNotifications,
                )
                SilentSummarySnapshot(
                    rows = archivedRows,
                    count = archivedRows.size,
                    settings = settings,
                )
            }.distinctUntilChanged().collect { snapshot ->
                val rootCountChanged = snapshot.count != lastCount
                lastCount = snapshot.count
                val plan = planner.plan(
                    previousState = trayState,
                    currentSilent = snapshot.rows,
                )
                trayState = plan.nextState
                withContext(Dispatchers.Main) {
                    if (rootCountChanged) {
                        silentSummaryNotifier.post(snapshot.count, snapshot.settings)
                    }
                    plan.summaryCancels.forEach { key ->
                        silentSummaryNotifier.cancelGroupSummary(key)
                    }
                    plan.childCancels.forEach { cancel ->
                        silentSummaryNotifier.cancelGroupChild(cancel.notificationId)
                    }
                    plan.childPosts.forEach { post ->
                        val entity = post.entity.toGroupTrayEntity()
                        silentSummaryNotifier.postGroupChild(
                            notificationId = post.notificationId,
                            entity = entity,
                            key = post.key,
                            settings = snapshot.settings,
                        )
                    }
                    plan.summaryPosts.forEach { post ->
                        silentSummaryNotifier.postGroupSummary(
                            key = post.key,
                            count = post.count,
                            preview = post.preview.map { it.toGroupTrayEntity() },
                            rootDeepLink = SilentHiddenSummaryNotifier.ROUTE_HIDDEN,
                            settings = snapshot.settings,
                        )
                    }
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
        // Plan `2026-04-27-silent-sender-messagingstyle-gate.md` Task 2:
        // gate the EXTRA_TITLE → sender fallback behind a MessagingStyle hint
        // so non-messaging apps (shopping / news / promo) no longer leak
        // product names into NotificationEntity.sender. The grouping policy's
        // App-fallback then takes over for those rows.
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
        val sender = MessagingStyleSenderResolver.resolve(
            MessagingStyleSenderInput(
                conversationTitle = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
                title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString(),
                template = extras.getString(android.app.Notification.EXTRA_TEMPLATE),
                hasMessages = (messages?.isNotEmpty() == true),
            )
        )

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        val repository = NotificationRepository.getInstance(applicationContext)
        val rulesRepository = RulesRepository.getInstance(applicationContext)
        val categoriesRepository = CategoriesRepository.getInstance(applicationContext)
        val settingsRepository = SettingsRepository.getInstance(applicationContext)

        val rules = rulesRepository.currentRules()
        // Plan `2026-04-22-categories-runtime-wiring-fix.md` Task 4 (Drift #1):
        // without this read the Classifier always falls back to SILENT because
        // the Category.action it routes through is never in scope. Read at
        // the same call site as rules so both snapshots are consistent.
        val categories = categoriesRepository.currentCategories()
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
        // Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 4:
        // build the policy from the freshly-read settings snapshot so a
        // dropdown change in Settings reaches the very next notification
        // without an app restart. The signature path is window-independent
        // (legacy field-level `duplicatePolicy` would do) but we use the
        // settings-driven instance everywhere in this method to keep the
        // call site coherent.
        val settingsDrivenDuplicatePolicy = DuplicateNotificationPolicy(
            windowMillis = settings.duplicateWindowMinutes * 60_000L,
        )
        val contentSignature = settingsDrivenDuplicatePolicy.contentSignature(
            title = title,
            body = body,
        ) + if (isPersistent) "|persistent:${sbn.packageName}:${sbn.id}" else ""
        val duplicateWindowStart = settingsDrivenDuplicatePolicy.windowStart(sbn.postTime)
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
            // Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 4:
            // forward the user-tunable threshold to the classifier through
            // the processor. `settings` is the snapshot read above at the
            // same call site as rules / categories, so all three knobs are
            // mutually consistent for this notification.
            duplicateThreshold = settings.duplicateDigestThreshold,
        ).withContext(settingsRepository.currentNotificationContext(if (isPersistent) 1 else duplicateCount))

        val classifiedNotification = processor.process(
            input = captureInput,
            rules = rules,
            settings = settings,
            categories = categories,
        )

        // Debug-only override — lets the journey-tester priority-inbox
        // recipe pin a classification regardless of accumulated user
        // rules on the emulator. Release builds fold this branch to
        // dead code because `BuildConfig.DEBUG == false`, so the
        // `DebugClassificationOverride` reference is never resolved
        // in an unminified release build and R8 strips the call site
        // entirely under minification.
        // Plan: docs/plans/2026-04-22-priority-recipe-debug-inject-hook.md
        val baseNotification = if (BuildConfig.DEBUG) {
            DebugClassificationOverride.resolve(extras, classifiedNotification)
        } else {
            classifiedNotification
        }

        val isProtectedSourceNotification = ProtectedSourceNotificationDetector.isProtected(
            ProtectedSourceNotificationDetector.signalsFrom(sbn),
        )

        // Plan `2026-04-27-refactor-listener-process-notification-extract.md`
        // Task 2: the post-classifier "decision → side-effect" branch now
        // lives in [NotificationDecisionPipeline]. Statement order inside
        // dispatch is identical to the previous inline implementation; the
        // pipeline routes side effects through [SourceTrayActions] so
        // `NotificationDecisionPipelineCharacterizationTest` can assert
        // against a fake without the listener.
        val pipeline = NotificationDecisionPipeline(
            actions = ListenerSourceTrayActions(
                service = this,
                notifier = notifier,
                repository = repository,
                settingsRepository = settingsRepository,
            ),
        )
        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                postedAtMillis = sbn.postTime,
                contentSignature = contentSignature,
                settings = settings,
                isPersistent = isPersistent,
                shouldBypassPersistentHiding = shouldBypassPersistentHiding,
                isProtectedSourceNotification = isProtectedSourceNotification,
            )
        )
    }

    /**
     * Production [SourceTrayActions] that bridges the pure pipeline back to
     * the listener-service-only `cancelNotification` (main dispatcher) and
     * the existing notifier / repository / settings-repository singletons.
     */
    private class ListenerSourceTrayActions(
        private val service: SmartNotiNotificationListenerService,
        private val notifier: SmartNotiNotifier,
        private val repository: NotificationRepository,
        private val settingsRepository: SettingsRepository,
    ) : SourceTrayActions {
        override suspend fun cancelSource(key: String) {
            withContext(Dispatchers.Main) {
                service.cancelNotification(key)
            }
        }

        override fun postReplacement(
            decision: NotificationDecision,
            packageName: String,
            appName: String,
            title: String,
            body: String,
            notificationId: String,
            reasonTags: List<String>,
            settings: com.smartnoti.app.data.settings.SmartNotiSettings,
            deliveryProfile: com.smartnoti.app.domain.model.DeliveryProfile,
        ) {
            notifier.notifySuppressedNotification(
                decision = decision,
                packageName = packageName,
                appName = appName,
                title = title,
                body = body,
                notificationId = notificationId,
                reasonTags = reasonTags,
                settings = settings,
                deliveryProfile = deliveryProfile,
            )
        }

        override suspend fun setSuppressedApps(apps: Set<String>) {
            settingsRepository.setSuppressedSourceApps(apps)
        }

        override suspend fun save(
            notification: NotificationUiModel,
            postedAtMillis: Long,
            contentSignature: String,
        ) {
            repository.save(notification, postedAtMillis, contentSignature)
        }
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

        /**
         * Debug-only re-entry point invoked from
         * `com.smartnoti.app.debug.DebugBootstrapRehearsalReceiver`. Hands the
         * connected listener's `activeNotifications` snapshot, app package
         * name, dedup-recording hook, and `processNotification` callback to
         * [block] — the receiver wires those into
         * `DebugBootstrapRehearsal.rehearse` to re-run the bootstrap pipeline
         * without consulting the production one-shot pending flag, so the
         * `onboarding-bootstrap` journey verification recipe can be automated
         * (no `pm clear`, no permission grant wipe). Returns `false` when the
         * listener is not currently bound — the receiver then requests a
         * rebind.
         *
         * The call site in production is the static
         * [rehearseOnboardingBootstrapIfConnected] helper below; the only
         * caller is the debug-source-set receiver, so release APKs never
         * exercise this path. Plan:
         * `docs/plans/2026-04-27-onboarding-bootstrap-non-destructive-recipe.md`.
         */
        fun rehearseOnboardingBootstrapIfConnected(
            block: (
                appPackageName: String,
                actives: Array<StatusBarNotification>,
                recordProcessedByBootstrap: (StatusBarNotification) -> Unit,
                processNotification: suspend (StatusBarNotification) -> Unit,
            ) -> Unit,
        ): Boolean {
            val service = activeService ?: return false
            val actives = runCatching { service.activeNotifications }.getOrNull() ?: emptyArray()
            block(
                service.packageName,
                actives,
                { sbn -> service.reconnectSweepCoordinator.recordProcessedByBootstrap(service.dedupKeyFor(sbn)) },
                { sbn -> service.processNotification(sbn) },
            )
            return true
        }

        /**
         * Ask the currently connected listener service instance (if any) to cancel
         * the tray entry identified by [key].
         *
         * Used by Detail's "처리 완료로 표시" action to chain the original tray
         * notification removal onto a successful `markSilentProcessed` flip
         * (plan `silent-archive-drift-fix` Task 3). `cancelNotification(key)`
         * can only be invoked on a live [NotificationListenerService] instance,
         * so we dispatch through [activeService]. When the service is not
         * bound (OS reclaimed it, user toggled access off, etc.) this helper
         * returns `false` so callers can surface a best-effort outcome to
         * the UI without mutating DB state.
         *
         * The cancel is dispatched on the main dispatcher to match the other
         * `cancelNotification(key)` call site inside [processNotification].
         */
        fun cancelSourceEntryIfConnected(key: String): Boolean {
            val service = activeService ?: return false
            return runCatching {
                service.cancelNotification(key)
            }.isSuccess
        }
    }
}

/**
 * Snapshot emitted by the silent summary flow — bundles the filtered SILENT × ARCHIVED rows
 * with the derived count so the collector can drive both the legacy root summary count and
 * the per-sender grouping planner off a single distinctUntilChanged stream.
 */
internal data class SilentSummarySnapshot(
    val rows: List<NotificationUiModel>,
    val count: Int,
    // Plan `2026-04-27-tray-replacement-auto-dismiss-timeout.md` Task 2:
    // settings snapshot so the SILENT summary / group tray notifier can
    // wire `replacementAutoDismissEnabled` / `replacementAutoDismissMinutes`
    // into `setTimeoutAfter`. Carried with the snapshot so all three
    // post() / postGroupSummary() / postGroupChild() call sites see the
    // same coherent value.
    val settings: com.smartnoti.app.data.settings.SmartNotiSettings,
)

/**
 * Converts a [NotificationUiModel] into the minimal [NotificationEntity] shape the group
 * notifier needs to render a child / summary preview. We don't persist through this path,
 * so [NotificationUiModel.postedAtMillis] is reused directly.
 */
internal fun NotificationUiModel.toGroupTrayEntity(): NotificationEntity =
    toEntity(postedAtMillis = postedAtMillis)
