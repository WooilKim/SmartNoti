package com.smartnoti.app.notification

import com.smartnoti.app.domain.model.NotificationUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SmartNotiNotificationStore {
    private val _capturedNotifications = MutableStateFlow<List<NotificationUiModel>>(emptyList())
    val capturedNotifications: StateFlow<List<NotificationUiModel>> = _capturedNotifications.asStateFlow()

    fun prepend(notification: NotificationUiModel) {
        _capturedNotifications.value = listOf(notification) + _capturedNotifications.value
    }
}
