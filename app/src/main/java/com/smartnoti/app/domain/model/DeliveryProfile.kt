package com.smartnoti.app.domain.model

enum class AlertLevel {
    LOUD,
    SOFT,
    QUIET,
    NONE,
}

enum class VibrationMode {
    STRONG,
    LIGHT,
    OFF,
}

enum class LockScreenVisibilityMode {
    PUBLIC,
    PRIVATE,
    SECRET,
}

enum class DeliveryMode {
    IMMEDIATE,
    BATCHED,
    SUMMARY_ONLY,
    LOG_ONLY,
}

data class DeliveryProfile(
    val alertLevel: AlertLevel,
    val vibrationMode: VibrationMode,
    val headsUpEnabled: Boolean,
    val lockScreenVisibilityMode: LockScreenVisibilityMode,
    val deliveryMode: DeliveryMode,
    val channelKey: String,
) {
    fun softened(): DeliveryProfile = copy(
        alertLevel = alertLevel.softened(),
        vibrationMode = vibrationMode.softened(),
        headsUpEnabled = false,
    )

    fun persistentSafe(): DeliveryProfile = copy(
        alertLevel = AlertLevel.NONE,
        vibrationMode = VibrationMode.OFF,
        headsUpEnabled = false,
        lockScreenVisibilityMode = lockScreenVisibilityMode.safeForPersistent(),
        deliveryMode = DeliveryMode.LOG_ONLY,
        channelKey = CHANNEL_SILENT,
    )

    companion object {
        const val CHANNEL_PRIORITY = "smartnoti_priority"
        const val CHANNEL_DIGEST = "smartnoti_digest"
        const val CHANNEL_SILENT = "smartnoti_silent"

        fun defaultsFor(decision: NotificationDecision): DeliveryProfile = when (decision) {
            NotificationDecision.PRIORITY -> priorityDefaults()
            NotificationDecision.DIGEST -> digestDefaults()
            NotificationDecision.SILENT -> silentDefaults()
        }

        fun priorityDefaults(): DeliveryProfile = DeliveryProfile(
            alertLevel = AlertLevel.LOUD,
            vibrationMode = VibrationMode.STRONG,
            headsUpEnabled = true,
            lockScreenVisibilityMode = LockScreenVisibilityMode.PRIVATE,
            deliveryMode = DeliveryMode.IMMEDIATE,
            channelKey = CHANNEL_PRIORITY,
        )

        fun digestDefaults(): DeliveryProfile = DeliveryProfile(
            alertLevel = AlertLevel.SOFT,
            vibrationMode = VibrationMode.LIGHT,
            headsUpEnabled = false,
            lockScreenVisibilityMode = LockScreenVisibilityMode.PRIVATE,
            deliveryMode = DeliveryMode.BATCHED,
            channelKey = CHANNEL_DIGEST,
        )

        fun silentDefaults(): DeliveryProfile = DeliveryProfile(
            alertLevel = AlertLevel.NONE,
            vibrationMode = VibrationMode.OFF,
            headsUpEnabled = false,
            lockScreenVisibilityMode = LockScreenVisibilityMode.SECRET,
            deliveryMode = DeliveryMode.SUMMARY_ONLY,
            channelKey = CHANNEL_SILENT,
        )
    }
}

private fun AlertLevel.softened(): AlertLevel = when (this) {
    AlertLevel.LOUD -> AlertLevel.SOFT
    AlertLevel.SOFT -> AlertLevel.QUIET
    AlertLevel.QUIET -> AlertLevel.QUIET
    AlertLevel.NONE -> AlertLevel.NONE
}

private fun VibrationMode.softened(): VibrationMode = when (this) {
    VibrationMode.STRONG -> VibrationMode.LIGHT
    VibrationMode.LIGHT -> VibrationMode.OFF
    VibrationMode.OFF -> VibrationMode.OFF
}

private fun LockScreenVisibilityMode.safeForPersistent(): LockScreenVisibilityMode = when (this) {
    LockScreenVisibilityMode.PUBLIC -> LockScreenVisibilityMode.PRIVATE
    LockScreenVisibilityMode.PRIVATE -> LockScreenVisibilityMode.PRIVATE
    LockScreenVisibilityMode.SECRET -> LockScreenVisibilityMode.SECRET
}
