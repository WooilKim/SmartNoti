package com.smartnoti.app.data.local

data class CapturedAppSelectionItem(
    val packageName: String,
    val appName: String,
    val notificationCount: Long,
    val lastSeenLabel: String,
)
