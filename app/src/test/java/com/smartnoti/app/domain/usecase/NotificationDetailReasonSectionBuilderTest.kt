package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDetailReasonSectionBuilderTest {

    private val builder = NotificationDetailReasonSectionBuilder()

    @Test
    fun classifier_signals_passthrough_when_no_rule_hits() {
        val notification = sampleNotification(
            reasonTags = listOf("발신자 있음", "조용한 시간", "반복 알림"),
            matchedRuleIds = emptyList(),
        )

        val sections = builder.build(notification, rules = emptyList())

        assertEquals(listOf("발신자 있음", "조용한 시간", "반복 알림"), sections.classifierSignals)
        assertTrue(sections.ruleHits.isEmpty())
    }

    @Test
    fun rule_hits_resolved_against_rules_list() {
        val rule = sampleRule(id = "rule-1", title = "엄마", matchValue = "엄마")
        val notification = sampleNotification(
            reasonTags = listOf("발신자 있음", "사용자 규칙", "엄마"),
            matchedRuleIds = listOf("rule-1"),
        )

        val sections = builder.build(notification, rules = listOf(rule))

        assertEquals(1, sections.ruleHits.size)
        assertEquals("rule-1", sections.ruleHits[0].ruleId)
        assertEquals("엄마", sections.ruleHits[0].title)
    }

    @Test
    fun rule_hit_title_is_removed_from_classifier_signals_to_avoid_duplication() {
        val rule = sampleRule(id = "rule-1", title = "엄마", matchValue = "엄마")
        val notification = sampleNotification(
            reasonTags = listOf("발신자 있음", "엄마", "조용한 시간"),
            matchedRuleIds = listOf("rule-1"),
        )

        val sections = builder.build(notification, rules = listOf(rule))

        assertEquals(listOf("발신자 있음", "조용한 시간"), sections.classifierSignals)
    }

    @Test
    fun umbrella_tags_filtered_when_rule_resolved() {
        val rule = sampleRule(id = "rule-1", title = "결제 키워드", matchValue = "결제")
        val notification = sampleNotification(
            reasonTags = listOf("사용자 규칙", "온보딩 추천", "결제 키워드", "반복 알림"),
            matchedRuleIds = listOf("rule-1"),
        )

        val sections = builder.build(notification, rules = listOf(rule))

        assertEquals(listOf("반복 알림"), sections.classifierSignals)
        assertEquals(listOf("rule-1"), sections.ruleHits.map { it.ruleId })
    }

    @Test
    fun umbrella_tags_preserved_when_rule_is_stale() {
        val notification = sampleNotification(
            reasonTags = listOf("사용자 규칙", "온보딩 추천", "결제 키워드"),
            matchedRuleIds = listOf("rule-1"),
        )

        // Rules list no longer contains rule-1 — the deleted rule scenario.
        val sections = builder.build(notification, rules = emptyList())

        assertEquals(
            listOf("사용자 규칙", "온보딩 추천", "결제 키워드"),
            sections.classifierSignals,
        )
        assertTrue(sections.ruleHits.isEmpty())
    }

    @Test
    fun multiple_matched_rules_preserve_order_and_deduplicate() {
        val ruleA = sampleRule(id = "rule-a", title = "엄마", matchValue = "엄마")
        val ruleB = sampleRule(id = "rule-b", title = "인증번호", matchValue = "인증번호")
        val notification = sampleNotification(
            reasonTags = listOf("사용자 규칙", "엄마", "인증번호"),
            matchedRuleIds = listOf("rule-a", "rule-b", "rule-a"),
        )

        val sections = builder.build(notification, rules = listOf(ruleA, ruleB))

        assertEquals(listOf("rule-a", "rule-b"), sections.ruleHits.map { it.ruleId })
        assertTrue(sections.classifierSignals.isEmpty())
    }

    @Test
    fun empty_when_no_reasons_and_no_matched_rules() {
        val notification = sampleNotification(
            reasonTags = emptyList(),
            matchedRuleIds = emptyList(),
        )

        val sections = builder.build(notification, rules = emptyList())

        assertTrue(sections.classifierSignals.isEmpty())
        assertTrue(sections.ruleHits.isEmpty())
    }

    private fun sampleNotification(
        reasonTags: List<String>,
        matchedRuleIds: List<String>,
    ) = NotificationUiModel(
        id = "n1",
        appName = "카카오톡",
        packageName = "com.kakao.talk",
        sender = "엄마",
        title = "title",
        body = "body",
        receivedAtLabel = "방금",
        status = NotificationStatusUi.PRIORITY,
        reasonTags = reasonTags,
        matchedRuleIds = matchedRuleIds,
    )

    private fun sampleRule(
        id: String,
        title: String,
        matchValue: String,
    ) = RuleUiModel(
        id = id,
        title = title,
        subtitle = "항상 바로 보기",
        type = RuleTypeUi.PERSON,
        enabled = true,
        matchValue = matchValue,
    )
}
