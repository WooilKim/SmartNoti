package com.smartnoti.app.domain.model

data class CapturedNotificationInput(
    val packageName: String,
    val appName: String,
    val sender: String?,
    val title: String,
    val body: String,
    val postedAtMillis: Long,
    val quietHours: Boolean,
    val duplicateCountInWindow: Int,
)

fun CapturedNotificationInput.withContext(context: NotificationContext): CapturedNotificationInput {
    return copy(
        quietHours = context.isQuietHoursActive(),
        duplicateCountInWindow = context.duplicateCountInWindow,
    )
}
