package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistentNotificationFilterBuilderTest {

    private val builder = PersistentNotificationFilterBuilder()

    @Test
    fun excludes_persistent_notifications_when_hide_option_enabled() {
        val notifications = listOf(
            notification(id = "1", isPersistent = true),
            notification(id = "2", isPersistent = false),
        )

        val result = builder.filter(
            notifications = notifications,
            hidePersistentNotifications = true,
        )

        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun keeps_all_notifications_when_hide_option_disabled() {
        val notifications = listOf(
            notification(id = "1", isPersistent = true),
            notification(id = "2", isPersistent = false),
        )

        val result = builder.filter(
            notifications = notifications,
            hidePersistentNotifications = false,
        )

        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    private fun notification(id: String, isPersistent: Boolean) = NotificationUiModel(
        id = id,
        appName = "앱",
        packageName = "pkg.$id",
        sender = null,
        title = "제목",
        body = "본문",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.DIGEST,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
        isPersistent = isPersistent,
    )
}
