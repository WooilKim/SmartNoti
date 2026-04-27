package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.toDecision
import com.smartnoti.app.domain.model.toDeliveryProfileOrDefault

/**
 * Plan `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md`
 * Task 2.
 *
 * Pure helper that owns the post-classifier "decision → side-effect" stage
 * of `SmartNotiNotificationListenerService.processNotification`. Statement
 * order matches the original inline implementation verbatim so the
 * `NotificationDecisionPipelineCharacterizationTest` stays GREEN across the
 * extraction:
 *
 *  1. `IGNORE` early-return — cancel + save with `CANCEL_ATTEMPTED`.
 *  2. Otherwise: protected detection → auto-expansion (write back via
 *     [SourceTrayActions.setSuppressedApps]) → suppression policy → silent
 *     capture mode → routing decision → optional cancel → optional
 *     replacement alert → suppression-state resolve → save.
 *
 * Side effects flow exclusively through [SourceTrayActions] so unit tests
 * (`NotificationDecisionPipelineCharacterizationTest`) can assert against a
 * fake implementation without instantiating the Android listener.
 */
internal class NotificationDecisionPipeline(
    private val actions: SourceTrayActions,
) {
    /**
     * Bundle of inputs that mirrors the locals in flight inside
     * `processNotification` after classifier output is in hand. Ordering and
     * field names match the test-side `DispatchInput` so future plan tasks
     * can rename / move call sites without churning either side.
     */
    internal data class DispatchInput(
        val baseNotification: NotificationUiModel,
        val sourceEntryKey: String,
        val packageName: String,
        val appName: String,
        val postedAtMillis: Long,
        val contentSignature: String,
        val settings: SmartNotiSettings,
        val isPersistent: Boolean,
        val shouldBypassPersistentHiding: Boolean,
        val isProtectedSourceNotification: Boolean,
    )

    suspend fun dispatch(input: DispatchInput) {
        val baseNotification = input.baseNotification
        val decision = baseNotification.status.toDecision()
        val deliveryProfile: DeliveryProfile = baseNotification.toDeliveryProfileOrDefault()
        val isPersistent = input.isPersistent
        val shouldBypassPersistentHiding = input.shouldBypassPersistentHiding
        val settings = input.settings

        // IGNORE early-return — plan `2026-04-21-ignore-tier-fourth-decision`
        // Task 4. The user has declared (via a Category.action = IGNORE) that
        // this notification is trash: cancel from the source tray, never post
        // a replacement alert, but still persist the DB row so audit /
        // recovery / weekly-insights can see it existed. We short-circuit
        // BEFORE the DIGEST/SILENT auto-expand, silent-mode, and
        // suppression-state machinery because none of those user-visible
        // surfaces apply to an IGNORE row. Default-view filtering keeps it
        // out of Home / Hidden / Digest / Priority. The tray cancel still
        // runs unconditionally — the gating here matches
        // [NotificationSuppressionPolicy.shouldSuppressSourceNotification]
        // returning true for IGNORE regardless of the opt-in flags.
        if (decision == NotificationDecision.IGNORE) {
            actions.cancelSource(input.sourceEntryKey)
            val ignoredNotification = baseNotification.copy(
                sourceSuppressionState = SourceNotificationSuppressionState.CANCEL_ATTEMPTED,
                replacementNotificationIssued = false,
                isPersistent = isPersistent,
            )
            actions.save(ignoredNotification, input.postedAtMillis, input.contentSignature)
            return
        }

        val shouldHidePersistentSourceNotification =
            (isPersistent && !shouldBypassPersistentHiding) && settings.hidePersistentSourceNotifications
        val isProtectedSourceNotification = input.isProtectedSourceNotification
        val autoExpandedApps = if (!isProtectedSourceNotification) {
            SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
                decision = decision,
                suppressSourceForDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                packageName = input.packageName,
                currentApps = settings.suppressedSourceApps,
                // Plan `2026-04-26-digest-suppression-sticky-exclude-list.md`
                // Task 5: wire the persisted sticky-exclude set into the
                // policy. Apps the user explicitly unchecked in Settings stay
                // excluded across DIGEST notifications instead of being
                // re-added by auto-expansion.
                excludedApps = settings.suppressedSourceAppsExcluded,
            )
        } else {
            null
        }
        if (autoExpandedApps != null) {
            actions.setSuppressedApps(autoExpandedApps)
        }
        val effectiveSuppressedApps = autoExpandedApps ?: settings.suppressedSourceApps
        val shouldSuppressSourceNotification = !isProtectedSourceNotification &&
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
                suppressedApps = effectiveSuppressedApps,
                packageName = input.packageName,
                decision = decision,
            )
        val capturedSilentMode = SilentCaptureRoutingSelector.silentModeFor(
            decision = decision,
            isPersistent = isPersistent,
            shouldBypassPersistentHiding = shouldBypassPersistentHiding,
            isProtectedSourceNotification = isProtectedSourceNotification,
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
                silentMode = capturedSilentMode,
            )
        }
        val suppressionState = SourceNotificationSuppressionStateResolver.resolve(
            decision = decision,
            suppressDigestAndSilent = settings.suppressSourceForDigestAndSilent,
            suppressedApps = settings.suppressedSourceApps,
            packageName = input.packageName,
            hidePersistentSourceNotifications = settings.hidePersistentSourceNotifications,
            isPersistent = isPersistent,
            bypassPersistentHiding = shouldBypassPersistentHiding,
            sourceRouting = sourceRouting,
        )
        var replacementNotificationPosted = false
        if (sourceRouting.cancelSourceNotification) {
            actions.cancelSource(input.sourceEntryKey)
        }
        if (sourceRouting.notifyReplacementNotification) {
            actions.postReplacement(
                decision = decision,
                packageName = input.packageName,
                appName = input.appName,
                title = baseNotification.title,
                body = baseNotification.body,
                notificationId = baseNotification.id,
                reasonTags = baseNotification.reasonTags,
                settings = settings,
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
            // Persist the ARCHIVED split so fresh SILENT captures land in the Hidden
            // inbox "보관 중" tab. Classifier / processor chain already leaves
            // `silentMode = null` for legacy SILENT rows; when the capture selector
            // picks ARCHIVED we override here. We only override on ARCHIVED to avoid
            // trampling modes a future task might set earlier in the pipeline.
            silentMode = capturedSilentMode ?: baseNotification.silentMode,
        )
        actions.save(notification, input.postedAtMillis, input.contentSignature)
    }
}
