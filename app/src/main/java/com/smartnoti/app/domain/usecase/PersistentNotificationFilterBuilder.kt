package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.domain.model.DigestGroupUiModel
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

    fun filterDigestGroups(
        groups: List<DigestGroupUiModel>,
        hidePersistentNotifications: Boolean,
    ): List<DigestGroupUiModel> {
        if (!hidePersistentNotifications) return groups

        return groups.mapNotNull { group ->
            val visibleItems = filter(
                notifications = group.items,
                hidePersistentNotifications = true,
            )
            if (visibleItems.isEmpty()) {
                null
            } else {
                group.copy(
                    count = visibleItems.size,
                    summary = "${group.appName} 관련 알림 ${visibleItems.size}건",
                    items = visibleItems,
                )
            }
        }
    }

    fun filterCapturedApps(
        capturedApps: List<CapturedAppSelectionItem>,
        notifications: List<NotificationUiModel>,
        hidePersistentNotifications: Boolean,
    ): List<CapturedAppSelectionItem> {
        if (!hidePersistentNotifications) return capturedApps

        val visibleCountByPackage = filter(
            notifications = notifications,
            hidePersistentNotifications = true,
        ).groupingBy(NotificationUiModel::packageName)
            .eachCount()

        return capturedApps.mapNotNull { app ->
            val visibleCount = visibleCountByPackage[app.packageName] ?: 0
            if (visibleCount == 0) {
                null
            } else {
                app.copy(notificationCount = visibleCount.toLong())
            }
        }
    }
}
