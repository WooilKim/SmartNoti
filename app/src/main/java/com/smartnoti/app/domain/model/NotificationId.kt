package com.smartnoti.app.domain.model

/**
 * Returns a stable primary-key string for Room's `notifications` table.
 *
 * When the caller passes a [sourceEntryKey] (the `StatusBarNotification.key`), we drop
 * [postedAtMillis] from the id so the same notification slot (same package + tag + id)
 * resolves to the same row across updates. This lets the DAO's REPLACE strategy actually
 * overwrite rather than insert a new row whenever a media/chat notification gets updated.
 *
 * When the key is absent (legacy callers) we fall back to the old `packageName:timestamp`
 * form so existing ids keep parsing.
 */
fun buildNotificationId(
    packageName: String,
    postedAtMillis: Long,
    sourceEntryKey: String? = null,
): String {
    val suffix = sourceEntryKey.toNotificationIdSuffix()
    return if (suffix == null) {
        "$packageName:$postedAtMillis"
    } else {
        "$packageName:$suffix"
    }
}

fun NotificationUiModel.postedAtMillisOrNull(): Long? {
    return postedAtMillis.takeIf { it > 0L } ?: id.notificationPostedAtMillisOrNull()
}

fun String.notificationPostedAtMillisOrNull(): Long? {
    val segments = split(':')
    val candidate = segments.getOrNull(1) ?: return null
    return candidate.replace("_", "").toLongOrNull()
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
