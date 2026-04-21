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
    fun build_emits_ignore_group_after_silent_and_contextual() {
        // Plan 2026-04-21-ignore-tier-fourth-decision Task 5 step 2 — IGNORE
        // surfaces at the bottom of the tier stack because it is the loudest
        // (destructive) downgrade of a notification and should not visually
        // dominate the editor.
        val groups = builder.build(
            listOf(
                rule("1", RuleActionUi.ALWAYS_PRIORITY),
                rule("2", RuleActionUi.DIGEST),
                rule("3", RuleActionUi.SILENT),
                rule("4", RuleActionUi.IGNORE),
                rule("5", RuleActionUi.IGNORE),
            )
        )

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
        val groups = builder.build(
            listOf(
                rule("1", RuleActionUi.ALWAYS_PRIORITY),
                rule("2", RuleActionUi.DIGEST),
            )
        )

        assertEquals(
            listOf("즉시 전달", "Digest"),
            groups.map { it.title },
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
