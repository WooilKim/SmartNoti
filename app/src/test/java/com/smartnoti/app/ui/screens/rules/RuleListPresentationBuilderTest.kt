package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleListPresentationBuilderTest {

    private val builder = RuleListPresentationBuilder()

    @Test
    fun build_creates_overview_and_filter_counts_in_operator_order() {
        val rules = listOf(rule("1"), rule("2"), rule("3"), rule("4"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.DIGEST,
            "2" to RuleActionUi.ALWAYS_PRIORITY,
            "3" to RuleActionUi.SILENT,
            "4" to RuleActionUi.DIGEST,
        )

        val presentation = builder.build(rules, ruleActions)

        assertEquals("전체 4개 · 즉시 전달 1 · Digest 2 · 조용히 1", presentation.overview)
        assertEquals(
            listOf("전체 4", "즉시 전달 1", "Digest 2", "조용히 1"),
            presentation.filters.map { it.label },
        )
        assertEquals(
            listOf(null, RuleActionUi.ALWAYS_PRIORITY, RuleActionUi.DIGEST, RuleActionUi.SILENT),
            presentation.filters.map { it.action },
        )
    }

    @Test
    fun build_includes_ignore_overview_segment_and_filter_when_ignore_rules_exist() {
        val rules = listOf(rule("1"), rule("2"), rule("3"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.ALWAYS_PRIORITY,
            "2" to RuleActionUi.IGNORE,
            "3" to RuleActionUi.IGNORE,
        )

        val presentation = builder.build(rules, ruleActions)

        assertEquals(
            "전체 3개 · 즉시 전달 1 · Digest 0 · 조용히 0 · 무시 2",
            presentation.overview,
        )
        assertEquals(
            listOf("전체 3", "즉시 전달 1", "무시 2"),
            presentation.filters.map { it.label },
        )
        assertEquals(
            listOf(null, RuleActionUi.ALWAYS_PRIORITY, RuleActionUi.IGNORE),
            presentation.filters.map { it.action },
        )
    }

    @Test
    fun build_omits_ignore_segments_when_no_ignore_rules_present() {
        val rules = listOf(rule("1"), rule("2"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.ALWAYS_PRIORITY,
            "2" to RuleActionUi.DIGEST,
        )

        val presentation = builder.build(rules, ruleActions)

        assertEquals(
            "전체 2개 · 즉시 전달 1 · Digest 1 · 조용히 0",
            presentation.overview,
        )
        assertEquals(
            listOf("전체 2", "즉시 전달 1", "Digest 1"),
            presentation.filters.map { it.label },
        )
    }

    @Test
    fun build_only_includes_contextual_filter_when_rules_exist() {
        val rules = listOf(rule("1"), rule("2"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.ALWAYS_PRIORITY,
            "2" to RuleActionUi.CONTEXTUAL,
        )

        val presentation = builder.build(rules, ruleActions)

        assertEquals(
            listOf("전체 2", "즉시 전달 1", "상황별 1"),
            presentation.filters.map { it.label },
        )
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
