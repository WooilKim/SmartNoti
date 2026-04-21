package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Soft-validates that an override rule's match conditions are a *superset* of
 * its base rule's conditions — i.e. every notification that fires the override
 * could also fire the base, so the override really is a narrower special case.
 *
 * Plan `rules-ux-v2-inbox-restructure` Phase C Task 4. This validator returns
 * a *warning*, not a hard reject: the rule graph is user-authored and the
 * surface-area of condition types (keyword / app / person / schedule) makes a
 * provably-correct superset check complicated. A visible warning in the
 * editor dialog is a kinder signal than blocking save.
 *
 * Policy (phase-C scope):
 *  - Same [RuleTypeUi] required. Mismatched types warn because the classifier
 *    evaluates them on different fields of the notification.
 *  - KEYWORD: every comma-token of the base's `matchValue` must appear in the
 *    override's `matchValue`. Adding extra keywords is the canonical way to
 *    author an override (plan's 결제 / 결제+광고 example).
 *  - Other types (APP / PERSON / SCHEDULE / REPEAT_BUNDLE): strict equality
 *    of `matchValue` is accepted. A richer expression language is out of
 *    scope (plan Non-goals).
 *  - If `overrideOf` points at a base that isn't in `allRules` the override
 *    still persists (classifier handles stale base) but we warn here so the
 *    user knows.
 */
class RuleOverrideSupersetValidator {

    sealed interface Verdict {
        data object Ok : Verdict
        data class Warning(val reason: Reason) : Verdict
    }

    enum class Reason {
        BASE_MISSING,
        TYPE_MISMATCH,
        KEYWORD_NOT_SUPERSET,
        VALUE_MISMATCH,
    }

    fun validate(draft: RuleUiModel, allRules: List<RuleUiModel>): Verdict {
        val baseId = draft.overrideOf ?: return Verdict.Ok
        val base = allRules.firstOrNull { it.id == baseId }
            ?: return Verdict.Warning(Reason.BASE_MISSING)

        if (base.type != draft.type) {
            return Verdict.Warning(Reason.TYPE_MISMATCH)
        }

        return when (draft.type) {
            RuleTypeUi.KEYWORD -> validateKeywordSuperset(draft, base)
            else -> validateStrictEquality(draft, base)
        }
    }

    private fun validateKeywordSuperset(draft: RuleUiModel, base: RuleUiModel): Verdict {
        val draftTokens = tokens(draft.matchValue)
        val baseTokens = tokens(base.matchValue)
        val missing = baseTokens - draftTokens
        return if (missing.isEmpty()) Verdict.Ok else Verdict.Warning(Reason.KEYWORD_NOT_SUPERSET)
    }

    private fun validateStrictEquality(draft: RuleUiModel, base: RuleUiModel): Verdict {
        return if (draft.matchValue == base.matchValue) {
            Verdict.Ok
        } else {
            Verdict.Warning(Reason.VALUE_MISMATCH)
        }
    }

    private fun tokens(raw: String): Set<String> =
        raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
}
