package com.smartnoti.app.domain.model

fun buildNotificationId(
    packageName: String,
    postedAtMillis: Long,
    sourceEntryKey: String? = null,
): String {
    val suffix = sourceEntryKey.toNotificationIdSuffix()
    return if (suffix == null) {
        "$packageName:$postedAtMillis"
    } else {
        "$packageName:$postedAtMillis:$suffix"
    }
}

fun NotificationUiModel.postedAtMillisOrNull(): Long? = id.notificationPostedAtMillisOrNull()

fun String.notificationPostedAtMillisOrNull(): Long? {
    return split(':').getOrNull(1)
        ?.replace("_", "")
        ?.toLongOrNull()
}

private fun String?.toNotificationIdSuffix(): String? {
    val raw = this?.takeIf { it.isNotBlank() } ?: return null
    val pipeSegments = raw.split('|')
    val relevantSegments = when {
        pipeSegments.size >= 4 -> listOf(pipeSegments[2], pipeSegments[3])
        else -> listOf(raw)
    }
    return relevantSegments
        .map { segment -> segment.replace(':', '_').trim() }
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(":")
}
