package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Pure helper that splits the unassigned-rule bucket into two intent-aware
 * sub-buckets.
 *
 * Plan `docs/plans/2026-04-26-rule-explicit-draft-flag.md` Task 5. Runs on
 * top of [UnassignedRulesDetector] (same input semantics) and partitions
 * its output by the new [RuleUiModel.draft] flag:
 *
 *  - [Partition.actionNeeded] — rules with `draft == true`. The user has
 *    not yet expressed an intent for this rule (typically a freshly saved
 *    rule whose post-save sheet was dismissed without a Category pick).
 *    RulesScreen renders these in the louder "작업 필요" sub-bucket so the
 *    user can spot the dormant draft on next visit.
 *  - [Partition.parked] — rules with `draft == false`. The user explicitly
 *    chose "분류 없이 보류" or the row predates the draft flag (legacy
 *    7-column DataStore fallback). RulesScreen renders these in the
 *    quieter "보류" sub-bucket so they don't shout on every mount.
 *
 * Output preserves input rule order inside each bucket so list rendering
 * stays deterministic across recompositions.
 */
class UnassignedRulesPartitioner {

    private val detector = UnassignedRulesDetector()

    fun partition(
        rules: List<RuleUiModel>,
        categories: List<Category>,
    ): Partition {
        val unassigned = detector.detect(rules, categories)
        if (unassigned.isEmpty()) return Partition(emptyList(), emptyList())
        val actionNeeded = unassigned.filter { it.draft }
        val parked = unassigned.filterNot { it.draft }
        return Partition(actionNeeded = actionNeeded, parked = parked)
    }

    data class Partition(
        val actionNeeded: List<RuleUiModel>,
        val parked: List<RuleUiModel>,
    )
}
