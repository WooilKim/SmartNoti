package com.smartnoti.app.domain.model

enum class NotificationDecision {
    PRIORITY,
    DIGEST,
    SILENT,
}

data class ClassificationInput(
    val sender: String? = null,
    val packageName: String,
    val title: String = "",
    val body: String = "",
    val quietHours: Boolean = false,
    val duplicateCountInWindow: Int = 0,
    val hourOfDay: Int? = null,
)
