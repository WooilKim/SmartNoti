package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.data.settings.SmartNotiSettings

data class SettingsOperationalSummary(
    val modeTitle: String,
    val modeDetail: String,
    val quietHoursWindow: String,
    val quietHoursState: String,
    val quietHoursEnabled: Boolean,
    val digestSchedule: String,
    val digestDetail: String,
)

class SettingsOperationalSummaryBuilder {
    fun build(settings: SmartNotiSettings): SettingsOperationalSummary {
        val modeTitle = if (settings.quietHoursEnabled) "조용한 시간 자동 적용" else "항상 즉시 분류"
        val modeDetail = if (settings.quietHoursEnabled) {
            "지정한 시간대에는 덜 급한 알림을 정리함 중심으로 다뤄요."
        } else {
            "모든 시간대에 동일한 기준으로 바로 분류해요."
        }

        val quietHoursWindow = "${formatHour(settings.quietHoursStartHour)} ~ ${formatHour(settings.quietHoursEndHour)}"
        val quietHoursState = if (settings.quietHoursEnabled) "자동 완화 사용 중" else "꺼짐"

        val digestSchedule = if (settings.digestHours.isEmpty()) {
            "예약된 정리 시점이 없어요"
        } else {
            settings.digestHours.joinToString(" · ") { formatHour(it) }
        }
        val digestDetail = if (settings.digestHours.isEmpty()) {
            "정리 시점 없음"
        } else {
            "정리 시점 ${settings.digestHours.size}개"
        }

        return SettingsOperationalSummary(
            modeTitle = modeTitle,
            modeDetail = modeDetail,
            quietHoursWindow = quietHoursWindow,
            quietHoursState = quietHoursState,
            quietHoursEnabled = settings.quietHoursEnabled,
            digestSchedule = digestSchedule,
            digestDetail = digestDetail,
        )
    }

    private fun formatHour(hour: Int): String = "%02d:00".format(hour)
}
