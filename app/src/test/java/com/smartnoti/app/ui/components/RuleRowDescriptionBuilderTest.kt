package com.smartnoti.app.ui.components

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleRowDescriptionBuilderTest {

    private val builder = RuleRowDescriptionBuilder()

    @Test
    fun app_rule_description_shows_package_and_action_sentence() {
        val description = builder.build(
            rule = RuleUiModel(
                id = "app:com.coupang.mobile",
                title = "쿠팡",
                subtitle = "Digest로 묶기",
                type = RuleTypeUi.APP,
                enabled = true,
                matchValue = "com.coupang.mobile",
            ),
            action = RuleActionUi.DIGEST,
        )

        assertEquals("쿠팡 알림은 Digest로 묶어요", description.primaryText)
        assertEquals("패키지 · com.coupang.mobile", description.secondaryText)
        assertEquals(null, description.emphasisLabel)
    }

    @Test
    fun onboarding_recommendation_rule_shows_emphasis_label() {
        val description = builder.build(
            rule = RuleUiModel(
                id = "keyword:promo",
                title = "프로모션 알림",
                subtitle = "Digest로 묶기",
                type = RuleTypeUi.KEYWORD,
                enabled = true,
                matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택",
            ),
            action = RuleActionUi.DIGEST,
        )

        assertEquals("온보딩 추천", description.emphasisLabel)
        assertTrue(description.secondaryText.contains("키워드 기준"))
    }

    @Test
    fun schedule_rule_description_formats_time_window() {
        val description = builder.build(
            rule = RuleUiModel(
                id = "schedule:23-7",
                title = "야간 정리",
                subtitle = "조용히 정리",
                type = RuleTypeUi.SCHEDULE,
                enabled = true,
                matchValue = "23-7",
            ),
            action = RuleActionUi.SILENT,
        )

        assertEquals("23:00 ~ 07:00에는 조용히 정리해요", description.primaryText)
    }

    @Test
    fun ignore_keyword_rule_description_uses_destructive_copy() {
        val description = builder.build(
            rule = RuleUiModel(
                id = "keyword:광고",
                title = "광고 키워드",
                subtitle = "무시 (즉시 삭제)",
                type = RuleTypeUi.KEYWORD,
                enabled = true,
                matchValue = "광고",
            ),
            action = RuleActionUi.IGNORE,
        )

        assertEquals(
            "'광고'가 들어오면 알림센터에서 즉시 삭제하고 앱에서도 숨겨요",
            description.primaryText,
        )
        assertEquals("키워드 기준", description.secondaryText)
    }

    @Test
    fun ignore_app_rule_description_keeps_package_secondary_text() {
        val description = builder.build(
            rule = RuleUiModel(
                id = "app:com.ads.example",
                title = "광고 앱",
                subtitle = "무시 (즉시 삭제)",
                type = RuleTypeUi.APP,
                enabled = true,
                matchValue = "com.ads.example",
            ),
            action = RuleActionUi.IGNORE,
        )

        assertEquals(
            "광고 앱 알림은 알림센터에서 즉시 삭제하고 앱에서도 숨겨요",
            description.primaryText,
        )
        assertEquals("패키지 · com.ads.example", description.secondaryText)
    }

    @Test
    fun repeat_bundle_rule_description_explains_threshold() {
        val description = builder.build(
            rule = RuleUiModel(
                id = "repeat_bundle:5",
                title = "반복 알림",
                subtitle = "항상 바로 보기",
                type = RuleTypeUi.REPEAT_BUNDLE,
                enabled = true,
                matchValue = "5",
            ),
            action = RuleActionUi.ALWAYS_PRIORITY,
        )

        assertEquals("같은 알림이 5회 이상 반복되면 바로 보여줘요", description.primaryText)
    }
}
