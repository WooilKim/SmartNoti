package com.smartnoti.app.notification

import android.service.notification.StatusBarNotification
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.model.withContext
import com.smartnoti.app.domain.usecase.PersistentNotificationPolicy

/**
 * Plan `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md`
 * Task 3.
 *
 * Builds the pre-classifier "pipeline input" stage of
 * `SmartNotiNotificationListenerService.processNotification`. Pulls extras
 * (title / body / sender via [MessagingStyleSenderResolver]), resolves
 * `appName` through an injectable lookup, derives `isPersistent` /
 * `shouldBypassPersistentHiding`, and assembles the [CapturedNotificationInput]
 * + per-notification [NotificationContext] that the
 * [NotificationProcessingCoordinator] consumes.
 *
 * Side effects are limited to the [appNameLookup] and [contextLookup]
 * functional ports so this builder can be exercised in pure-Kotlin tests.
 */
internal class NotificationPipelineInputBuilder(
    private val persistentNotificationPolicy: PersistentNotificationPolicy,
    private val duplicateContextBuilder: NotificationDuplicateContextBuilder,
    private val appNameLookup: (String) -> String,
    private val contextLookup: suspend (Int) -> NotificationContext,
) {
    data class PipelineInput(
        val captureInput: CapturedNotificationInput,
        val isPersistent: Boolean,
        val shouldBypassPersistentHiding: Boolean,
        val appName: String,
        val contentSignature: String,
        val title: String,
        val body: String,
    )

    suspend fun build(
        sbn: StatusBarNotification,
        settings: SmartNotiSettings,
    ): PipelineInput {
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

        val appName = appNameLookup(sbn.packageName)

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

        val duplicateContext = duplicateContextBuilder.build(
            packageName = sbn.packageName,
            notificationId = sbn.id,
            sourceEntryKey = sbn.key,
            postTimeMillis = sbn.postTime,
            title = title,
            body = body,
            settings = settings,
            isPersistent = isPersistent,
        )

        val effectiveDuplicateCount = if (isPersistent) 1 else duplicateContext.duplicateCount

        val captureInput = CapturedNotificationInput(
            packageName = sbn.packageName,
            appName = appName,
            sender = sender,
            title = title,
            body = body,
            postedAtMillis = sbn.postTime,
            quietHours = false,
            duplicateCountInWindow = effectiveDuplicateCount,
            isPersistent = isPersistent && !shouldBypassPersistentHiding,
            sourceEntryKey = sbn.key,
            // Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 4:
            // forward the user-tunable threshold to the classifier through
            // the processor. `settings` is the snapshot read above at the
            // same call site as rules / categories, so all three knobs are
            // mutually consistent for this notification.
            duplicateThreshold = settings.duplicateDigestThreshold,
        ).withContext(contextLookup(effectiveDuplicateCount))

        return PipelineInput(
            captureInput = captureInput,
            isPersistent = isPersistent,
            shouldBypassPersistentHiding = shouldBypassPersistentHiding,
            appName = appName,
            contentSignature = duplicateContext.contentSignature,
            title = title,
            body = body,
        )
    }
}
