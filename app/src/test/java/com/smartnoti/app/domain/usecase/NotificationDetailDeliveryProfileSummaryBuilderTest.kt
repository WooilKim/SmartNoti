package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.VibrationMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDetailDeliveryProfileSummaryBuilderTest {

    private val builder = NotificationDetailDeliveryProfileSummaryBuilder()

    @Test
    fun builds_priority_summary_with_heads_up_and_private_lockscreen_copy() {
        val summary = builder.build(
            notification(
                status = NotificationStatusUi.PRIORITY,
                alertLevel = AlertLevel.LOUD,
                vibrationMode = VibrationMode.STRONG,
                headsUpEnabled = true,
                lockScreenVisibility = LockScreenVisibilityMode.PRIVATE,
            )
        )

        assertEquals("즉시 전달", summary.deliveryModeLabel)
        assertEquals("강하게 알림", summary.alertLevelLabel)
        assertEquals("강한 진동", summary.vibrationLabel)
        assertEquals("켜짐", summary.headsUpLabel)
        assertEquals("내용 일부만 표시", summary.lockScreenVisibilityLabel)
        assertEquals(
            "중요 알림으로 바로 전달되고, 화면 상단 heads-up으로도 보여줘요.",
            summary.overview,
        )
    }

    @Test
    fun builds_digest_summary_with_safe_sanitized_copy() {
        val summary = builder.build(
            notification(
                status = NotificationStatusUi.DIGEST,
                alertLevel = AlertLevel.QUIET,
                vibrationMode = VibrationMode.OFF,
                headsUpEnabled = true,
                lockScreenVisibility = LockScreenVisibilityMode.PUBLIC,
            )
        )

        assertEquals("Digest 묶음 전달", summary.deliveryModeLabel)
        assertEquals("조용히 표시", summary.alertLevelLabel)
        assertEquals("진동 없음", summary.vibrationLabel)
        assertEquals("꺼짐", summary.headsUpLabel)
        assertEquals("내용 일부만 표시", summary.lockScreenVisibilityLabel)
        assertEquals(
            "덜 급한 알림이라 Digest로 모아두고 조용하게 보여줘요.",
            summary.overview,
        )
    }

    @Test
    fun builds_silent_summary_with_hidden_preview_copy() {
        val summary = builder.build(
            notification(
                status = NotificationStatusUi.SILENT,
                alertLevel = AlertLevel.LOUD,
                vibrationMode = VibrationMode.STRONG,
                headsUpEnabled = true,
                lockScreenVisibility = LockScreenVisibilityMode.PUBLIC,
            )
        )

        assertEquals("조용히 보관", summary.deliveryModeLabel)
        assertEquals("조용히 표시", summary.alertLevelLabel)
        assertEquals("진동 없음", summary.vibrationLabel)
        assertEquals("꺼짐", summary.headsUpLabel)
        assertEquals("내용 일부만 표시", summary.lockScreenVisibilityLabel)
        assertEquals(
            "사용자 방해를 줄이기 위해 조용히 보관하고 필요할 때만 열어볼 수 있게 했어요.",
            summary.overview,
        )
    }

    private fun notification(
        status: NotificationStatusUi,
        alertLevel: AlertLevel,
        vibrationMode: VibrationMode,
        headsUpEnabled: Boolean,
        lockScreenVisibility: LockScreenVisibilityMode,
    ) = NotificationUiModel(
        id = "id-1",
        appName = "카카오톡",
        packageName = "com.kakao.talk",
        sender = "엄마",
        title = "엄마",
        body = "오늘 저녁 몇 시에 와?",
        receivedAtLabel = "방금",
        status = status,
        reasonTags = listOf("중요한 사람"),
        alertLevel = alertLevel,
        vibrationMode = vibrationMode,
        headsUpEnabled = headsUpEnabled,
        lockScreenVisibility = lockScreenVisibility,
    )
}
