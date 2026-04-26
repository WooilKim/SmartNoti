package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pin the "rule with no owning Category is no-op (heuristic-only)" contract
 * before plan `docs/plans/2026-04-24-rule-editor-remove-action-dropdown.md`
 * removes the editor's silent 1:1 Category auto-upsert. After that change a
 * freshly saved Rule has no owning Category until the user picks one in the
 * post-save sheet — and the classifier must not crash or invent a SILENT
 * routing decision under the user's name. The current behavior is "fall back
 * to the heuristic chain (VIP / priority keyword / quiet hours / duplicate
 * burst), defaulting to SILENT only when nothing else fires" — i.e. the rule
 * is effectively invisible to user-driven routing.
 */
class RuleWithoutCategoryNoOpTest {

    private val classifier = NotificationClassifier(
        vipSenders = emptySet(),
        priorityKeywords = emptySet(),
        shoppingPackages = emptySet(),
    )

    @Test
    fun matched_rule_with_empty_categories_does_not_promote_to_priority_or_digest() {
        val rule = RuleUiModel(
            id = "keyword:인증번호",
            title = "인증번호",
            subtitle = "",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "인증번호",
        )
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.bank.app",
                title = "은행",
                body = "인증번호 123456",
            ),
            rules = listOf(rule),
            categories = emptyList(),
        )

        // Falls through to SILENT — the rule fired so its id is reported, but
        // no Category owns it so the action defaults to SILENT (no PRIORITY /
        // DIGEST / IGNORE escalation under the user's name).
        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf("keyword:인증번호"), result.matchedRuleIds)
    }

    @Test
    fun unmatched_rule_with_empty_categories_falls_through_heuristic_chain_to_silent() {
        val rule = RuleUiModel(
            id = "person:엄마",
            title = "엄마",
            subtitle = "",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "엄마",
        )
        val result = classifier.classify(
            input = ClassificationInput(
                sender = "광고",
                packageName = "com.someapp",
                title = "프로모션",
                body = "할인",
            ),
            rules = listOf(rule),
            categories = emptyList(),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun draft_true_rule_with_empty_categories_still_falls_through_to_silent() {
        // Plan `2026-04-26-rule-explicit-draft-flag` Task 1 step 3 — the new
        // `draft` flag is a pure UI/UX hint. The classifier contract does
        // NOT change: a rule whose match fires but whose owning Category is
        // missing falls through to SILENT regardless of `draft` value.
        val rule = RuleUiModel(
            id = "keyword:인증번호",
            title = "인증번호",
            subtitle = "",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "인증번호",
            draft = true,
        )
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.bank.app",
                title = "은행",
                body = "인증번호 123456",
            ),
            rules = listOf(rule),
            categories = emptyList(),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf("keyword:인증번호"), result.matchedRuleIds)
    }

    @Test
    fun draft_false_parked_rule_with_empty_categories_still_falls_through_to_silent() {
        // Mirror of the draft=true case for the "보류" sub-bucket. A user who
        // explicitly parked a rule expects no behavior change at the
        // classifier — only the RulesScreen rendering should differ.
        val rule = RuleUiModel(
            id = "keyword:인증번호",
            title = "인증번호",
            subtitle = "",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "인증번호",
            draft = false,
        )
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.bank.app",
                title = "은행",
                body = "인증번호 123456",
            ),
            rules = listOf(rule),
            categories = emptyList(),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf("keyword:인증번호"), result.matchedRuleIds)
    }
}
