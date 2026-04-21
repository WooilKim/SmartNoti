package com.smartnoti.app.domain.model

fun NotificationStatusUi.toDecision(): NotificationDecision = when (this) {
    NotificationStatusUi.PRIORITY -> NotificationDecision.PRIORITY
    NotificationStatusUi.DIGEST -> NotificationDecision.DIGEST
    NotificationStatusUi.SILENT -> NotificationDecision.SILENT
    NotificationStatusUi.IGNORE -> NotificationDecision.IGNORE
}

fun NotificationDecision.toUiStatus(): NotificationStatusUi = when (this) {
    NotificationDecision.PRIORITY -> NotificationStatusUi.PRIORITY
    NotificationDecision.DIGEST -> NotificationStatusUi.DIGEST
    NotificationDecision.SILENT -> NotificationStatusUi.SILENT
    NotificationDecision.IGNORE -> NotificationStatusUi.IGNORE
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

        // IGNORE (plan 2026-04-21-ignore-tier-fourth-decision Task 2): sanitize
        // identically to SILENT — Task 4 will add an early-return in the
        // notifier so this branch is effectively unused for live posts, but
        // keeping a safe, silent-equivalent profile guarantees any stray
        // downstream caller cannot produce a loud alert from an IGNORE row.
        NotificationDecision.IGNORE -> copy(
            alertLevel = AlertLevel.NONE,
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
