package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleDraftFactoryTest {

    private val factory = RuleDraftFactory()

    @Test
    fun creates_person_rule_draft_with_human_readable_subtitle() {
        val draft = factory.create(
            title = "팀장",
            matchValue = "팀장",
            type = RuleTypeUi.PERSON,
            action = RuleActionUi.ALWAYS_PRIORITY,
        )

        assertEquals("팀장", draft.title)
        assertEquals("항상 바로 보기", draft.subtitle)
        assertEquals("팀장", draft.matchValue)
        assertEquals(RuleTypeUi.PERSON, draft.type)
        assertEquals(RuleActionUi.ALWAYS_PRIORITY, draft.action)
        assertEquals(true, draft.enabled)
    }

    @Test
    fun creates_app_rule_with_trimmed_values_and_deterministic_id() {
        val draft = factory.create(
            title = "  쿠팡  ",
            matchValue = "  com.coupang.mobile  ",
            type = RuleTypeUi.APP,
            action = RuleActionUi.DIGEST,
        )

        assertEquals("쿠팡", draft.title)
        assertEquals("com.coupang.mobile", draft.matchValue)
        assertEquals("app:com.coupang.mobile", draft.id)
    }

    @Test
    fun keyword_rule_normalizes_multiple_keywords_into_canonical_list() {
        val draft = factory.create(
            title = "업무 긴급 키워드",
            matchValue = "  배포, 장애 , 배포 ,, 긴급 ",
            type = RuleTypeUi.KEYWORD,
            action = RuleActionUi.ALWAYS_PRIORITY,
        )

        assertEquals("배포,장애,긴급", draft.matchValue)
        assertEquals("keyword:배포,장애,긴급", draft.id)
    }

    @Test
    fun schedule_rule_normalizes_overnight_hours_into_canonical_range() {
        val draft = factory.create(
            title = "야간 근무",
            matchValue = " 23-7 ",
            type = RuleTypeUi.SCHEDULE,
            action = RuleActionUi.DIGEST,
        )

        assertEquals("23-7", draft.matchValue)
        assertEquals("schedule:23-7", draft.id)
    }

    @Test
    fun repeat_bundle_rule_normalizes_numeric_threshold() {
        val draft = factory.create(
            title = "반복 푸시",
            matchValue = " 05회 ",
            type = RuleTypeUi.REPEAT_BUNDLE,
            action = RuleActionUi.DIGEST,
        )

        assertEquals("5", draft.matchValue)
        assertEquals("repeat_bundle:5", draft.id)
    }

    @Test
    fun override_of_is_threaded_through_to_the_created_rule() {
        // Plan rules-ux-v2-inbox-restructure Phase C Task 4: rule editor
        // dialog wires override-of into the factory so override creation
        // yields the right data shape.
        val draft = factory.create(
            title = "광고 예외",
            matchValue = "광고",
            type = RuleTypeUi.KEYWORD,
            action = RuleActionUi.SILENT,
            overrideOf = "keyword:결제",
        )

        assertEquals("keyword:결제", draft.overrideOf)
    }

    @Test
    fun override_of_defaults_to_null_for_base_rules() {
        val draft = factory.create(
            title = "기본 규칙",
            matchValue = "결제",
            type = RuleTypeUi.KEYWORD,
            action = RuleActionUi.ALWAYS_PRIORITY,
        )

        assertEquals(null, draft.overrideOf)
    }
}
