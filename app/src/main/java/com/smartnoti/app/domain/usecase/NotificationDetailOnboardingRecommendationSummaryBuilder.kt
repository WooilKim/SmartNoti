package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationUiModel

data class NotificationDetailOnboardingRecommendationSummary(
    val title: String,
    val body: String,
)

class NotificationDetailOnboardingRecommendationSummaryBuilder {
    fun build(notification: NotificationUiModel): NotificationDetailOnboardingRecommendationSummary? {
        if (!notification.reasonTags.contains("온보딩 추천")) return null
        val matchedRecommendation = notification.reasonTags.firstOrNull {
            it in onboardingRecommendationTitles
        } ?: return null
        return NotificationDetailOnboardingRecommendationSummary(
            title = "빠른 시작 추천에서 추가된 규칙이에요",
            body = "온보딩에서 선택한 '$matchedRecommendation' 추천이 이 알림 정리에 영향을 줬어요",
        )
    }

    private companion object {
        val onboardingRecommendationTitles = setOf("프로모션 알림", "반복 알림", "중요 알림")
    }
}
