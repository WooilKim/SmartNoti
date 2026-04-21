package com.smartnoti.app.ui.screens.rules

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the Phase C Task 4 superset validator.
 *
 * The plan specifies that an override rule's match conditions should be a
 * superset of the base rule's — i.e. every notification that would trigger the
 * override should also be able to trigger the base, so the override is a
 * strictly more-specific case. The validator emits a *warning* (not a hard
 * rejection) because the rule graph is user-authored and a soft nudge is kinder
 * than blocking save.
 */
class RuleOverrideSupersetValidatorTest {

    private val validator = RuleOverrideSupersetValidator()

    private fun rule(
        id: String,
        type: RuleTypeUi = RuleTypeUi.KEYWORD,
        matchValue: String = "base",
        overrideOf: String? = null,
    ) = RuleUiModel(
        id = id,
        title = id,
        subtitle = "",
        type = type,
        action = RuleActionUi.ALWAYS_PRIORITY,
        enabled = true,
        matchValue = matchValue,
        overrideOf = overrideOf,
    )

    @Test
    fun ok_when_overrideOf_is_null() {
        val base = rule("base", type = RuleTypeUi.KEYWORD, matchValue = "payment")

        val result = validator.validate(draft = base, allRules = listOf(base))

        assertEquals(RuleOverrideSupersetValidator.Verdict.Ok, result)
    }

    @Test
    fun ok_when_override_keyword_contains_base_keyword() {
        // Plan example: 결제 (base) → 결제,광고 (override). Override fires on a
        // strict subset of notifications that the base already matches.
        val base = rule("base", type = RuleTypeUi.KEYWORD, matchValue = "결제")
        val override = rule(
            id = "override",
            type = RuleTypeUi.KEYWORD,
            matchValue = "결제,광고",
            overrideOf = "base",
        )

        val result = validator.validate(draft = override, allRules = listOf(base, override))

        assertEquals(RuleOverrideSupersetValidator.Verdict.Ok, result)
    }

    @Test
    fun warns_when_override_keyword_drops_base_keyword() {
        val base = rule("base", type = RuleTypeUi.KEYWORD, matchValue = "결제")
        val override = rule(
            id = "override",
            type = RuleTypeUi.KEYWORD,
            matchValue = "광고",
            overrideOf = "base",
        )

        val result = validator.validate(draft = override, allRules = listOf(base, override))

        assertEquals(
            RuleOverrideSupersetValidator.Verdict.Warning(
                reason = RuleOverrideSupersetValidator.Reason.KEYWORD_NOT_SUPERSET,
            ),
            result,
        )
    }

    @Test
    fun warns_when_override_type_differs_from_base() {
        // A PERSON-type override of a KEYWORD-type base can't guarantee
        // superset semantics — warn the user.
        val base = rule("base", type = RuleTypeUi.KEYWORD, matchValue = "결제")
        val override = rule(
            id = "override",
            type = RuleTypeUi.PERSON,
            matchValue = "엄마",
            overrideOf = "base",
        )

        val result = validator.validate(draft = override, allRules = listOf(base, override))

        assertEquals(
            RuleOverrideSupersetValidator.Verdict.Warning(
                reason = RuleOverrideSupersetValidator.Reason.TYPE_MISMATCH,
            ),
            result,
        )
    }

    @Test
    fun warns_when_base_is_missing_from_rule_list() {
        val override = rule(
            id = "override",
            type = RuleTypeUi.KEYWORD,
            matchValue = "결제,광고",
            overrideOf = "missing-base",
        )

        val result = validator.validate(draft = override, allRules = listOf(override))

        assertEquals(
            RuleOverrideSupersetValidator.Verdict.Warning(
                reason = RuleOverrideSupersetValidator.Reason.BASE_MISSING,
            ),
            result,
        )
    }

    @Test
    fun ok_when_non_keyword_types_match_and_same_value() {
        // For APP/PERSON/SCHEDULE we take strict equality as the simplest
        // superset proxy — users override by adding extra conditions in a
        // future expression language (out of scope for this plan).
        val base = rule("base", type = RuleTypeUi.APP, matchValue = "com.coupang.mobile")
        val override = rule(
            id = "override",
            type = RuleTypeUi.APP,
            matchValue = "com.coupang.mobile",
            overrideOf = "base",
        )

        val result = validator.validate(draft = override, allRules = listOf(base, override))

        assertEquals(RuleOverrideSupersetValidator.Verdict.Ok, result)
    }
}
