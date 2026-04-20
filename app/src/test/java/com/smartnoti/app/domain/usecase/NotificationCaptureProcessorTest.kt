package com.smartnoti.app.domain.usecase

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.CapturedNotificationInput
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.VibrationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationCaptureProcessorTest {

    private val processor = NotificationCaptureProcessor(
        classifier = NotificationClassifier(
            vipSenders = setOf("엄마", "팀장"),
            priorityKeywords = setOf("인증번호", "OTP", "결제"),
            shoppingPackages = setOf("com.coupang.mobile")
        ),
        deliveryProfilePolicy = DeliveryProfilePolicy(),
    )

    @Test
    fun matched_user_rule_becomes_priority_with_rule_reason_tag() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.kakao.talk",
                appName = "카카오톡",
                sender = "고객",
                title = "고객",
                body = "긴급 회의 일정 확인",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 0
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r1",
                    title = "고객",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.PERSON,
                    action = RuleActionUi.ALWAYS_PRIORITY,
                    enabled = true,
                    matchValue = "고객",
                )
            ),
            settings = SmartNotiSettings(),
        )

        assertEquals(NotificationStatusUi.PRIORITY, result.status)
        assertTrue(result.reasonTags.contains("사용자 규칙"))
        assertTrue(result.reasonTags.contains("고객"))
    }

    @Test
    fun onboarding_promo_rule_adds_onboarding_recommendation_reason_tag() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.coupang.mobile",
                appName = "쿠팡",
                sender = null,
                title = "(광고) 오늘만 특가",
                body = "쿠폰을 확인해 보세요",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 0,
            ),
            rules = listOf(
                RuleUiModel(
                    id = "keyword:promo",
                    title = "프로모션 알림",
                    subtitle = "Digest로 묶기",
                    type = RuleTypeUi.KEYWORD,
                    action = RuleActionUi.DIGEST,
                    enabled = true,
                    matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택",
                ),
            ),
            settings = SmartNotiSettings(),
        )

        assertEquals(NotificationStatusUi.DIGEST, result.status)
        assertTrue(result.reasonTags.contains("프로모션 알림"))
        assertTrue(result.reasonTags.contains("온보딩 추천"))
    }

    @Test
    fun onboarding_important_rule_matches_keywords_inside_comma_delimited_rule() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.toss.app",
                appName = "토스",
                sender = null,
                title = "결제가 완료됐어요",
                body = "승인 내역을 확인해 주세요",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 0,
            ),
            rules = listOf(
                RuleUiModel(
                    id = "keyword:important",
                    title = "중요 알림",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.KEYWORD,
                    action = RuleActionUi.ALWAYS_PRIORITY,
                    enabled = true,
                    matchValue = "인증번호,결제,배송,출발",
                ),
            ),
            settings = SmartNotiSettings(),
        )

        assertEquals(NotificationStatusUi.PRIORITY, result.status)
        assertTrue(result.reasonTags.contains("중요 알림"))
        assertTrue(result.reasonTags.contains("온보딩 추천"))
    }

    @Test
    fun notification_ids_remain_unique_when_same_package_posts_multiple_items_in_same_millisecond() {
        val first = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.smartnoti.testnotifier",
                appName = "SmartNoti Test Notifier",
                sender = null,
                title = "오늘만 특가 안내",
                body = "멤버십 쿠폰이 도착했어요.",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 1,
                sourceEntryKey = "0|com.smartnoti.testnotifier|101|promo",
            ),
            settings = SmartNotiSettings(),
        )
        val second = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.smartnoti.testnotifier",
                appName = "SmartNoti Test Notifier",
                sender = null,
                title = "배달 상태 업데이트",
                body = "라이더 위치가 갱신됐어요.",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 1,
                sourceEntryKey = "0|com.smartnoti.testnotifier|102|delivery",
            ),
            settings = SmartNotiSettings(),
        )

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun blank_group_summary_notification_uses_status_bar_key_suffix_in_id() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.smartnoti.testnotifier",
                appName = "SmartNoti Test Notifier",
                sender = null,
                title = "",
                body = "",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 1,
                sourceEntryKey = "0|com.smartnoti.testnotifier|2147483647|ranker_group|10193|ranker_group",
            ),
            settings = SmartNotiSettings(),
        )

        assertEquals(
            "com.smartnoti.testnotifier:2147483647:ranker_group",
            result.id,
        )
    }

    @Test
    fun vip_sender_becomes_priority_notification_with_priority_delivery_metadata() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.kakao.talk",
                appName = "카카오톡",
                sender = "엄마",
                title = "엄마",
                body = "오늘 저녁 몇 시에 와?",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 0
            ),
            settings = SmartNotiSettings(),
        )

        assertEquals(NotificationStatusUi.PRIORITY, result.status)
        assertTrue(result.reasonTags.contains("중요한 사람"))
        assertEquals("엄마", result.sender)
        assertEquals("smartnoti_priority", result.deliveryChannelKey)
        assertEquals(AlertLevel.LOUD, result.alertLevel)
        assertEquals(VibrationMode.STRONG, result.vibrationMode)
        assertTrue(result.headsUpEnabled)
    }

    @Test
    fun shopping_notification_during_quiet_hours_becomes_digest_with_soft_metadata() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.coupang.mobile",
                appName = "쿠팡",
                sender = null,
                title = "오늘의 특가",
                body = "장바구니 상품이 할인 중이에요",
                postedAtMillis = 1_700_000_000_000,
                quietHours = true,
                duplicateCountInWindow = 0
            ),
            settings = SmartNotiSettings(),
        )

        assertEquals(NotificationStatusUi.DIGEST, result.status)
        assertTrue(result.reasonTags.contains("쇼핑 앱"))
        assertTrue(result.reasonTags.contains("조용한 시간"))
        assertEquals("smartnoti_digest", result.deliveryChannelKey)
        assertEquals(AlertLevel.SOFT, result.alertLevel)
        assertEquals(VibrationMode.LIGHT, result.vibrationMode)
        assertFalse(result.headsUpEnabled)
    }

    @Test
    fun repeated_priority_notification_softens_delivery_metadata() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.chat.app",
                appName = "채팅",
                sender = "팀장",
                title = "긴급 확인",
                body = "유사 알림이 반복 도착했어요",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 3
            ),
            settings = SmartNotiSettings(),
        )

        assertEquals(NotificationStatusUi.PRIORITY, result.status)
        assertTrue(result.reasonTags.contains("반복 알림"))
        assertEquals("smartnoti_priority", result.deliveryChannelKey)
        assertEquals(AlertLevel.SOFT, result.alertLevel)
        assertEquals(VibrationMode.LIGHT, result.vibrationMode)
        assertFalse(result.headsUpEnabled)
    }

    @Test
    fun settings_override_priority_delivery_metadata_used_by_processor() {
        val result = processor.process(
            input = CapturedNotificationInput(
                packageName = "com.chat.app",
                appName = "채팅",
                sender = "팀장",
                title = "확인 부탁",
                body = "긴급 확인",
                postedAtMillis = 1_700_000_000_000,
                quietHours = false,
                duplicateCountInWindow = 0,
            ),
            settings = SmartNotiSettings(
                priorityAlertLevel = "QUIET",
                priorityVibrationMode = "OFF",
                priorityHeadsUpEnabled = false,
                priorityLockScreenVisibility = "SECRET",
            ),
        )

        assertEquals(NotificationStatusUi.PRIORITY, result.status)
        assertEquals("smartnoti_priority", result.deliveryChannelKey)
        assertEquals(AlertLevel.QUIET, result.alertLevel)
        assertEquals(VibrationMode.OFF, result.vibrationMode)
        assertFalse(result.headsUpEnabled)
        assertEquals(LockScreenVisibilityMode.SECRET, result.lockScreenVisibility)
    }
}
