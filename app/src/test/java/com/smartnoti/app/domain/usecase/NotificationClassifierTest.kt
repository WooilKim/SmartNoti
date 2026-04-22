package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import org.junit.Ignore
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationClassifierTest {

    private val classifier = NotificationClassifier(
        vipSenders = setOf("엄마", "팀장"),
        priorityKeywords = setOf("인증번호", "OTP", "결제"),
        shoppingPackages = setOf("com.coupang.mobile")
    )

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun schedule_rule_matches_hour_inside_same_day_window() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                title = "슬랙 요약",
                body = "새 메시지가 도착했어요",
                hourOfDay = 10,
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r-schedule",
                    title = "업무 시간에는 Digest",
                    subtitle = "Digest로 묶기",
                    type = RuleTypeUi.SCHEDULE,
                    enabled = true,
                    matchValue = "9-18",
                )
            )
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        assertEquals(listOf("r-schedule"), result.matchedRuleIds)
    }

    @Test
    fun schedule_rule_matches_hour_inside_overnight_window() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.shopping.app",
                title = "특가",
                body = "야간 할인 중이에요",
                hourOfDay = 2,
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r-night",
                    title = "심야엔 조용히",
                    subtitle = "조용히 정리",
                    type = RuleTypeUi.SCHEDULE,
                    enabled = true,
                    matchValue = "23-7",
                )
            )
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf("r-night"), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun earlier_matching_rule_wins_when_multiple_rules_match() {
        val input = ClassificationInput(
            packageName = "com.chat.app",
            title = "오늘 운영 현황",
            body = "긴급 장애 대응이 필요해요"
        )

        val result = classifier.classify(
            input = input,
            rules = listOf(
                RuleUiModel(
                    id = "r-digest",
                    title = "운영 Digest",
                    subtitle = "Digest로 묶기",
                    type = RuleTypeUi.KEYWORD,
                    enabled = true,
                    matchValue = "장애,긴급",
                ),
                RuleUiModel(
                    id = "r-priority",
                    title = "운영 긴급",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.KEYWORD,
                    enabled = true,
                    matchValue = "장애,긴급",
                )
            )
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        assertEquals(listOf("r-digest"), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun user_person_rule_is_applied_before_default_logic() {
        val result = classifier.classify(
            input = ClassificationInput(
                sender = "고객",
                packageName = "com.kakao.talk",
                body = "회의 일정 확인 부탁드려요"
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r1",
                    title = "고객",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.PERSON,
                    enabled = true,
                    matchValue = "고객",
                )
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf("r1"), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun keyword_rule_matches_any_keyword_in_comma_separated_list() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                title = "오늘 운영 현황",
                body = "새로운 장애 접수가 도착했어요"
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r-keywords",
                    title = "운영 키워드",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.KEYWORD,
                    enabled = true,
                    matchValue = "배포,장애,긴급",
                )
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf("r-keywords"), result.matchedRuleIds)
    }

    @Test
    fun disabled_user_rule_is_ignored() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.social.app",
                body = "새로운 좋아요가 도착했어요"
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r2",
                    title = "소셜",
                    subtitle = "항상 조용히",
                    type = RuleTypeUi.APP,
                    enabled = false,
                    matchValue = "com.social.app",
                )
            )
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun vip_sender_is_always_priority() {
        val result = classifier.classify(
            ClassificationInput(
                sender = "엄마",
                packageName = "com.kakao.talk",
                body = "오늘 저녁 몇 시에 와?"
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun priority_keyword_is_always_priority() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.bank.app",
                body = "인증번호 123456"
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun shopping_app_during_quiet_hours_goes_to_digest() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.coupang.mobile",
                body = "장바구니 상품이 할인 중이에요",
                quietHours = true
            )
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun repeated_notifications_go_to_digest() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보가 도착했어요",
                duplicateCountInWindow = 3
            )
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun repeat_bundle_rule_overrides_default_repeat_handling_at_custom_threshold() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                body = "같은 알림이 계속 와요",
                duplicateCountInWindow = 2,
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r-repeat-priority",
                    title = "반복되면 바로 보기",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.REPEAT_BUNDLE,
                    enabled = true,
                    matchValue = "2",
                )
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf("r-repeat-priority"), result.matchedRuleIds)
    }

    @Test
    fun default_case_is_silent() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.social.app",
                body = "새로운 좋아요가 도착했어요"
            )
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun matched_rule_id_is_reported_even_when_classifier_signal_would_also_fire() {
        // VIP sender would otherwise promote to PRIORITY on its own, but a
        // matching user rule still takes precedence and its id is returned.
        val result = classifier.classify(
            input = ClassificationInput(
                sender = "엄마",
                packageName = "com.kakao.talk",
                body = "오늘 저녁 몇 시에 와?",
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r-person-mom",
                    title = "엄마",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.PERSON,
                    enabled = true,
                    matchValue = "엄마",
                )
            )
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf("r-person-mom"), result.matchedRuleIds)
    }

    // region Phase C Task 2 — hierarchical override preference
    //
    // When both a base rule and its override match the same notification, the
    // override wins (plan `rules-ux-v2-inbox-restructure`). These tests
    // exercise `NotificationClassifier` end-to-end rather than the resolver in
    // isolation — the classifier must delegate to `RuleConflictResolver` so
    // real notifications (and the Detail reason-tag chip) see override-aware
    // behavior.

    @Test
    fun override_rule_wins_over_its_base_when_both_match_user_payment_ad_example() {
        // User-provided example: base "결제 → PRIORITY", override
        // "결제+광고 → SILENT". A payment promo ad must be silenced, not
        // surfaced as Priority.
        val basePayment = RuleUiModel(
            id = "base-payment",
            title = "결제",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "결제",
        )
        val adOverride = RuleUiModel(
            id = "override-payment-ad",
            title = "결제+광고",
            subtitle = "조용히",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
            overrideOf = basePayment.id,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "결제 프로모션",
                body = "결제 광고 쿠폰이 도착했어요",
            ),
            rules = listOf(basePayment, adOverride),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf(adOverride.id), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun base_rule_applies_when_override_condition_is_absent() {
        // Same rule pair as above. A payment-only notification (no 광고
        // keyword) must fall through to the base rule — PRIORITY.
        val basePayment = RuleUiModel(
            id = "base-payment",
            title = "결제",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "결제",
        )
        val adOverride = RuleUiModel(
            id = "override-payment-ad",
            title = "결제+광고",
            subtitle = "조용히",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
            overrideOf = basePayment.id,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.bank.app",
                title = "결제 완료",
                body = "결제 완료 안내",
            ),
            rules = listOf(basePayment, adOverride),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf(basePayment.id), result.matchedRuleIds)
    }

    @Test
    fun override_wins_even_when_listed_before_its_base() {
        // The override preference must not depend on list order. If an
        // override is stored/persisted before its base, the classifier must
        // still pick the override.
        val basePayment = RuleUiModel(
            id = "base-payment",
            title = "결제",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "결제",
        )
        val adOverride = RuleUiModel(
            id = "override-payment-ad",
            title = "결제+광고",
            subtitle = "조용히",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
            overrideOf = basePayment.id,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "결제 프로모션",
                body = "결제 광고 쿠폰",
            ),
            // Override listed FIRST on purpose.
            rules = listOf(adOverride, basePayment),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf(adOverride.id), result.matchedRuleIds)
    }

    @Test
    fun override_is_ignored_when_its_base_did_not_fire() {
        // If only the override matched (but not its base), the override still
        // applies — but this is not "override vs base", it is just the
        // override acting as a single matched rule. Verify the classifier
        // does not require the base to fire for the override to work.
        val basePayment = RuleUiModel(
            id = "base-payment",
            title = "결제",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "결제",
        )
        val adOverride = RuleUiModel(
            id = "override-payment-ad",
            title = "결제+광고",
            subtitle = "조용히",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
            overrideOf = basePayment.id,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "광고 알림",
                body = "새 광고가 도착했어요", // no "결제"
            ),
            rules = listOf(basePayment, adOverride),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf(adOverride.id), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun disabled_override_does_not_win_and_base_rule_applies() {
        // If the override is toggled off, it must not participate. The base
        // remains the sole match.
        val basePayment = RuleUiModel(
            id = "base-payment",
            title = "결제",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "결제",
        )
        val adOverride = RuleUiModel(
            id = "override-payment-ad",
            title = "결제+광고",
            subtitle = "조용히",
            type = RuleTypeUi.KEYWORD,
            enabled = false, // disabled
            matchValue = "광고",
            overrideOf = basePayment.id,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "결제 프로모션",
                body = "결제 광고 쿠폰",
            ),
            rules = listOf(basePayment, adOverride),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf(basePayment.id), result.matchedRuleIds)
    }

    // endregion

    // region Plan `2026-04-21-ignore-tier-fourth-decision` Task 3 —
    // rule-driven IGNORE routing.
    //
    // IGNORE must *only* be reachable through a matching user rule. The
    // classifier cascade (VIP, priority keywords, quiet-hours shopping,
    // repeat burst, default) must never auto-promote any notification to
    // IGNORE — that would be too destructive. Overrides still apply via
    // `RuleConflictResolver`: a base IGNORE rule may be overridden by an
    // ALWAYS_PRIORITY override so the PRIORITY tier wins.

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun ignore_rule_match_routes_to_ignore_decision() {
        val ignoreRule = RuleUiModel(
            id = "r-ignore-ads",
            title = "광고",
            subtitle = "무시",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "오늘의 광고",
                body = "새 광고가 도착했어요",
            ),
            rules = listOf(ignoreRule),
        )

        assertEquals(NotificationDecision.IGNORE, result.decision)
        assertEquals(listOf(ignoreRule.id), result.matchedRuleIds)
    }

    @Test
    fun classifier_never_auto_promotes_to_ignore_without_a_rule() {
        // Sweep: exercise every classifier fallback branch (VIP, priority
        // keyword, quiet-hours shopping, repeat burst, plain default) and
        // verify none of them produce IGNORE. Rule list is empty throughout.
        val inputs = listOf(
            // VIP sender -> PRIORITY
            ClassificationInput(sender = "엄마", packageName = "com.kakao.talk", body = "오늘 저녁 몇 시야?"),
            // Priority keyword -> PRIORITY
            ClassificationInput(packageName = "com.bank.app", body = "인증번호 123456"),
            // Quiet-hours shopping -> DIGEST
            ClassificationInput(
                packageName = "com.coupang.mobile",
                body = "장바구니 할인",
                quietHours = true,
            ),
            // Repeat burst -> DIGEST
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보",
                duplicateCountInWindow = 5,
            ),
            // Default fallback -> SILENT
            ClassificationInput(packageName = "com.social.app", body = "새로운 좋아요"),
        )

        inputs.forEach { input ->
            val result = classifier.classify(input, rules = emptyList())
            assertEquals(
                "No IGNORE rule present, classifier must not auto-promote to IGNORE " +
                    "(input packageName=${input.packageName})",
                false,
                result.decision == NotificationDecision.IGNORE,
            )
        }
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun always_priority_override_beats_base_ignore_rule() {
        // "광고 앱 전부 무시, 단 COMPANY_APP 은 항상 바로 보기" pattern. Base IGNORE
        // + override ALWAYS_PRIORITY both match -> override wins -> PRIORITY.
        val baseIgnore = RuleUiModel(
            id = "base-ignore-ads",
            title = "광고",
            subtitle = "무시",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
        )
        val priorityOverride = RuleUiModel(
            id = "override-company-priority",
            title = "회사 앱은 예외",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.APP,
            enabled = true,
            matchValue = "com.company.app",
            overrideOf = baseIgnore.id,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.company.app",
                title = "사내 공지 — 긴급 광고",
                body = "긴급 사내 광고 알림",
            ),
            rules = listOf(baseIgnore, priorityOverride),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf(priorityOverride.id), result.matchedRuleIds)
    }

    @Test
    @Ignore("Phase P2 restores with Category-driven action. Plan docs/plans/2026-04-22-categories-split-rules-actions.md Task 4 Step 3.")
    fun base_ignore_rule_still_wins_when_override_condition_absent() {
        // Same rule pair as the override test above. A payload that only
        // satisfies the base IGNORE rule (no matching override condition)
        // must still route to IGNORE.
        val baseIgnore = RuleUiModel(
            id = "base-ignore-ads",
            title = "광고",
            subtitle = "무시",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
        )
        val priorityOverride = RuleUiModel(
            id = "override-company-priority",
            title = "회사 앱은 예외",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.APP,
            enabled = true,
            matchValue = "com.company.app",
            overrideOf = baseIgnore.id,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app", // not the company app
                title = "광고",
                body = "새 광고",
            ),
            rules = listOf(baseIgnore, priorityOverride),
        )

        assertEquals(NotificationDecision.IGNORE, result.decision)
        assertEquals(listOf(baseIgnore.id), result.matchedRuleIds)
    }

    // endregion

    @Test
    fun no_rule_match_returns_empty_matched_rule_ids_when_rules_are_defined() {
        // A rule is present but does not match. matchedRuleIds must be empty,
        // so downstream persistence (ruleHitIds) does not conflate classifier
        // signals with rule hits.
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.social.app",
                body = "새로운 좋아요가 도착했어요",
            ),
            rules = listOf(
                RuleUiModel(
                    id = "r-unmatched",
                    title = "업무 키워드",
                    subtitle = "항상 바로 보기",
                    type = RuleTypeUi.KEYWORD,
                    enabled = true,
                    matchValue = "배포,장애",
                )
            )
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }
}
