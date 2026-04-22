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
        val rules = listOf(rule("1"), rule("2"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.ALWAYS_PRIORITY,
            "2" to RuleActionUi.DIGEST,
        )

        val result = applicator.apply(rules, action = null, ruleActions = ruleActions)

        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    @Test
    fun apply_returns_only_rules_for_selected_action() {
        val rules = listOf(rule("1"), rule("2"), rule("3"), rule("4"))
        val ruleActions = mapOf(
            "1" to RuleActionUi.ALWAYS_PRIORITY,
            "2" to RuleActionUi.DIGEST,
            "3" to RuleActionUi.DIGEST,
            "4" to RuleActionUi.SILENT,
        )

        val result = applicator.apply(
            rules,
            action = RuleActionUi.DIGEST,
            ruleActions = ruleActions,
        )

        assertEquals(listOf("2", "3"), result.map { it.id })
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
