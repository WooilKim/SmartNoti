package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationContext
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.VibrationMode

class DeliveryProfilePolicy {

    fun resolve(
        decision: NotificationDecision,
        settings: SmartNotiSettings,
        context: NotificationContext,
        isPersistent: Boolean,
    ): DeliveryProfile {
        val baseProfile = configuredProfileFor(decision, settings)
        val quietHoursAdjusted = if (decision == NotificationDecision.PRIORITY && context.isQuietHoursActive()) {
            baseProfile.copy(headsUpEnabled = false)
        } else {
            baseProfile
        }
        val duplicateAdjusted = if (context.duplicateCountInWindow >= 3) {
            quietHoursAdjusted.softened()
        } else {
            quietHoursAdjusted
        }

        return if (isPersistent) {
            duplicateAdjusted.persistentSafe()
        } else {
            duplicateAdjusted
        }
    }

    private fun configuredProfileFor(
        decision: NotificationDecision,
        settings: SmartNotiSettings,
    ): DeliveryProfile {
        val defaults = DeliveryProfile.defaultsFor(decision)
        val raw = when (decision) {
            NotificationDecision.PRIORITY -> DecisionSettings(
                alertLevel = settings.priorityAlertLevel,
                vibrationMode = settings.priorityVibrationMode,
                headsUpEnabled = settings.priorityHeadsUpEnabled,
                lockScreenVisibility = settings.priorityLockScreenVisibility,
            )
            NotificationDecision.DIGEST -> DecisionSettings(
                alertLevel = settings.digestAlertLevel,
                vibrationMode = settings.digestVibrationMode,
                headsUpEnabled = settings.digestHeadsUpEnabled,
                lockScreenVisibility = settings.digestLockScreenVisibility,
            )
            NotificationDecision.SILENT -> DecisionSettings(
                alertLevel = settings.silentAlertLevel,
                vibrationMode = settings.silentVibrationMode,
                headsUpEnabled = settings.silentHeadsUpEnabled,
                lockScreenVisibility = settings.silentLockScreenVisibility,
            )
            // IGNORE (plan 2026-04-21-ignore-tier-fourth-decision Task 2):
            // IGNORE never posts a replacement notification (Task 4
            // early-return), so this profile is never actually consumed. Reuse
            // silent settings so any stray lookup produces the quietest legal
            // profile instead of throwing.
            NotificationDecision.IGNORE -> DecisionSettings(
                alertLevel = settings.silentAlertLevel,
                vibrationMode = settings.silentVibrationMode,
                headsUpEnabled = false,
                lockScreenVisibility = settings.silentLockScreenVisibility,
            )
        }

        return defaults.copy(
            alertLevel = raw.alertLevel.toAlertLevelOr(defaults.alertLevel),
            vibrationMode = raw.vibrationMode.toVibrationModeOr(defaults.vibrationMode),
            headsUpEnabled = raw.headsUpEnabled,
            lockScreenVisibilityMode = raw.lockScreenVisibility.toLockScreenVisibilityOr(defaults.lockScreenVisibilityMode),
        )
    }

    private data class DecisionSettings(
        val alertLevel: String,
        val vibrationMode: String,
        val headsUpEnabled: Boolean,
        val lockScreenVisibility: String,
    )
}

private fun String.toAlertLevelOr(default: AlertLevel): AlertLevel = when (trim().uppercase()) {
    "LOUD", "HIGH" -> AlertLevel.LOUD
    "SOFT", "LOW" -> AlertLevel.SOFT
    "QUIET" -> AlertLevel.QUIET
    "NONE", "MINIMAL" -> AlertLevel.NONE
    else -> default
}

private fun String.toVibrationModeOr(default: VibrationMode): VibrationMode = when (trim().uppercase()) {
    "STRONG", "DEFAULT" -> VibrationMode.STRONG
    "LIGHT" -> VibrationMode.LIGHT
    "OFF" -> VibrationMode.OFF
    else -> default
}

private fun String.toLockScreenVisibilityOr(default: LockScreenVisibilityMode): LockScreenVisibilityMode = when (trim().uppercase()) {
    LockScreenVisibilityMode.PUBLIC.name -> LockScreenVisibilityMode.PUBLIC
    LockScreenVisibilityMode.PRIVATE.name -> LockScreenVisibilityMode.PRIVATE
    LockScreenVisibilityMode.SECRET.name -> LockScreenVisibilityMode.SECRET
    else -> default
}
