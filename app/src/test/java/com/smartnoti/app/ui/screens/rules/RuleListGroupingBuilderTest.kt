package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleListGroupingBuilderTest {

    private val builder = RuleListGroupingBuilder()

    @Test
    fun build_orders_groups_by_operator_priority_and_hides_empty_actions() {
        val rules = listOf(rule("1"), rule("2"), rule("3"), rule("4"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.SILENT,
            "2" to RuleActionUi.DIGEST,
            "3" to RuleActionUi.ALWAYS_PRIORITY,
            "4" to RuleActionUi.DIGEST,
        )

        val groups = builder.build(rules, ruleActions)

        assertEquals(
            listOf("즉시 전달", "Digest", "조용히"),
            groups.map { it.title },
        )
        assertEquals(
            listOf(listOf("3"), listOf("2", "4"), listOf("1")),
            groups.map { group -> group.rules.map { it.id } },
        )
    }

    @Test
    fun build_emits_ignore_group_after_silent_and_contextual() {
        val rules = listOf(rule("1"), rule("2"), rule("3"), rule("4"), rule("5"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.ALWAYS_PRIORITY,
            "2" to RuleActionUi.DIGEST,
            "3" to RuleActionUi.SILENT,
            "4" to RuleActionUi.IGNORE,
            "5" to RuleActionUi.IGNORE,
        )

        val groups = builder.build(rules, ruleActions)

        assertEquals(
            listOf("즉시 전달", "Digest", "조용히", "무시"),
            groups.map { it.title },
        )
        assertEquals(
            listOf(listOf("1"), listOf("2"), listOf("3"), listOf("4", "5")),
            groups.map { group -> group.rules.map { it.id } },
        )
    }

    @Test
    fun build_omits_ignore_group_when_no_ignore_rules_present() {
        val rules = listOf(rule("1"), rule("2"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.ALWAYS_PRIORITY,
            "2" to RuleActionUi.DIGEST,
        )

        val groups = builder.build(rules, ruleActions)

        assertEquals(
            listOf("즉시 전달", "Digest"),
            groups.map { it.title },
        )
    }

    @Test
    fun build_uses_count_based_section_subtitles() {
        val rules = listOf(rule("1"), rule("2"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.CONTEXTUAL,
            "2" to RuleActionUi.CONTEXTUAL,
        )

        val groups = builder.build(rules, ruleActions)

        assertEquals("상황별", groups.single().title)
        assertEquals("규칙 2개", groups.single().subtitle)
    }

    private fun rule(id: String) = RuleUiModel(
        id = id,
        title = "규칙$id",
        subtitle = "subtitle$id",
        type = RuleTypeUi.KEYWORD,
        enabled = true,
        matchValue = "match$id",
    )
}
