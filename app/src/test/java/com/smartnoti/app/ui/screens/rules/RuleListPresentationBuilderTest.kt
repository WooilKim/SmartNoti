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
