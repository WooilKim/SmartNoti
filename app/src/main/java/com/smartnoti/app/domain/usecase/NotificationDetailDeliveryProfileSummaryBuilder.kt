package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.DeliveryMode
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.toDeliveryProfileOrDefault
import com.smartnoti.app.domain.model.toDecision
import com.smartnoti.app.domain.model.VibrationMode

class NotificationDetailDeliveryProfileSummaryBuilder {
    fun build(notification: NotificationUiModel): NotificationDetailDeliveryProfileSummary {
        val decision = notification.status.toDecision()
        val deliveryProfile = notification.toDeliveryProfileOrDefault()

        return NotificationDetailDeliveryProfileSummary(
            deliveryModeLabel = deliveryProfile.deliveryMode.toLabel(),
            alertLevelLabel = deliveryProfile.alertLevel.toLabel(),
            vibrationLabel = deliveryProfile.vibrationMode.toLabel(),
            headsUpLabel = if (deliveryProfile.headsUpEnabled) "켜짐" else "꺼짐",
            lockScreenVisibilityLabel = deliveryProfile.lockScreenVisibilityMode.toLabel(),
            overview = when (decision) {
                com.smartnoti.app.domain.model.NotificationDecision.PRIORITY -> {
                    if (deliveryProfile.headsUpEnabled) {
                        "중요 알림으로 바로 전달되고, 화면 상단 heads-up으로도 보여줘요."
                    } else {
                        "중요 알림으로 바로 전달하지만 방해를 줄이기 위해 heads-up은 띄우지 않아요."
                    }
                }
                com.smartnoti.app.domain.model.NotificationDecision.DIGEST -> {
                    "덜 급한 알림이라 Digest로 모아두고 조용하게 보여줘요."
                }
                com.smartnoti.app.domain.model.NotificationDecision.SILENT -> {
                    "사용자 방해를 줄이기 위해 조용히 보관하고 필요할 때만 열어볼 수 있게 했어요."
                }
            },
        )
    }
}

data class NotificationDetailDeliveryProfileSummary(
    val deliveryModeLabel: String,
    val alertLevelLabel: String,
    val vibrationLabel: String,
    val headsUpLabel: String,
    val lockScreenVisibilityLabel: String,
    val overview: String,
)

private fun DeliveryMode.toLabel(): String = when (this) {
    DeliveryMode.IMMEDIATE -> "즉시 전달"
    DeliveryMode.BATCHED -> "Digest 묶음 전달"
    DeliveryMode.SUMMARY_ONLY -> "조용히 보관"
    DeliveryMode.LOG_ONLY -> "기록만 보관"
}

private fun AlertLevel.toLabel(): String = when (this) {
    AlertLevel.LOUD -> "강하게 알림"
    AlertLevel.SOFT -> "부드럽게 알림"
    AlertLevel.QUIET -> "조용히 표시"
    AlertLevel.NONE -> "소리 없이 표시"
}

private fun VibrationMode.toLabel(): String = when (this) {
    VibrationMode.STRONG -> "강한 진동"
    VibrationMode.LIGHT -> "가벼운 진동"
    VibrationMode.OFF -> "진동 없음"
}

private fun LockScreenVisibilityMode.toLabel(): String = when (this) {
    LockScreenVisibilityMode.PUBLIC -> "전체 내용 표시"
    LockScreenVisibilityMode.PRIVATE -> "내용 일부만 표시"
    LockScreenVisibilityMode.SECRET -> "잠금화면에 숨김"
}
