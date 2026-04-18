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
        val groups = builder.build(
            listOf(
                rule("1", RuleActionUi.SILENT),
                rule("2", RuleActionUi.DIGEST),
                rule("3", RuleActionUi.ALWAYS_PRIORITY),
                rule("4", RuleActionUi.DIGEST),
            )
        )

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
    fun build_uses_count_based_section_subtitles() {
        val groups = builder.build(
            listOf(
                rule("1", RuleActionUi.CONTEXTUAL),
                rule("2", RuleActionUi.CONTEXTUAL),
            )
        )

        assertEquals("상황별", groups.single().title)
        assertEquals("규칙 2개", groups.single().subtitle)
    }

    private fun rule(id: String, action: RuleActionUi) = RuleUiModel(
        id = id,
        title = "규칙$id",
        subtitle = "subtitle$id",
        type = RuleTypeUi.KEYWORD,
        action = action,
        enabled = true,
        matchValue = "match$id",
    )
}
