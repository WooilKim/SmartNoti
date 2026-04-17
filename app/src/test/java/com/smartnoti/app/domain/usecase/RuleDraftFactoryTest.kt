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
}
