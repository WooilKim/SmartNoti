package com.smartnoti.app.domain.model

data class DigestGroupUiModel(
    val id: String,
    val appName: String,
    val count: Int,
    val summary: String,
    val items: List<NotificationUiModel>,
)
