package com.smartnoti.app.domain.usecase

import com.smartnoti.app.onboarding.OnboardingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNotificationAccessSummaryBuilderTest {

    private val builder = HomeNotificationAccessSummaryBuilder()

    @Test
    fun builds_connect_needed_summary_when_notification_access_is_missing() {
        val summary = builder.build(
            status = OnboardingStatus(
                notificationListenerGranted = false,
                postNotificationsGranted = true,
                postNotificationsRequired = false,
            ),
            recentCount = 0,
            priorityCount = 0,
            digestCount = 0,
            silentCount = 0,
        )

        assertEquals(false, summary.granted)
        assertEquals("연결 필요", summary.statusLabel)
        assertEquals("실제 알림 연결이 필요해요", summary.title)
        assertTrue(summary.body.contains("실제 알림을 읽지 못하고 있어요"))
        assertEquals("설정에서 연결하기", summary.actionLabel)
    }

    @Test
    fun builds_connected_summary_with_live_counts_when_recent_notifications_exist() {
        val summary = builder.build(
            status = OnboardingStatus(
                notificationListenerGranted = true,
                postNotificationsGranted = false,
                postNotificationsRequired = true,
            ),
            recentCount = 7,
            priorityCount = 2,
            digestCount = 4,
            silentCount = 1,
        )

        assertEquals(true, summary.granted)
        assertEquals("연결됨", summary.statusLabel)
        assertEquals("실제 알림이 연결되어 있어요", summary.title)
        assertTrue(summary.body.contains("최근 실제 알림 7개"))
        assertTrue(summary.body.contains("즉시 2개"))
        assertTrue(summary.body.contains("Digest 4개"))
        assertTrue(summary.body.contains("조용히 1개"))
        assertEquals("설정에서 연결 상태 보기", summary.actionLabel)
    }

    @Test
    fun builds_connected_summary_without_counts_when_no_recent_notifications_exist() {
        val summary = builder.build(
            status = OnboardingStatus(
                notificationListenerGranted = true,
                postNotificationsGranted = true,
                postNotificationsRequired = false,
            ),
            recentCount = 0,
            priorityCount = 0,
            digestCount = 0,
            silentCount = 0,
        )

        assertEquals(true, summary.granted)
        assertTrue(summary.body.contains("연결은 완료됐어요"))
        assertTrue(summary.body.contains("Home·Priority·Digest"))
    }
}
