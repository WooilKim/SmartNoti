package com.smartnoti.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.smartnoti.app.BuildConfig
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.ActiveTrayEntry
import com.smartnoti.app.data.local.NotificationEntity
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.local.toEntity
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.diagnostic.DiagnosticLoggerProvider
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationUiModel
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
    private val notifier by lazy {
        SmartNotiNotifier(applicationContext, appIconResolver = appIconResolver)
    }
    private val silentSummaryNotifier by lazy {
        SilentHiddenSummaryNotifier(applicationContext, appIconResolver = appIconResolver)
    }
    private var storeSyncJob: Job? = null
    private var silentSummaryJob: Job? = null

    /**
     * Plan
     * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
     * Tasks 2-3. Single shared resolver that owns the explicit fallback
     * chain + per-package memoization. Built lazily so tests that don't
     * touch the listener never instantiate it.
     */
    private val appLabelResolver by lazy {
        AppLabelResolver(AndroidPackageLabelSource(applicationContext.packageManager))
    }

    /**
     * Plan
     * `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
     * Tasks 2-4. Sibling of [appLabelResolver] for the source-app
     * launcher icon surface — single shared instance so both notifiers
     * (SmartNotiNotifier + SilentHiddenSummaryNotifier) and the
     * package-broadcast invalidation hook all see the same cache.
     */
    private val appIconResolver by lazy {
        AppIconResolver(AndroidAppIconSource(applicationContext.packageManager))
    }

    /**
     * Plan
     * `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
     * Task 3. Invalidates the per-package label cache when an app is
     * installed / replaced / removed so the next notification picks up the
     * new label without paying a stale-cache cost. Receiver is registered
     * in [onCreate] and unregistered in [onDestroy].
     */
    private val packageChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val packageName = intent?.data?.let(Uri::getSchemeSpecificPart)
            if (packageName.isNullOrBlank()) {
                appLabelResolver.clearAll()
                // Plan
                // `docs/plans/2026-04-27-fix-issue-510-replacement-icon-source-action-overlay.md`
                // Task 4: invalidate the icon cache on the same package
                // broadcasts so an app upgrade picks up its new launcher
                // icon on the next replacement notification.
                appIconResolver.clearAll()
            } else {
                appLabelResolver.invalidate(packageName)
                appIconResolver.invalidate(packageName)
            }
        }
    }
    private var packageChangedReceiverRegistered = false
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

    override fun onCreate() {
        super.onCreate()
        // Plan
        // `docs/plans/2026-04-27-fix-issue-503-app-label-resolver-fallback-chain.md`
        // Task 3: register a receiver for ACTION_PACKAGE_REPLACED /
        // _ADDED / _REMOVED so the in-process app-label cache is
        // invalidated when an app is installed, upgraded, or removed.
        // The data scheme MUST be `package` so the system populates
        // `intent.data` with the affected packageName.
        if (!packageChangedReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            applicationContext.registerReceiver(packageChangedReceiver, filter)
            packageChangedReceiverRegistered = true
        }
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
        val repository = NotificationRepository.getInstance(applicationContext)
        val rulesRepository = RulesRepository.getInstance(applicationContext)
        val categoriesRepository = CategoriesRepository.getInstance(applicationContext)
        val settingsRepository = SettingsRepository.getInstance(applicationContext)

        // Plan `2026-04-22-categories-runtime-wiring-fix.md` Task 4 (Drift #1):
        // read Rules + Categories + Settings at the same call site so the
        // Classifier can apply Category.action consistently. Plan
        // `2026-04-27-refactor-listener-process-notification-extract.md`
        // Task 3: the snapshot read stays inline so the duplicate-context
        // builder can be driven by the same `settings` instance the
        // decision pipeline later receives.
        val settings = settingsRepository.observeSettings().first()

        // Plan `2026-04-27-refactor-listener-process-notification-extract.md`
        // Task 3: the pre-classifier "input build" stage (extras parsing,
        // MessagingStyle sender, appName, persistent flags, duplicate
        // signature/count, CapturedNotificationInput.withContext) is now
        // owned by [NotificationPipelineInputBuilder]. The builder still
        // reads the same fields verbatim — only call-site shape changed.
        val inputBuilder = NotificationPipelineInputBuilder(
            persistentNotificationPolicy = persistentNotificationPolicy,
            duplicateContextBuilder = NotificationDuplicateContextBuilder(
                tracker = liveDuplicateCountTracker,
                persistedDuplicateCount = repository::countRecentDuplicates,
            ),
            appNameLookup = appLabelResolver::resolve,
            contextLookup = settingsRepository::currentNotificationContext,
        )
        val pipelineInput = inputBuilder.build(sbn, settings)

        val classifiedNotification = processor.process(
            input = pipelineInput.captureInput,
            rules = rulesRepository.currentRules(),
            settings = settings,
            categories = categoriesRepository.currentCategories(),
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
            DebugClassificationOverride.resolve(sbn.notification.extras, classifiedNotification)
        } else {
            classifiedNotification
        }

        val isProtectedSourceNotification = ProtectedSourceNotificationDetector.isProtected(
            ProtectedSourceNotificationDetector.signalsFrom(sbn),
        )

        // Plan `2026-04-27-refactor-listener-process-notification-extract.md`
        // Task 2: the post-classifier "decision → side-effect" branch lives
        // in [NotificationDecisionPipeline]. Statement order inside dispatch
        // is identical to the previous inline implementation; the pipeline
        // routes side effects through [SourceTrayActions] so
        // `NotificationDecisionPipelineCharacterizationTest` can assert
        // against a fake without the listener.
        val pipeline = NotificationDecisionPipeline(
            actions = ListenerSourceTrayActions(
                service = this,
                notifier = notifier,
                repository = repository,
                settingsRepository = settingsRepository,
            ),
            // Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
            // Task 4 — wire the route diagnostic hook. The provider returns a
            // singleton logger backed by `filesDir/diagnostic/diagnostic.log`;
            // the logger short-circuits when the user has not opted in via
            // Settings → 진단, so this is a strict no-op for default users.
            diagnosticLogger = DiagnosticLoggerProvider.getInstance(applicationContext),
        )
        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = sbn.key,
                packageName = sbn.packageName,
                appName = pipelineInput.appName,
                postedAtMillis = sbn.postTime,
                contentSignature = pipelineInput.contentSignature,
                settings = settings,
                isPersistent = pipelineInput.isPersistent,
                shouldBypassPersistentHiding = pipelineInput.shouldBypassPersistentHiding,
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
        if (packageChangedReceiverRegistered) {
            runCatching { applicationContext.unregisterReceiver(packageChangedReceiver) }
            packageChangedReceiverRegistered = false
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

        /**
         * Snapshot of the currently active StatusBarNotification keys held by the
         * connected listener service, or `null` when the listener is not bound.
         *
         * Used by
         * [com.smartnoti.app.data.local.ListenerActiveSourceNotificationInspector]
         * (plan
         * `docs/plans/2026-04-28-fix-issue-511-cancel-source-on-replacement.md`
         * Task 5) so the cold-start
         * [com.smartnoti.app.data.local.MigrateOrphanedSourceCancellationRunner]
         * can decide whether each persisted `sourceEntryKey` still has a live
         * tray entry to cancel. A returned `null` (listener not bound)
         * intentionally distinguishes from an empty set (listener bound but
         * tray currently empty) so the migration runner can defer-without-
         * flipping-the-flag in the former case.
         */
        fun activeSourceKeysSnapshotIfConnected(): Set<String>? {
            val service = activeService ?: return null
            val actives = runCatching { service.activeNotifications }.getOrNull() ?: return emptySet()
            return actives.mapTo(HashSet(actives.size)) { it.key }
        }

        /**
         * Richer sibling of [activeSourceKeysSnapshotIfConnected] that
         * preserves `(packageName, groupKey, flags)` for each entry — used
         * by [com.smartnoti.app.data.local.ListenerActiveTrayInspector]
         * (plan
         * `docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`
         * Task 2) so the user-triggered
         * [com.smartnoti.app.data.local.TrayOrphanCleanupRunner] can:
         *
         *   - derive the source packageName set from
         *     `smartnoti_silent_group_app:<pkg>` group keys, and
         *   - skip PERSISTENT_PROTECTED entries (FOREGROUND_SERVICE /
         *     NO_CLEAR / ONGOING_EVENT) without falsely cancelling music /
         *     call / nav / foreground-service notifications.
         *
         * `null` (listener not bound) is intentionally distinguished from
         * an empty list (listener bound but tray empty) so the runner can
         * surface the "알림 권한 활성 후 다시 시도해 주세요" hint instead
         * of a misleading "0건 정리됨" toast.
         */
        fun activeTrayEntriesSnapshotIfConnected(): List<ActiveTrayEntry>? {
            val service = activeService ?: return null
            val actives = runCatching { service.activeNotifications }.getOrNull() ?: return emptyList()
            return actives.map { sbn ->
                ActiveTrayEntry(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    groupKey = runCatching { sbn.groupKey }.getOrNull(),
                    flags = sbn.notification.flags,
                )
            }
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
