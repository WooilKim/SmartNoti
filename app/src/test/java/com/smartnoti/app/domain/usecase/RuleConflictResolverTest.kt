package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [RuleConflictResolver].
 *
 * See plan `rules-ux-v2-inbox-restructure` Phase C Task 1. When multiple rules
 * match the same notification, the resolver must:
 *  - Prefer an override rule over its base when both matched.
 *  - Break ties among equally-specific candidates by rule order (earlier wins).
 *  - Fall back to the single matched rule when no override is involved.
 */
class RuleConflictResolverTest {

    private val resolver = RuleConflictResolver()

    @Test
    fun no_matched_rules_returns_null() {
        val resolved = resolver.resolve(
            matched = emptyList(),
            allRules = listOf(basePaymentRule()),
        )

        assertNull(resolved)
    }

    @Test
    fun single_matched_rule_is_returned_as_is() {
        val base = basePaymentRule()

        val resolved = resolver.resolve(
            matched = listOf(base),
            allRules = listOf(base),
        )

        assertEquals(base.id, resolved?.id)
    }

    @Test
    fun override_wins_over_its_base_when_both_matched() {
        // User example: 결제 → PRIORITY is the base. 결제 AND 광고 → SILENT is
        // the override. When both fired, the override must win so the payment
        // promo ad is silenced rather than promoted.
        val base = basePaymentRule()
        val override = adPaymentOverrideRule(baseId = base.id)

        val resolved = resolver.resolve(
            matched = listOf(base, override),
            allRules = listOf(base, override),
        )

        assertEquals(override.id, resolved?.id)
    }

    @Test
    fun override_wins_even_if_listed_before_its_base() {
        // Order within allRules should not undo the override preference; the
        // override is still more specific. This protects against accidental
        // reordering bugs where an override got moved above its base.
        val base = basePaymentRule()
        val override = adPaymentOverrideRule(baseId = base.id)

        val resolved = resolver.resolve(
            matched = listOf(base, override),
            allRules = listOf(override, base),
        )

        assertEquals(override.id, resolved?.id)
    }

    @Test
    fun base_is_returned_when_its_override_did_not_match() {
        val base = basePaymentRule()
        val override = adPaymentOverrideRule(baseId = base.id)

        val resolved = resolver.resolve(
            matched = listOf(base),
            allRules = listOf(base, override),
        )

        assertEquals(base.id, resolved?.id)
    }

    @Test
    fun tied_same_tier_rules_break_by_order_in_allRules() {
        // Two equally-specific keyword rules fired. Without override, the one
        // that appears earlier in allRules wins (priority = position).
        val earlier = RuleUiModel(
            id = "kw-earlier",
            title = "운영 Digest",
            subtitle = "Digest로 묶기",
            type = RuleTypeUi.KEYWORD,
            action = RuleActionUi.DIGEST,
            enabled = true,
            matchValue = "장애",
        )
        val later = RuleUiModel(
            id = "kw-later",
            title = "운영 Priority",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            action = RuleActionUi.ALWAYS_PRIORITY,
            enabled = true,
            matchValue = "장애",
        )

        val resolved = resolver.resolve(
            matched = listOf(later, earlier),
            allRules = listOf(earlier, later),
        )

        assertEquals(earlier.id, resolved?.id)
    }

    @Test
    fun override_pointing_at_nonexistent_base_is_still_usable() {
        // If the base rule got deleted, the override still lives on by id. The
        // resolver should not crash and should return the override when only it
        // matched.
        val orphanedOverride = adPaymentOverrideRule(baseId = "deleted-base-id")

        val resolved = resolver.resolve(
            matched = listOf(orphanedOverride),
            allRules = listOf(orphanedOverride),
        )

        assertEquals(orphanedOverride.id, resolved?.id)
    }

    @Test
    fun multiple_overrides_of_same_base_tiebreak_by_order() {
        val base = basePaymentRule()
        val overrideA = RuleUiModel(
            id = "override-a",
            title = "결제+광고 A",
            subtitle = "조용히",
            type = RuleTypeUi.KEYWORD,
            action = RuleActionUi.SILENT,
            enabled = true,
            matchValue = "광고",
            overrideOf = base.id,
        )
        val overrideB = RuleUiModel(
            id = "override-b",
            title = "결제+광고 B",
            subtitle = "Digest",
            type = RuleTypeUi.KEYWORD,
            action = RuleActionUi.DIGEST,
            enabled = true,
            matchValue = "프로모션",
            overrideOf = base.id,
        )

        val resolved = resolver.resolve(
            matched = listOf(base, overrideB, overrideA),
            allRules = listOf(base, overrideA, overrideB),
        )

        assertEquals(overrideA.id, resolved?.id)
    }

    private fun basePaymentRule(): RuleUiModel = RuleUiModel(
        id = "base-payment",
        title = "결제",
        subtitle = "항상 바로 보기",
        type = RuleTypeUi.KEYWORD,
        action = RuleActionUi.ALWAYS_PRIORITY,
        enabled = true,
        matchValue = "결제",
    )

    private fun adPaymentOverrideRule(baseId: String): RuleUiModel = RuleUiModel(
        id = "override-payment-ad",
        title = "결제+광고",
        subtitle = "조용히",
        type = RuleTypeUi.KEYWORD,
        action = RuleActionUi.SILENT,
        enabled = true,
        matchValue = "광고",
        overrideOf = baseId,
    )
}
