package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.NotificationCaptureProcessor

/**
 * Plan `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 4
 * (Drift #1 — listener injection).
 *
 * Pure seam that `SmartNotiNotificationListenerService.processNotification`
 * delegates to. Reads Rules + Categories + Settings at the same call site
 * and hands all three to [NotificationCaptureProcessor.process] so the
 * Classifier can actually resolve each notification's Category.action.
 *
 * Extracting this keeps the Android-only Listener service out of the unit
 * test surface — the coordinator is plain Kotlin and can be injected with
 * functional ports in [SmartNotiNotificationListenerServiceCategoryInjectionTest].
 */
class NotificationProcessingCoordinator(
    private val loadRules: suspend () -> List<RuleUiModel>,
    private val loadCategories: suspend () -> List<Category>,
    private val loadSettings: suspend () -> SmartNotiSettings,
    private val processor: NotificationCaptureProcessor,
) {
    suspend fun process(input: CapturedNotificationInput): NotificationUiModel {
        val rules = loadRules()
        val categories = loadCategories()
        val settings = loadSettings()
        return processor.process(
            input = input,
            rules = rules,
            settings = settings,
            categories = categories,
        )
    }
}
