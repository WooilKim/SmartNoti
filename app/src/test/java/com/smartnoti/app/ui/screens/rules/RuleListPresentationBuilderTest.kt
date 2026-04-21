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
        val presentation = builder.build(
            listOf(
                rule(id = "1", action = RuleActionUi.DIGEST),
                rule(id = "2", action = RuleActionUi.ALWAYS_PRIORITY),
                rule(id = "3", action = RuleActionUi.SILENT),
                rule(id = "4", action = RuleActionUi.DIGEST),
            )
        )

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
        // Plan 2026-04-21-ignore-tier-fourth-decision Task 5 — the overview
        // strip and the filter chip row both surface an IGNORE entry so the
        // user can scope the list to destructive rules without scrolling.
        val presentation = builder.build(
            listOf(
                rule(id = "1", action = RuleActionUi.ALWAYS_PRIORITY),
                rule(id = "2", action = RuleActionUi.IGNORE),
                rule(id = "3", action = RuleActionUi.IGNORE),
            )
        )

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
        val presentation = builder.build(
            listOf(
                rule(id = "1", action = RuleActionUi.ALWAYS_PRIORITY),
                rule(id = "2", action = RuleActionUi.DIGEST),
            )
        )

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
        val presentation = builder.build(
            listOf(
                rule(id = "1", action = RuleActionUi.ALWAYS_PRIORITY),
                rule(id = "2", action = RuleActionUi.CONTEXTUAL),
            )
        )

        assertEquals(
            listOf("전체 2", "즉시 전달 1", "상황별 1"),
            presentation.filters.map { it.label },
        )
    }

    private fun rule(
        id: String,
        action: RuleActionUi,
    ) = RuleUiModel(
        id = id,
        title = "규칙$id",
        subtitle = "subtitle$id",
        type = RuleTypeUi.KEYWORD,
        action = action,
        enabled = true,
        matchValue = "match$id",
    )
}
