package com.smartnoti.app.notification

object NotificationCapturePolicy {
    fun shouldIgnoreCapture(
        title: String,
        body: String,
        isGroupSummary: Boolean,
    ): Boolean {
        return isGroupSummary && title.isBlank() && body.isBlank()
    }
}
