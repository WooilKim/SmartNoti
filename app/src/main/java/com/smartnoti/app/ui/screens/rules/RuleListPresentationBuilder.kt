package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleUiModel

data class RuleListFilterOption(
    val label: String,
    val action: RuleActionUi?,
)

data class RuleListPresentation(
    val overview: String,
    val filters: List<RuleListFilterOption>,
)

class RuleListPresentationBuilder {
    fun build(rules: List<RuleUiModel>): RuleListPresentation {
        val totalCount = rules.size
        val counts = RuleActionUi.entries.associateWith { action ->
            rules.count { rule -> rule.action == action }
        }

        val overviewSegments = buildList {
            add("전체 ${totalCount}개")
            add("즉시 전달 ${counts[RuleActionUi.ALWAYS_PRIORITY] ?: 0}")
            add("Digest ${counts[RuleActionUi.DIGEST] ?: 0}")
            add("조용히 ${counts[RuleActionUi.SILENT] ?: 0}")
            if ((counts[RuleActionUi.CONTEXTUAL] ?: 0) > 0) {
                add("상황별 ${counts[RuleActionUi.CONTEXTUAL] ?: 0}")
            }
        }

        val filters = buildList {
            add(RuleListFilterOption(label = "전체 $totalCount", action = null))
            addFilterIfPresent(RuleActionUi.ALWAYS_PRIORITY, counts, "즉시 전달")
            addFilterIfPresent(RuleActionUi.DIGEST, counts, "Digest")
            addFilterIfPresent(RuleActionUi.SILENT, counts, "조용히")
            addFilterIfPresent(RuleActionUi.CONTEXTUAL, counts, "상황별")
        }

        return RuleListPresentation(
            overview = overviewSegments.joinToString(" · "),
            filters = filters,
        )
    }

    private fun MutableList<RuleListFilterOption>.addFilterIfPresent(
        action: RuleActionUi,
        counts: Map<RuleActionUi, Int>,
        label: String,
    ) {
        val count = counts[action] ?: 0
        if (count > 0) {
            add(RuleListFilterOption(label = "$label $count", action = action))
        }
    }
}
