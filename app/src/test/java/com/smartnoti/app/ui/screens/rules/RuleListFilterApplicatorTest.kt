package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleListFilterApplicatorTest {

    private val applicator = RuleListFilterApplicator()

    @Test
    fun apply_returns_all_rules_when_filter_is_null() {
        val rules = listOf(
            rule("1", RuleActionUi.ALWAYS_PRIORITY),
            rule("2", RuleActionUi.DIGEST),
        )

        val result = applicator.apply(rules, action = null)

        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun apply_returns_only_rules_for_selected_action() {
        val rules = listOf(
            rule("1", RuleActionUi.ALWAYS_PRIORITY),
            rule("2", RuleActionUi.DIGEST),
            rule("3", RuleActionUi.DIGEST),
            rule("4", RuleActionUi.SILENT),
        )

        val result = applicator.apply(rules, action = RuleActionUi.DIGEST)

        assertEquals(listOf("2", "3"), result.map { it.id })
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
