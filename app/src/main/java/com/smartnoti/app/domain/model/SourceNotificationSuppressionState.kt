package com.smartnoti.app.domain.model

enum class SourceNotificationSuppressionState {
    NOT_CONFIGURED,
    APP_NOT_SELECTED,
    PRIORITY_KEPT,
    PERSISTENT_PROTECTED,
    CANCEL_ATTEMPTED,
}
