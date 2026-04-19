package com.smartnoti.app.domain.usecase

import com.smartnoti.app.onboarding.OnboardingStatus

data class HomeNotificationAccessSummary(
    val granted: Boolean,
    val statusLabel: String,
    val title: String,
    val body: String,
    val actionLabel: String,
)

class HomeNotificationAccessSummaryBuilder {
    fun build(
        status: OnboardingStatus,
        recentCount: Int,
        priorityCount: Int,
        digestCount: Int,
        silentCount: Int,
    ): HomeNotificationAccessSummary {
        if (!status.notificationListenerGranted) {
            return HomeNotificationAccessSummary(
                granted = false,
                statusLabel = "연결 필요",
                title = "실제 알림 연결이 필요해요",
                body = "아직 실제 알림을 읽지 못하고 있어요. 알림 접근을 켜면 Home에 실제 알림과 추천 규칙 효과가 바로 보여요.",
                actionLabel = "설정에서 연결하기",
            )
        }

        val body = if (recentCount > 0) {
            "최근 실제 알림 ${recentCount}개가 Home에 반영됐어요 · 즉시 ${priorityCount}개 · Digest ${digestCount}개 · 조용히 ${silentCount}개로 분류됐어요."
        } else {
            "연결은 완료됐어요. 이제 들어오는 실제 알림이 Home·Priority·Digest에 바로 반영돼요."
        }

        return HomeNotificationAccessSummary(
            granted = true,
            statusLabel = "연결됨",
            title = "실제 알림이 연결되어 있어요",
            body = body,
            actionLabel = "설정에서 연결 상태 보기",
        )
    }
}
