package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationUiModel

class PersistentNotificationFilterBuilder {
    fun filter(
        notifications: List<NotificationUiModel>,
        hidePersistentNotifications: Boolean,
    ): List<NotificationUiModel> {
        return if (hidePersistentNotifications) {
            notifications.filterNot(NotificationUiModel::isPersistent)
        } else {
            notifications
        }
    }
}
