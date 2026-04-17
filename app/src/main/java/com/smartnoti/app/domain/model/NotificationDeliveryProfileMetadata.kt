package com.smartnoti.app.domain.model

fun NotificationStatusUi.toDecision(): NotificationDecision = when (this) {
    NotificationStatusUi.PRIORITY -> NotificationDecision.PRIORITY
    NotificationStatusUi.DIGEST -> NotificationDecision.DIGEST
    NotificationStatusUi.SILENT -> NotificationDecision.SILENT
}

fun NotificationDecision.toUiStatus(): NotificationStatusUi = when (this) {
    NotificationDecision.PRIORITY -> NotificationStatusUi.PRIORITY
    NotificationDecision.DIGEST -> NotificationStatusUi.DIGEST
    NotificationDecision.SILENT -> NotificationStatusUi.SILENT
}

fun NotificationUiModel.toDeliveryProfileOrDefault(): DeliveryProfile {
    val decision = status.toDecision()
    return DeliveryProfile.defaultsFor(decision).copy(
        alertLevel = alertLevel,
        vibrationMode = vibrationMode,
        headsUpEnabled = headsUpEnabled,
        lockScreenVisibilityMode = lockScreenVisibility,
        channelKey = deliveryChannelKey,
    ).sanitizedForDecision(decision)
}

fun DeliveryProfile.sanitizedForDecision(decision: NotificationDecision): DeliveryProfile {
    val defaults = DeliveryProfile.defaultsFor(decision)
    val normalizedChannelKey = if (channelKey == defaults.channelKey) channelKey else defaults.channelKey
    return when (decision) {
        NotificationDecision.PRIORITY -> copy(
            deliveryMode = defaults.deliveryMode,
            channelKey = normalizedChannelKey,
        )

        NotificationDecision.DIGEST -> copy(
            alertLevel = when (alertLevel) {
                AlertLevel.LOUD -> AlertLevel.SOFT
                else -> alertLevel
            },
            vibrationMode = when (vibrationMode) {
                VibrationMode.STRONG -> VibrationMode.LIGHT
                else -> vibrationMode
            },
            headsUpEnabled = false,
            lockScreenVisibilityMode = when (lockScreenVisibilityMode) {
                LockScreenVisibilityMode.PUBLIC -> LockScreenVisibilityMode.PRIVATE
                else -> lockScreenVisibilityMode
            },
            deliveryMode = defaults.deliveryMode,
            channelKey = normalizedChannelKey,
        )

        NotificationDecision.SILENT -> copy(
            alertLevel = when (alertLevel) {
                AlertLevel.NONE -> AlertLevel.NONE
                else -> AlertLevel.QUIET
            },
            vibrationMode = VibrationMode.OFF,
            headsUpEnabled = false,
            lockScreenVisibilityMode = when (lockScreenVisibilityMode) {
                LockScreenVisibilityMode.PUBLIC -> LockScreenVisibilityMode.PRIVATE
                else -> lockScreenVisibilityMode
            },
            deliveryMode = defaults.deliveryMode,
            channelKey = normalizedChannelKey,
        )
    }
}
