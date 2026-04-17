package com.smartnoti.app.notification

import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.DeliveryProfile
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.VibrationMode
import com.smartnoti.app.domain.model.sanitizedForDecision

internal object ReplacementNotificationChannelRegistry {

    private const val CHANNEL_PREFIX = "smartnoti_replacement"

    fun all(): List<ReplacementNotificationChannelSpec> {
        return NotificationDecision.entries
            .flatMap { decision -> supportedProfilesFor(decision).map { profile -> resolve(decision, profile) } }
            .distinctBy(ReplacementNotificationChannelSpec::id)
            .sortedBy(ReplacementNotificationChannelSpec::id)
    }

    fun resolve(
        decision: NotificationDecision,
        deliveryProfile: DeliveryProfile,
    ): ReplacementNotificationChannelSpec {
        val sanitizedProfile = deliveryProfile.sanitizedForDecision(decision)
        val behavior = behaviorFor(decision, sanitizedProfile)
        val visibility = sanitizedProfile.lockScreenVisibilityMode.toNotificationVisibility()
        val decisionLabel = decision.name.lowercase()
        val vibrationLabel = sanitizedProfile.vibrationMode.name.lowercase()
        val visibilityLabel = sanitizedProfile.lockScreenVisibilityMode.name.lowercase()
        val importanceLabel = behavior.importance.toImportanceLabel()
        val headsUpLabel = if (behavior.headsUpAllowed) "headsup" else "noheadsup"
        return ReplacementNotificationChannelSpec(
            id = listOf(
                CHANNEL_PREFIX,
                decisionLabel,
                importanceLabel,
                vibrationLabel,
                visibilityLabel,
                headsUpLabel,
            ).joinToString("_"),
            name = buildName(
                decision = decision,
                importanceLabel = importanceLabel,
                vibrationLabel = vibrationLabel,
                visibilityLabel = visibilityLabel,
                headsUpAllowed = behavior.headsUpAllowed,
            ),
            description = buildDescription(
                decision = decision,
                importanceLabel = importanceLabel,
                vibrationLabel = vibrationLabel,
                visibilityLabel = visibilityLabel,
                headsUpAllowed = behavior.headsUpAllowed,
            ),
            importance = behavior.importance,
            compatPriority = behavior.compatPriority,
            notificationVisibility = visibility,
            vibrationPattern = behavior.vibrationPattern,
            soundEnabled = behavior.soundEnabled,
            headsUpAllowed = behavior.headsUpAllowed,
        )
    }

    private fun supportedProfilesFor(decision: NotificationDecision): List<DeliveryProfile> {
        val defaults = DeliveryProfile.defaultsFor(decision)
        val alertLevels = when (decision) {
            NotificationDecision.PRIORITY -> listOf(AlertLevel.LOUD, AlertLevel.SOFT, AlertLevel.QUIET)
            NotificationDecision.DIGEST -> listOf(AlertLevel.SOFT, AlertLevel.QUIET, AlertLevel.NONE)
            NotificationDecision.SILENT -> listOf(AlertLevel.QUIET, AlertLevel.NONE)
        }
        val vibrationModes = when (decision) {
            NotificationDecision.PRIORITY -> listOf(VibrationMode.STRONG, VibrationMode.LIGHT, VibrationMode.OFF)
            NotificationDecision.DIGEST -> listOf(VibrationMode.LIGHT, VibrationMode.OFF)
            NotificationDecision.SILENT -> listOf(VibrationMode.OFF)
        }
        val headsUpOptions = when (decision) {
            NotificationDecision.PRIORITY -> listOf(true, false)
            NotificationDecision.DIGEST,
            NotificationDecision.SILENT,
            -> listOf(false)
        }
        val lockScreenModes = when (decision) {
            NotificationDecision.PRIORITY -> listOf(
                LockScreenVisibilityMode.PUBLIC,
                LockScreenVisibilityMode.PRIVATE,
                LockScreenVisibilityMode.SECRET,
            )
            NotificationDecision.DIGEST,
            NotificationDecision.SILENT,
            -> listOf(
                LockScreenVisibilityMode.PRIVATE,
                LockScreenVisibilityMode.SECRET,
            )
        }

        return buildList {
            for (alertLevel in alertLevels) {
                for (vibrationMode in vibrationModes) {
                    for (headsUpEnabled in headsUpOptions) {
                        for (lockScreenMode in lockScreenModes) {
                            add(
                                defaults.copy(
                                    alertLevel = alertLevel,
                                    vibrationMode = vibrationMode,
                                    headsUpEnabled = headsUpEnabled,
                                    lockScreenVisibilityMode = lockScreenMode,
                                ).sanitizedForDecision(decision)
                            )
                        }
                    }
                }
            }
        }.distinctBy { profile ->
            listOf(
                profile.alertLevel.name,
                profile.vibrationMode.name,
                profile.headsUpEnabled.toString(),
                profile.lockScreenVisibilityMode.name,
            ).joinToString(":")
        }
    }

    private fun behaviorFor(
        decision: NotificationDecision,
        deliveryProfile: DeliveryProfile,
    ): ChannelBehavior {
        return when (decision) {
            NotificationDecision.PRIORITY -> when {
                deliveryProfile.alertLevel == AlertLevel.LOUD && deliveryProfile.headsUpEnabled -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_HIGH,
                    compatPriority = NotificationCompat.PRIORITY_HIGH,
                    soundEnabled = true,
                    headsUpAllowed = true,
                    vibrationPattern = vibrationPatternFor(deliveryProfile.vibrationMode),
                )
                deliveryProfile.alertLevel == AlertLevel.LOUD || deliveryProfile.alertLevel == AlertLevel.SOFT -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_DEFAULT,
                    compatPriority = NotificationCompat.PRIORITY_DEFAULT,
                    soundEnabled = true,
                    headsUpAllowed = false,
                    vibrationPattern = vibrationPatternFor(deliveryProfile.vibrationMode),
                )
                else -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_LOW,
                    compatPriority = NotificationCompat.PRIORITY_LOW,
                    soundEnabled = false,
                    headsUpAllowed = false,
                    vibrationPattern = vibrationPatternFor(deliveryProfile.vibrationMode),
                )
            }

            NotificationDecision.DIGEST -> when (deliveryProfile.alertLevel) {
                AlertLevel.SOFT -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_DEFAULT,
                    compatPriority = NotificationCompat.PRIORITY_DEFAULT,
                    soundEnabled = true,
                    headsUpAllowed = false,
                    vibrationPattern = vibrationPatternFor(deliveryProfile.vibrationMode),
                )
                AlertLevel.QUIET -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_LOW,
                    compatPriority = NotificationCompat.PRIORITY_LOW,
                    soundEnabled = false,
                    headsUpAllowed = false,
                    vibrationPattern = vibrationPatternFor(deliveryProfile.vibrationMode),
                )
                AlertLevel.NONE -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_MIN,
                    compatPriority = NotificationCompat.PRIORITY_MIN,
                    soundEnabled = false,
                    headsUpAllowed = false,
                    vibrationPattern = vibrationPatternFor(deliveryProfile.vibrationMode),
                )
                AlertLevel.LOUD -> error("Digest delivery profile must be sanitized before channel resolution")
            }

            NotificationDecision.SILENT -> when (deliveryProfile.alertLevel) {
                AlertLevel.QUIET -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_LOW,
                    compatPriority = NotificationCompat.PRIORITY_LOW,
                    soundEnabled = false,
                    headsUpAllowed = false,
                    vibrationPattern = emptyList(),
                )
                AlertLevel.NONE -> ChannelBehavior(
                    importance = NotificationManager.IMPORTANCE_MIN,
                    compatPriority = NotificationCompat.PRIORITY_MIN,
                    soundEnabled = false,
                    headsUpAllowed = false,
                    vibrationPattern = emptyList(),
                )
                AlertLevel.LOUD,
                AlertLevel.SOFT,
                -> error("Silent delivery profile must be sanitized before channel resolution")
            }
        }
    }

    private fun vibrationPatternFor(vibrationMode: VibrationMode): List<Long> = when (vibrationMode) {
        VibrationMode.STRONG -> listOf(0L, 250L, 200L, 250L)
        VibrationMode.LIGHT -> listOf(0L, 120L)
        VibrationMode.OFF -> emptyList()
    }

    private fun buildName(
        decision: NotificationDecision,
        importanceLabel: String,
        vibrationLabel: String,
        visibilityLabel: String,
        headsUpAllowed: Boolean,
    ): String {
        val decisionLabel = decision.name.lowercase().replaceFirstChar(Char::uppercaseChar)
        return buildString {
            append("SmartNoti ")
            append(decisionLabel)
            append(' ')
            append(importanceLabel.replaceFirstChar(Char::uppercaseChar))
            append(' ')
            append(vibrationLabel.replaceFirstChar(Char::uppercaseChar))
            append(' ')
            append(visibilityLabel.replaceFirstChar(Char::uppercaseChar))
            if (decision == NotificationDecision.PRIORITY) {
                append(' ')
                append(if (headsUpAllowed) "HeadsUp" else "NoHeadsUp")
            }
        }
    }

    private fun buildDescription(
        decision: NotificationDecision,
        importanceLabel: String,
        vibrationLabel: String,
        visibilityLabel: String,
        headsUpAllowed: Boolean,
    ): String {
        return buildString {
            append(decision.name.lowercase().replaceFirstChar(Char::uppercaseChar))
            append(" replacement notifications surfaced by SmartNoti")
            append(" (importance=")
            append(importanceLabel)
            append(", vibration=")
            append(vibrationLabel)
            append(", lockscreen=")
            append(visibilityLabel)
            if (decision == NotificationDecision.PRIORITY) {
                append(", heads-up=")
                append(headsUpAllowed)
            }
            append(')')
        }
    }

    private fun LockScreenVisibilityMode.toNotificationVisibility(): Int = when (this) {
        LockScreenVisibilityMode.PUBLIC -> Notification.VISIBILITY_PUBLIC
        LockScreenVisibilityMode.PRIVATE -> Notification.VISIBILITY_PRIVATE
        LockScreenVisibilityMode.SECRET -> Notification.VISIBILITY_SECRET
    }

    private fun Int.toImportanceLabel(): String = when (this) {
        NotificationManager.IMPORTANCE_HIGH -> "high"
        NotificationManager.IMPORTANCE_DEFAULT -> "default"
        NotificationManager.IMPORTANCE_LOW -> "low"
        NotificationManager.IMPORTANCE_MIN -> "min"
        else -> "other"
    }
}

internal data class ReplacementNotificationChannelSpec(
    val id: String,
    val name: String,
    val description: String,
    val importance: Int,
    val compatPriority: Int,
    val notificationVisibility: Int,
    val vibrationPattern: List<Long>,
    val soundEnabled: Boolean,
    val headsUpAllowed: Boolean,
) {
    val vibrationEnabled: Boolean
        get() = vibrationPattern.isNotEmpty()

    val silentBuilder: Boolean
        get() = !soundEnabled && vibrationPattern.isEmpty()
}

private data class ChannelBehavior(
    val importance: Int,
    val compatPriority: Int,
    val soundEnabled: Boolean,
    val headsUpAllowed: Boolean,
    val vibrationPattern: List<Long>,
)
