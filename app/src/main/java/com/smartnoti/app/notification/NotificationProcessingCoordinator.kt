package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.NotificationCaptureProcessor

/**
 * RED-phase skeleton for plan
 * `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Task 4.
 *
 * Pure seam that `SmartNotiNotificationListenerService.processNotification`
 * will delegate to once Task 4 lands. Keeps the Listener service (Android
 * service, hard to unit-test) out of the unit-test surface by factoring
 * out the read-repos-then-process sequence.
 *
 * Task 1's listener-injection test asserts that both [loadRules] and
 * [loadCategories] are invoked at the same call site so the processor
 * always sees the current Category graph. Task 4 replaces the [process]
 * body with the real sequence.
 */
@Suppress("UNUSED_PARAMETER")
class NotificationProcessingCoordinator(
    private val loadRules: suspend () -> List<RuleUiModel>,
    private val loadCategories: suspend () -> List<Category>,
    private val loadSettings: suspend () -> SmartNotiSettings,
    private val processor: NotificationCaptureProcessor,
) {
    suspend fun process(input: CapturedNotificationInput): NotificationUiModel {
        TODO("Plan task 4: read rules + categories + settings, then delegate to processor.process")
    }
}
