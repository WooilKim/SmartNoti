package com.smartnoti.app.data.rules

import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Validates override-chain integrity when upserting a [RuleUiModel] into the
 * rules store.
 *
 * Plan `rules-ux-v2-inbox-restructure` Phase C Task 1 requires the repository
 * to reject circular override references (e.g. A → B → A). This class is a pure
 * function with no Android / DataStore dependency so the policy is unit-tested
 * in isolation.
 */
class RuleOverrideValidator {

    sealed interface Result {
        data class Accepted(val rule: RuleUiModel) : Result
        data class Rejected(val reason: Reason) : Result
    }

    enum class Reason {
        SELF_REFERENCE,
        CYCLE_DETECTED,
    }

    /**
     * Decide whether [incoming] may be persisted given the currently stored
     * [existing] rules. Returns [Result.Accepted] if the resulting override
     * graph stays acyclic, otherwise [Result.Rejected] with the specific
     * [Reason].
     *
     * A rule whose [RuleUiModel.overrideOf] points at an id that isn't in
     * [existing] is still accepted — the base might simply not be persisted
     * yet, and the classifier treats such rules as plain base-tier rules.
     */
    fun validate(incoming: RuleUiModel, existing: List<RuleUiModel>): Result {
        val overrideOf = incoming.overrideOf ?: return Result.Accepted(incoming)

        if (overrideOf == incoming.id) {
            return Result.Rejected(Reason.SELF_REFERENCE)
        }

        // Build the candidate graph as it would look *after* the upsert: the
        // incoming rule replaces any existing row with the same id.
        val candidate = existing.filterNot { it.id == incoming.id } + incoming
        val byId = candidate.associateBy { it.id }

        val visited = mutableSetOf<String>()
        var current: String? = incoming.id
        while (current != null) {
            if (current in visited) {
                return Result.Rejected(Reason.CYCLE_DETECTED)
            }
            visited += current
            current = byId[current]?.overrideOf
        }
        return Result.Accepted(incoming)
    }
}
