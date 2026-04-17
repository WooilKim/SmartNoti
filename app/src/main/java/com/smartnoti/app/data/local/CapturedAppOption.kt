package com.smartnoti.app.data.local

data class CapturedAppOption(
    val packageName: String,
    val appName: String,
    val lastPostedAtMillis: Long,
    val notificationCount: Long,
)
