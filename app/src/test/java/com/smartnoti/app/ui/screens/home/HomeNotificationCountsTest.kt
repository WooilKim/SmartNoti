package com.smartnoti.app.ui.screens.home

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNotificationCountsTest {

    @Test
    fun counts_each_status_in_one_pass() {
        val counts = HomeNotificationCounts.from(
            listOf(
                notification(id = "priority-1", status = NotificationStatusUi.PRIORITY),
                notification(id = "digest-1", status = NotificationStatusUi.DIGEST),
                notification(id = "silent-1", status = NotificationStatusUi.SILENT),
                notification(id = "priority-2", status = NotificationStatusUi.PRIORITY),
            )
        )

        assertEquals(2, counts.priority)
        assertEquals(1, counts.digest)
        assertEquals(1, counts.silent)
    }

    @Test
    fun returns_zero_counts_for_empty_input() {
        val counts = HomeNotificationCounts.from(emptyList())

        assertEquals(HomeNotificationCounts(priority = 0, digest = 0, silent = 0), counts)
    }

    private fun notification(
        id: String,
        status: NotificationStatusUi,
    ) = NotificationUiModel(
        id = id,
        appName = "앱",
        packageName = "pkg.$id",
        sender = null,
        title = "제목",
        body = "본문",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = emptyList(),
        score = null,
        isBundled = false,
    )
}
