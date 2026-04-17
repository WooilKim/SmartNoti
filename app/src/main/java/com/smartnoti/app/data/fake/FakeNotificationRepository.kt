package com.smartnoti.app.data.fake

import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.ui.preview.PreviewData

class FakeNotificationRepository {
    fun getRecentNotifications(): List<NotificationUiModel> = listOf(
        PreviewData.priorityNotification,
        PreviewData.digestNotification,
        PreviewData.digestNotification.copy(
            id = "5",
            appName = "연합뉴스",
            packageName = "com.news.app",
            title = "뉴스 알림 4건",
            body = "유사 뉴스 알림이 묶였어요",
            status = NotificationStatusUi.DIGEST,
            reasonTags = listOf("반복 알림"),
        ),
    )

    fun getPriorityNotifications(): List<NotificationUiModel> = listOf(
        PreviewData.priorityNotification,
        PreviewData.priorityNotification.copy(
            id = "6",
            appName = "배달의민족",
            packageName = "com.baemin.app",
            sender = null,
            title = "배달 도착",
            body = "문 앞에 두고 갔어요",
            reasonTags = listOf("실시간 필요"),
        ),
    )

    fun getDigestGroups(): List<DigestGroupUiModel> = listOf(PreviewData.digestGroup)

    fun getNotificationById(id: String): NotificationUiModel {
        return (getRecentNotifications() + getPriorityNotifications() + PreviewData.digestGroup.items)
            .distinctBy { it.id }
            .firstOrNull { it.id == id }
            ?: PreviewData.digestNotification
    }
}
