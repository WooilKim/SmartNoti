package com.smartnoti.app.domain.model

data class NotificationUiModel(
    val id: String,
    val appName: String,
    val packageName: String,
    val sender: String?,
    val title: String,
    val body: String,
    val receivedAtLabel: String,
    val status: NotificationStatusUi,
    val reasonTags: List<String>,
    val score: Int? = null,
    val isBundled: Boolean = false,
    val isPersistent: Boolean = false,
)

enum class NotificationStatusUi {
    PRIORITY,
    DIGEST,
    SILENT,
}
