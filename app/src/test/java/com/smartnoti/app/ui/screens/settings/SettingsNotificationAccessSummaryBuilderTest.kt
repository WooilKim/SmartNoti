package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.onboarding.OnboardingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNotificationAccessSummaryBuilderTest {

    private val builder = SettingsNotificationAccessSummaryBuilder()

    @Test
    fun builds_connect_needed_summary_when_notification_listener_is_missing() {
        val summary = builder.build(
            OnboardingStatus(
                notificationListenerGranted = false,
                postNotificationsGranted = true,
                postNotificationsRequired = false,
            )
        )

        assertEquals(false, summary.granted)
        assertEquals("연결 필요", summary.statusLabel)
        assertEquals("아직 실제 알림을 읽지 못하고 있어요", summary.headline)
        assertEquals("알림 접근을 켜면 들어오는 알림이 Home·Priority·Digest 흐름에 바로 반영돼요.", summary.supporting)
        assertEquals("알림 접근 설정 열기", summary.actionLabel)
        assertTrue(summary.pathDescription.contains("알림 읽기"))
    }

    @Test
    fun builds_connected_summary_when_notification_listener_is_granted() {
        val summary = builder.build(
            OnboardingStatus(
                notificationListenerGranted = true,
                postNotificationsGranted = false,
                postNotificationsRequired = true,
            )
        )

        assertEquals(true, summary.granted)
        assertEquals("연결됨", summary.statusLabel)
        assertEquals("실제 알림이 Home에 반영되고 있어요", summary.headline)
        assertEquals("들어오는 알림이 최근 효과 요약과 Priority·Digest 흐름에 바로 연결돼요.", summary.supporting)
        assertEquals("알림 접근 설정 다시 열기", summary.actionLabel)
        assertEquals(
            "SmartNoti가 실제 캡처된 알림만 홈 화면에 쌓고, 추천 규칙 효과도 최근 데이터 기준으로 보여줘요.",
            summary.impactDescription,
        )
    }
}
