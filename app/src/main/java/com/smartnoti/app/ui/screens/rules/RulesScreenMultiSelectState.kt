package com.smartnoti.app.ui.screens.rules

/**
 * Pure-Kotlin holder for the RulesScreen multi-select interaction (plan
 * `docs/plans/2026-04-26-rules-bulk-assign-unassigned.md` Tasks 3 + 4).
 *
 * The bulk-assign flow lives entirely on the 미분류 sub-sections of
 * [RulesScreen]. Long-press on a row enters selection mode for that
 * sub-section ([Bucket]); subsequent taps within the same bucket toggle
 * membership; cross-bucket interactions are intentionally ignored to keep
 * "작업 필요" and "보류" intents from bleeding into the same bulk action.
 *
 * Kept Compose-free so the state machine stays unit-test friendly — the
 * RulesScreen wires this via `remember { mutableStateOf(...) }` and replaces
 * the value on each transition.
 */
data class RulesScreenMultiSelectState(
    val activeBucket: Bucket? = null,
    val selectedRuleIds: Set<String> = emptySet(),
) {
    enum class Bucket {
        /** "작업 필요" sub-section — `draft = true`. */
        ACTION_NEEDED,

        /** "보류" sub-section — `draft = false`. */
        PARKED,
    }

    /**
     * Enter selection mode in [bucket] seeded with [seedRuleId]. If selection
     * mode was already active in another bucket the bucket is replaced (the
     * call site usually guards this with `activeBucket == null`, but the
     * state itself stays predictable either way).
     */
    fun enterSelection(bucket: Bucket, seedRuleId: String): RulesScreenMultiSelectState =
        copy(activeBucket = bucket, selectedRuleIds = setOf(seedRuleId))

    /**
     * Toggle [ruleId] in the selection set. Ignored when not in selection
     * mode or when [bucket] differs from [activeBucket]. Removing the last
     * selected ruleId auto-cancels selection mode (returns the initial
     * state) so the chrome disappears without an extra explicit cancel —
     * this is the simplification documented in the plan's Risks section.
     */
    fun toggle(bucket: Bucket, ruleId: String): RulesScreenMultiSelectState {
        if (activeBucket == null || activeBucket != bucket) return this
        val nextSelected = if (ruleId in selectedRuleIds) {
            selectedRuleIds - ruleId
        } else {
            selectedRuleIds + ruleId
        }
        return if (nextSelected.isEmpty()) {
            RulesScreenMultiSelectState()
        } else {
            copy(selectedRuleIds = nextSelected)
        }
    }

    /** Reset to the initial inactive state. Used by the explicit "취소" CTA. */
    fun cancel(): RulesScreenMultiSelectState = RulesScreenMultiSelectState()

    /** Convenience for per-row predicates that gate tap-vs-toggle dispatch. */
    fun isInSelectionMode(bucket: Bucket): Boolean = activeBucket == bucket
}
