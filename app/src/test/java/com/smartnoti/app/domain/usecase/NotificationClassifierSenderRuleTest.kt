package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 1 — RED contract for the new [RuleTypeUi.SENDER] matcher.
 *
 * Anchor case from issue #526: Microsoft Teams 1:1 DM ships
 * `title = "김동대(Special Recon)"` with an empty / app-name `sender`. The
 * existing PERSON matcher (exact `sender` equality) cannot route it to
 * PRIORITY. SENDER closes the gap by doing a substring + ignoreCase match
 * against the notification **title** so the user can promote "이 발신자"
 * to PRIORITY in one tap.
 *
 * Each fixture wires a single SENDER rule into a PRIORITY [Category] and
 * asserts the resolver-driven decision. Tests deliberately avoid touching
 * the legacy heuristic chain (vipSenders / priorityKeywords) so a future
 * refactor of those defaults cannot mask a regression in the SENDER branch.
 */
class NotificationClassifierSenderRuleTest {

    private val classifier = NotificationClassifier(
        vipSenders = emptySet(),
        priorityKeywords = emptySet(),
        shoppingPackages = emptySet(),
    )

    @Test
    fun senderRuleMatchesTitleSubstringCaseInsensitive() {
        val rule = senderRule(matchValue = "김동대")
        val category = priorityCategory(ruleIds = listOf(rule.id))

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.sds.teams",
                title = "김동대(Special Recon)",
                body = "회의 5분 뒤에 시작합니다",
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf(rule.id), result.matchedRuleIds)
    }

    @Test
    fun senderRuleDoesNotMatchWhenTitleEmpty() {
        val rule = senderRule(matchValue = "김동대")
        val category = priorityCategory(ruleIds = listOf(rule.id))

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.sds.teams",
                title = "",
                body = "본문만 있는 알림",
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        // No rule matches → fall through the legacy heuristic chain. With
        // empty vipSenders / priorityKeywords / shoppingPackages and zero
        // duplicates the default lands on SILENT.
        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun senderRuleDoesNotMatchWhenMatchValueBlank() {
        val rule = senderRule(matchValue = "")
        val category = priorityCategory(ruleIds = listOf(rule.id))

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.sds.teams",
                title = "임의 알림 제목",
                body = "본문",
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        // A blank matchValue would otherwise match every notification —
        // the SENDER branch must guard against that pathological case so
        // a freshly-created (matchValue not yet typed) draft rule cannot
        // promote every alert to PRIORITY.
        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun senderRuleDistinctFromPersonRule() {
        // Same matchValue, two rule types. The PERSON variant must NOT match
        // a Teams-style notification (sender = null, name only in title);
        // the SENDER variant must match.
        val personRule = RuleUiModel(
            id = "rule-person-홍길동",
            title = "홍길동 (PERSON)",
            subtitle = "발신자 정확 일치",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "홍길동",
        )
        val senderRule = senderRule(id = "rule-sender-홍길동", matchValue = "홍길동")
        val personCategory = priorityCategory(
            id = "cat-person",
            ruleIds = listOf(personRule.id),
        )
        val senderCategory = priorityCategory(
            id = "cat-sender",
            ruleIds = listOf(senderRule.id),
        )
        val input = ClassificationInput(
            packageName = "com.sds.teams",
            title = "홍길동(Team)",
            body = "메시지",
            // sender intentionally null — Teams DM does not populate it.
        )

        val personOnly = classifier.classify(
            input = input,
            rules = listOf(personRule),
            categories = listOf(personCategory),
        )
        assertEquals(
            "PERSON rule must not fire when sender is null even if title contains the name",
            NotificationDecision.SILENT,
            personOnly.decision,
        )
        assertEquals(emptyList<String>(), personOnly.matchedRuleIds)

        val senderOnly = classifier.classify(
            input = input,
            rules = listOf(senderRule),
            categories = listOf(senderCategory),
        )
        assertEquals(
            "SENDER rule must fire on title substring match",
            NotificationDecision.PRIORITY,
            senderOnly.decision,
        )
        assertEquals(listOf(senderRule.id), senderOnly.matchedRuleIds)
    }

    @Test
    fun senderRuleCaseInsensitiveAscii() {
        val rule = senderRule(id = "rule-sender-wooil", matchValue = "wooil kim")
        val category = priorityCategory(ruleIds = listOf(rule.id))

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.slack",
                title = "WOOIL KIM(Eng)",
                body = "PR review request",
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf(rule.id), result.matchedRuleIds)
    }

    private fun senderRule(
        id: String = "rule-sender-default",
        matchValue: String,
    ): RuleUiModel = RuleUiModel(
        id = id,
        title = "$matchValue 발신자",
        subtitle = "발신자 이름 매치",
        type = RuleTypeUi.SENDER,
        enabled = true,
        matchValue = matchValue,
    )

    private fun priorityCategory(
        id: String = "cat-priority",
        ruleIds: List<String>,
    ): Category = Category(
        id = id,
        name = id,
        appPackageName = null,
        ruleIds = ruleIds,
        action = CategoryAction.PRIORITY,
        order = 0,
    )
}
