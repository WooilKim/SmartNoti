package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationUiModel

/**
 * Side-effect port that [NotificationDecisionPipeline] uses to perform tray
 * cancels, post replacement alerts, mutate the suppressed-source apps SSOT,
 * and persist the captured row. Extracting these into an interface keeps the
 * pipeline pure-Kotlin so its outcomes can be characterized with a fake in
 * unit tests (`NotificationDecisionPipelineCharacterizationTest`).
 *
 * Plan: `docs/plans/2026-04-27-refactor-listener-process-notification-extract.md`
 * Task 2. The listener service supplies the production implementation;
 * `cancelSource` and `save` jump dispatchers internally so the pipeline can
 * stay agnostic about which thread it runs on.
 */
internal interface SourceTrayActions {
    /**
     * Cancel the source-tray entry identified by [key]. Implementations must
     * dispatch the underlying `NotificationListenerService.cancelNotification`
     * call on `Dispatchers.Main` since it is part of the listener API.
     */
    suspend fun cancelSource(key: String)

    /**
     * Post a replacement alert through `SmartNotiNotifier.notifySuppressedNotification`.
     * Production wiring forwards the same arguments verbatim.
     */
    fun postReplacement(
        decision: NotificationDecision,
        packageName: String,
        appName: String,
        title: String,
        body: String,
        notificationId: String,
        reasonTags: List<String>,
        settings: SmartNotiSettings,
        deliveryProfile: DeliveryProfile,
    )

    /**
     * Persist the auto-expanded suppressed-source apps set into
     * `SettingsRepository.setSuppressedSourceApps(...)`.
     */
    suspend fun setSuppressedApps(apps: Set<String>)

    /**
     * Persist the final captured row (`NotificationRepository.save`).
     */
    suspend fun save(
        notification: NotificationUiModel,
        postedAtMillis: Long,
        contentSignature: String,
    )
}
