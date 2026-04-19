package com.smartnoti.app.ui.screens.settings

import com.smartnoti.app.onboarding.OnboardingStatus

data class SettingsNotificationAccessSummary(
    val granted: Boolean,
    val statusLabel: String,
    val headline: String,
    val supporting: String,
    val pathDescription: String,
    val impactDescription: String,
    val actionLabel: String,
)

class SettingsNotificationAccessSummaryBuilder {
    fun build(status: OnboardingStatus): SettingsNotificationAccessSummary {
        val granted = status.notificationListenerGranted
        return if (granted) {
            SettingsNotificationAccessSummary(
                granted = true,
                statusLabel = "연결됨",
                headline = "실제 알림이 Home에 반영되고 있어요",
                supporting = "들어오는 알림이 최근 효과 요약과 Priority·Digest 흐름에 바로 연결돼요.",
                pathDescription = "설정 → 알림 → 기기 및 앱 알림 → 알림 읽기",
                impactDescription = "SmartNoti가 실제 캡처된 알림만 홈 화면에 쌓고, 추천 규칙 효과도 최근 데이터 기준으로 보여줘요.",
                actionLabel = "알림 접근 설정 다시 열기",
            )
        } else {
            SettingsNotificationAccessSummary(
                granted = false,
                statusLabel = "연결 필요",
                headline = "아직 실제 알림을 읽지 못하고 있어요",
                supporting = "알림 접근을 켜면 들어오는 알림이 Home·Priority·Digest 흐름에 바로 반영돼요.",
                pathDescription = "설정 → 알림 → 기기 및 앱 알림 → 알림 읽기에서 SmartNoti를 켜주세요.",
                impactDescription = "연결 후에는 SmartNoti가 실제 캡처된 알림만 홈 화면에 쌓고, 추천 규칙 효과도 최근 데이터 기준으로 보여줘요.",
                actionLabel = "알림 접근 설정 열기",
            )
        }
    }
}
