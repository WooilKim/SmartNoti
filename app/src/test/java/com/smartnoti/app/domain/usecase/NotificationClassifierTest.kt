package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationClassifierTest {

    private val classifier = NotificationClassifier(
        vipSenders = setOf("엄마", "팀장"),
        priorityKeywords = setOf("인증번호", "OTP", "결제"),
        shoppingPackages = setOf("com.coupang.mobile")
    )

    @Test
    fun user_person_rule_is_applied_before_default_logic() {
        val result = classifier.classify(
            input = ClassificationInput(
                sender = "고객",
                packageName = "com.kakao.talk",
                body = "회의 일정 확인 부탁드려요"
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
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result)
    }

    @Test
    fun keyword_rule_matches_any_keyword_in_comma_separated_list() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                title = "오늘 운영 현황",
                body = "새로운 장애 접수가 도착했어요"
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r-keywords",
                    title = "운영 키워드",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.KEYWORD,
                    action = RuleActionUi.ALWAYS_PRIORITY,
                    enabled = true,
                    matchValue = "배포,장애,긴급",
                )
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result)
    }

    @Test
    fun disabled_user_rule_is_ignored() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.social.app",
                body = "새로운 좋아요가 도착했어요"
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r2",
                    title = "소셜",
                    subtitle = "항상 조용히",
                    type = RuleTypeUi.APP,
                    action = RuleActionUi.SILENT,
                    enabled = false,
                    matchValue = "com.social.app",
                )
            )
        )

        assertEquals(NotificationDecision.SILENT, result)
    }

    @Test
    fun vip_sender_is_always_priority() {
        val result = classifier.classify(
            ClassificationInput(
                sender = "엄마",
                packageName = "com.kakao.talk",
                body = "오늘 저녁 몇 시에 와?"
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result)
    }

    @Test
    fun priority_keyword_is_always_priority() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.bank.app",
                body = "인증번호 123456"
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result)
    }

    @Test
    fun shopping_app_during_quiet_hours_goes_to_digest() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.coupang.mobile",
                body = "장바구니 상품이 할인 중이에요",
                quietHours = true
            )
        )

        assertEquals(NotificationDecision.DIGEST, result)
    }

    @Test
    fun repeated_notifications_go_to_digest() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보가 도착했어요",
                duplicateCountInWindow = 3
            )
        )

        assertEquals(NotificationDecision.DIGEST, result)
    }

    @Test
    fun default_case_is_silent() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.social.app",
                body = "새로운 좋아요가 도착했어요"
            )
        )

        assertEquals(NotificationDecision.SILENT, result)
    }
}
