package com.smartnoti.app.notification

object NotificationCapturePolicy {
    fun shouldIgnoreCapture(
        title: String,
        body: String,
        notificationFlags: Int,
    ): Boolean {
        val isGroupSummary = (notificationFlags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        return isGroupSummary && title.isBlank() && body.isBlank()
    }
}
