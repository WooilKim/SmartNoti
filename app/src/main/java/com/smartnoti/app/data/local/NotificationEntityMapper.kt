package com.smartnoti.app.data.local

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.VibrationMode

fun NotificationUiModel.toEntity(
    postedAtMillis: Long,
    contentSignature: String = listOf(title, body).joinToString(" ").trim(),
): NotificationEntity = NotificationEntity(
    id = id,
    appName = appName,
    packageName = packageName,
    sender = sender,
    title = title,
    body = body,
    postedAtMillis = postedAtMillis,
    status = status.name,
    reasonTags = reasonTags.joinToString("|"),
    score = score,
    isBundled = isBundled,
    isPersistent = isPersistent,
    contentSignature = contentSignature,
    deliveryChannelKey = deliveryChannelKey,
    alertLevel = alertLevel.name,
    vibrationMode = vibrationMode.name,
    headsUpEnabled = headsUpEnabled,
    lockScreenVisibility = lockScreenVisibility.name,
    sourceSuppressionState = sourceSuppressionState.name,
    replacementNotificationIssued = replacementNotificationIssued,
    silentMode = silentMode?.name,
    sourceEntryKey = sourceEntryKey,
    ruleHitIds = matchedRuleIds.takeIf { it.isNotEmpty() }?.joinToString(","),
)

fun NotificationEntity.toUiModel(): NotificationUiModel = NotificationUiModel(
    id = id,
    appName = appName,
    packageName = packageName,
    sender = sender,
    title = title,
    body = body,
    receivedAtLabel = "방금",
    status = NotificationStatusUi.valueOf(status),
    reasonTags = reasonTags.takeIf { it.isNotBlank() }?.split("|") ?: emptyList(),
    score = score,
    isBundled = isBundled,
    isPersistent = isPersistent,
    deliveryChannelKey = deliveryChannelKey,
    alertLevel = alertLevel.toAlertLevel(),
    vibrationMode = vibrationMode.toVibrationMode(),
    headsUpEnabled = headsUpEnabled,
    lockScreenVisibility = lockScreenVisibility.toLockScreenVisibility(),
    sourceSuppressionState = sourceSuppressionState.toSourceNotificationSuppressionState(),
    replacementNotificationIssued = replacementNotificationIssued,
    postedAtMillis = postedAtMillis,
    silentMode = silentMode?.toSilentMode(),
    sourceEntryKey = sourceEntryKey,
    matchedRuleIds = ruleHitIds
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: emptyList(),
)

private fun String.toAlertLevel(): AlertLevel = when (trim().uppercase()) {
    "LOUD", "HIGH" -> AlertLevel.LOUD
    "SOFT", "LOW" -> AlertLevel.SOFT
    "QUIET" -> AlertLevel.QUIET
    "NONE", "MINIMAL" -> AlertLevel.NONE
    else -> AlertLevel.NONE
}

private fun String.toVibrationMode(): VibrationMode = when (trim().uppercase()) {
    "STRONG", "DEFAULT" -> VibrationMode.STRONG
    "LIGHT" -> VibrationMode.LIGHT
    "OFF" -> VibrationMode.OFF
    else -> VibrationMode.OFF
}

private fun String.toLockScreenVisibility(): LockScreenVisibilityMode {
    return runCatching { enumValueOf<LockScreenVisibilityMode>(trim().uppercase()) }
        .getOrDefault(LockScreenVisibilityMode.SECRET)
}

private fun String.toSourceNotificationSuppressionState(): SourceNotificationSuppressionState {
    return runCatching { enumValueOf<SourceNotificationSuppressionState>(trim().uppercase()) }
        .getOrDefault(SourceNotificationSuppressionState.NOT_CONFIGURED)
}

private fun String.toSilentMode(): SilentMode? {
    val raw = trim()
    if (raw.isEmpty()) return null
    return runCatching { enumValueOf<SilentMode>(raw.uppercase()) }.getOrNull()
}
