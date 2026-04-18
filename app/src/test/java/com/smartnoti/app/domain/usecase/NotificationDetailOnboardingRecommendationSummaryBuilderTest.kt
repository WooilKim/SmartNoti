package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationDetailOnboardingRecommendationSummaryBuilderTest {

    private val builder = NotificationDetailOnboardingRecommendationSummaryBuilder()

    @Test
    fun onboarding_recommendation_notification_returns_summary() {
        val summary = builder.build(
            NotificationUiModel(
                id = "n1",
                appName = "쿠팡",
                packageName = "com.coupang.mobile",
                sender = null,
                title = "(광고) 오늘만 특가",
                body = "쿠폰을 확인해 보세요",
                receivedAtLabel = "방금",
                status = NotificationStatusUi.DIGEST,
                reasonTags = listOf("사용자 규칙", "프로모션 알림", "온보딩 추천"),
            ),
        )

        requireNotNull(summary)
        assertEquals("빠른 시작 추천에서 추가된 규칙이에요", summary.title)
        assertEquals("온보딩에서 선택한 '프로모션 알림' 추천이 이 알림 정리에 영향을 줬어요", summary.body)
    }

    @Test
    fun non_onboarding_notification_returns_null() {
        val summary = builder.build(
            NotificationUiModel(
                id = "n2",
                appName = "카카오톡",
                packageName = "com.kakao.talk",
                sender = "엄마",
                title = "엄마",
                body = "전화해",
                receivedAtLabel = "방금",
                status = NotificationStatusUi.PRIORITY,
                reasonTags = listOf("사용자 규칙", "엄마"),
            ),
        )

        assertNull(summary)
    }
}
