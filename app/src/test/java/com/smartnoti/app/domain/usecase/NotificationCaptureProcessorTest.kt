package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.NotificationStatusUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureProcessorTest {

    private val processor = NotificationCaptureProcessor(
        classifier = NotificationClassifier(
            vipSenders = setOf("엄마", "팀장"),
            priorityKeywords = setOf("인증번호", "OTP", "결제"),
            shoppingPackages = setOf("com.coupang.mobile")
        )
    )

    @Test
    fun vip_sender_becomes_priority_notification_with_reason_tag() {
        val result = processor.process(
            CapturedNotificationInput(
                packageName = "com.kakao.talk",
                appName = "카카오톡",
                sender = "엄마",
                title = "엄마",
                body = "오늘 저녁 몇 시에 와?",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 0
            )
        )

        assertEquals(NotificationStatusUi.PRIORITY, result.status)
        assertTrue(result.reasonTags.contains("중요한 사람"))
        assertEquals("엄마", result.sender)
    }

    @Test
    fun shopping_notification_during_quiet_hours_becomes_digest_with_tags() {
        val result = processor.process(
            CapturedNotificationInput(
                packageName = "com.coupang.mobile",
                appName = "쿠팡",
                sender = null,
                title = "오늘의 특가",
                body = "장바구니 상품이 할인 중이에요",
                postedAtMillis = 1_700_000_000_000,
                quietHours = true,
                duplicateCountInWindow = 0
            )
        )

        assertEquals(NotificationStatusUi.DIGEST, result.status)
        assertTrue(result.reasonTags.contains("쇼핑 앱"))
        assertTrue(result.reasonTags.contains("조용한 시간"))
    }

    @Test
    fun repeated_notification_becomes_digest_with_repeat_reason() {
        val result = processor.process(
            CapturedNotificationInput(
                packageName = "com.news.app",
                appName = "뉴스",
                sender = null,
                title = "속보",
                body = "유사 알림이 반복 도착했어요",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 3
            )
        )

        assertEquals(NotificationStatusUi.DIGEST, result.status)
        assertTrue(result.reasonTags.contains("반복 알림"))
    }
}
