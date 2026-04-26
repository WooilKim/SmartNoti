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
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P2
 * Tasks 5+6+7 re-activate these tests. The classifier:
 *   - matches all enabled rules,
 *   - lifts every matched rule to any owning Category,
 *   - delegates the action pick to [CategoryConflictResolver] (which applies
 *     the rule-type specificity ladder APP > KEYWORD > PERSON > SCHEDULE >
 *     REPEAT_BUNDLE, with an app-pin bonus, and the user-chosen drag order
 *     as the tie-break).
 *
 * Each test that used to depend on `Rule.action` now wires the rule into a
 * Category whose action matches the original expectation.
 */
class NotificationClassifierTest {

    private val classifier = NotificationClassifier(
        vipSenders = setOf("엄마", "팀장"),
        priorityKeywords = setOf("인증번호", "OTP", "결제"),
        shoppingPackages = setOf("com.coupang.mobile")
    )

    @Test
    fun schedule_rule_matches_hour_inside_same_day_window() {
        val rule = RuleUiModel(
            id = "r-schedule",
            title = "업무 시간에는 Digest",
            subtitle = "Digest로 묶기",
            type = RuleTypeUi.SCHEDULE,
            enabled = true,
            matchValue = "9-18",
        )
        val category = category(
            id = "cat-schedule",
            action = CategoryAction.DIGEST,
            ruleIds = listOf(rule.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                title = "슬랙 요약",
                body = "새 메시지가 도착했어요",
                hourOfDay = 10,
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        assertEquals(listOf("r-schedule"), result.matchedRuleIds)
    }

    @Test
    fun schedule_rule_matches_hour_inside_overnight_window() {
        val rule = RuleUiModel(
            id = "r-night",
            title = "심야엔 조용히",
            subtitle = "조용히 정리",
            type = RuleTypeUi.SCHEDULE,
            enabled = true,
            matchValue = "23-7",
        )
        val category = category(
            id = "cat-night",
            action = CategoryAction.SILENT,
            ruleIds = listOf(rule.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.shopping.app",
                title = "특가",
                body = "야간 할인 중이에요",
                hourOfDay = 2,
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf("r-night"), result.matchedRuleIds)
    }

    @Test
    fun earlier_category_wins_when_multiple_rules_match_same_type() {
        // Two equal-specificity (KEYWORD) rules match; each belongs to its
        // own Category. The Category with the lower `order` (dragged higher
        // in the 분류 tab) wins via the Phase P2 Task 6 tie-break.
        val ruleDigest = RuleUiModel(
            id = "r-digest",
            title = "운영 Digest",
            subtitle = "Digest로 묶기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "장애,긴급",
        )
        val rulePriority = RuleUiModel(
            id = "r-priority",
            title = "운영 긴급",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "장애,긴급",
        )
        val digestCategory = category(
            id = "cat-digest",
            action = CategoryAction.DIGEST,
            ruleIds = listOf(ruleDigest.id),
            order = 0, // user dragged this one to the top
        )
        val priorityCategory = category(
            id = "cat-priority",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(rulePriority.id),
            order = 1,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                title = "오늘 운영 현황",
                body = "긴급 장애 대응이 필요해요"
            ),
            rules = listOf(ruleDigest, rulePriority),
            categories = listOf(digestCategory, priorityCategory),
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        // Both rules matched — the classifier reports every matched rule id
        // so downstream surfaces (Detail reason tags) can still explain all
        // the rules that fired.
        assertEquals(setOf("r-digest", "r-priority"), result.matchedRuleIds.toSet())
    }

    @Test
    fun user_person_rule_is_applied_before_default_logic() {
        val rule = RuleUiModel(
            id = "r1",
            title = "고객",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "고객",
        )
        val category = category(
            id = "cat-person",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(rule.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                sender = "고객",
                packageName = "com.kakao.talk",
                body = "회의 일정 확인 부탁드려요"
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf("r1"), result.matchedRuleIds)
    }

    @Test
    fun keyword_rule_matches_any_keyword_in_comma_separated_list() {
        val rule = RuleUiModel(
            id = "r-keywords",
            title = "운영 키워드",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "배포,장애,긴급",
        )
        val category = category(
            id = "cat-keywords",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(rule.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                title = "오늘 운영 현황",
                body = "새로운 장애 접수가 도착했어요"
            ),
            rules = listOf(rule),
            categories = listOf(category),
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

    // region Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 3
    //
    // Pin that the classifier's quiet-hours branch fires against a *dynamic*
    // `shoppingPackages` set passed per-classify (Architecture (B) — mirrors
    // the per-input `duplicateThreshold` pattern). The constructor-provided
    // set still applies when no override is supplied so legacy call sites
    // and the unit-test fixture above stay on the historical default.

    @Test
    fun dynamic_shopping_packages_override_promotes_baemin_to_digest_during_quiet_hours() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.baemin",
                body = "오늘의 쿠폰",
                quietHours = true,
            ),
            shoppingPackagesOverride = setOf("com.baemin"),
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun dynamic_shopping_packages_override_excludes_coupang_when_set_replaces_default() {
        // Override = {com.baemin}. Coupang is no longer in the active set so
        // the quiet-hours branch must NOT fire — even though the constructor
        // fixture still lists Coupang, the override fully replaces it.
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.coupang.mobile",
                body = "장바구니 상품 할인",
                quietHours = true,
            ),
            shoppingPackagesOverride = setOf("com.baemin"),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    @Test
    fun empty_shopping_packages_override_disables_quiet_hours_branch_for_any_package() {
        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.coupang.mobile",
                body = "장바구니 상품 할인",
                quietHours = true,
            ),
            shoppingPackagesOverride = emptySet(),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
    }

    @Test
    fun multi_package_shopping_override_promotes_each_member_during_quiet_hours() {
        val override = setOf("com.coupang.mobile", "com.baemin")

        val coupang = classifier.classify(
            input = ClassificationInput(
                packageName = "com.coupang.mobile",
                body = "쿠팡 할인",
                quietHours = true,
            ),
            shoppingPackagesOverride = override,
        )
        val baemin = classifier.classify(
            input = ClassificationInput(
                packageName = "com.baemin",
                body = "배민 쿠폰",
                quietHours = true,
            ),
            shoppingPackagesOverride = override,
        )

        assertEquals(NotificationDecision.DIGEST, coupang.decision)
        assertEquals(NotificationDecision.DIGEST, baemin.decision)
    }

    // endregion

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

    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 1.
     *
     * The base heuristic compares against `input.duplicateThreshold` instead of
     * a hard-coded 3. With the user-tunable threshold lowered to 2, the second
     * duplicate already trips DIGEST (the user is asking for "더 자주 묶기").
     */
    @Test
    fun custom_threshold_two_promotes_to_digest_at_count_two() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보가 도착했어요",
                duplicateCountInWindow = 2,
                duplicateThreshold = 2,
            )
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 1.
     *
     * Same input as `custom_threshold_two_…` but with the user threshold lifted
     * to 5 — "거의 묶지 말기". `duplicateCountInWindow == 2` no longer trips
     * DIGEST and the cascade continues to the SILENT default.
     */
    @Test
    fun custom_threshold_five_falls_through_to_silent_at_count_two() {
        val result = classifier.classify(
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보가 도착했어요",
                duplicateCountInWindow = 2,
                duplicateThreshold = 5,
            )
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(emptyList<String>(), result.matchedRuleIds)
    }

    /**
     * Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 1.
     *
     * Default threshold (3) preserves the historical behavior at exactly the
     * boundary — pinned so a future refactor cannot silently flip the off-by-one.
     */
    @Test
    fun default_threshold_three_preserves_historical_boundary() {
        val belowThreshold = classifier.classify(
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보가 도착했어요",
                duplicateCountInWindow = 2,
            )
        )
        assertEquals(NotificationDecision.SILENT, belowThreshold.decision)

        val atThreshold = classifier.classify(
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보가 도착했어요",
                duplicateCountInWindow = 3,
            )
        )
        assertEquals(NotificationDecision.DIGEST, atThreshold.decision)
    }

    @Test
    fun repeat_bundle_rule_overrides_default_repeat_handling_at_custom_threshold() {
        val rule = RuleUiModel(
            id = "r-repeat-priority",
            title = "반복되면 바로 보기",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.REPEAT_BUNDLE,
            enabled = true,
            matchValue = "2",
        )
        val category = category(
            id = "cat-repeat",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(rule.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                body = "같은 알림이 계속 와요",
                duplicateCountInWindow = 2,
            ),
            rules = listOf(rule),
            categories = listOf(category),
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
    fun matched_rule_id_is_reported_even_when_classifier_signal_would_also_fire() {
        // VIP sender would otherwise promote to PRIORITY on its own, but a
        // matching user rule still takes precedence and its id is returned.
        val rule = RuleUiModel(
            id = "r-person-mom",
            title = "엄마",
            subtitle = "항상 바로 보기",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "엄마",
        )
        val category = category(
            id = "cat-mom",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(rule.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                sender = "엄마",
                packageName = "com.kakao.talk",
                body = "오늘 저녁 몇 시에 와?",
            ),
            rules = listOf(rule),
            categories = listOf(category),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf("r-person-mom"), result.matchedRuleIds)
    }

    // region Phase C Task 2 — hierarchical override preference
    //
    // When both a base rule and its override match the same notification, the
    // override wins. These tests now lift the Category graph into the setup —
    // the override rule belongs to the Category whose action the user wants
    // to win.

    @Test
    fun override_rule_wins_over_its_base_when_both_match_user_payment_ad_example() {
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
        // Each rule lives in its own Category with the expected action. The
        // override rule must win via the RuleConflictResolver override
        // preference, so the SILENT Category is selected.
        val priorityCategory = category(
            id = "cat-payment-priority",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(basePayment.id),
            order = 0,
        )
        val silentCategory = category(
            id = "cat-payment-ad-silent",
            action = CategoryAction.SILENT,
            ruleIds = listOf(adOverride.id),
            order = 1,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "결제 프로모션",
                body = "결제 광고 쿠폰이 도착했어요",
            ),
            rules = listOf(basePayment, adOverride),
            categories = listOf(priorityCategory, silentCategory),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        // Override shadows its base (Phase C override-vs-base contract); only
        // the override id is surfaced so Detail reason chips do not confuse
        // the user with a rule that was overridden.
        assertEquals(listOf(adOverride.id), result.matchedRuleIds)
    }

    @Test
    fun base_rule_applies_when_override_condition_is_absent() {
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
        val priorityCategory = category(
            id = "cat-payment-priority",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(basePayment.id),
        )
        val silentCategory = category(
            id = "cat-payment-ad-silent",
            action = CategoryAction.SILENT,
            ruleIds = listOf(adOverride.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.bank.app",
                title = "결제 완료",
                body = "결제 완료 안내",
            ),
            rules = listOf(basePayment, adOverride),
            categories = listOf(priorityCategory, silentCategory),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf(basePayment.id), result.matchedRuleIds)
    }

    @Test
    fun override_wins_even_when_listed_before_its_base() {
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
        val priorityCategory = category(
            id = "cat-payment-priority",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(basePayment.id),
        )
        val silentCategory = category(
            id = "cat-payment-ad-silent",
            action = CategoryAction.SILENT,
            ruleIds = listOf(adOverride.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "결제 프로모션",
                body = "결제 광고 쿠폰",
            ),
            rules = listOf(adOverride, basePayment),
            categories = listOf(priorityCategory, silentCategory),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf(adOverride.id), result.matchedRuleIds)
    }

    @Test
    fun override_is_ignored_when_its_base_did_not_fire() {
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
        val silentCategory = category(
            id = "cat-payment-ad-silent",
            action = CategoryAction.SILENT,
            ruleIds = listOf(adOverride.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "광고 알림",
                body = "새 광고가 도착했어요",
            ),
            rules = listOf(basePayment, adOverride),
            categories = listOf(silentCategory),
        )

        assertEquals(NotificationDecision.SILENT, result.decision)
        assertEquals(listOf(adOverride.id), result.matchedRuleIds)
    }

    @Test
    fun disabled_override_does_not_win_and_base_rule_applies() {
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
            enabled = false,
            matchValue = "광고",
            overrideOf = basePayment.id,
        )
        val priorityCategory = category(
            id = "cat-payment-priority",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(basePayment.id),
        )
        val silentCategory = category(
            id = "cat-payment-ad-silent",
            action = CategoryAction.SILENT,
            ruleIds = listOf(adOverride.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "결제 프로모션",
                body = "결제 광고 쿠폰",
            ),
            rules = listOf(basePayment, adOverride),
            categories = listOf(priorityCategory, silentCategory),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        assertEquals(listOf(basePayment.id), result.matchedRuleIds)
    }

    // endregion

    // region Plan `2026-04-21-ignore-tier-fourth-decision` —
    // IGNORE routing through Category.action = IGNORE.

    @Test
    fun ignore_rule_match_routes_to_ignore_decision() {
        val ignoreRule = RuleUiModel(
            id = "r-ignore-ads",
            title = "광고",
            subtitle = "무시",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
        )
        val category = category(
            id = "cat-ignore-ads",
            action = CategoryAction.IGNORE,
            ruleIds = listOf(ignoreRule.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "오늘의 광고",
                body = "새 광고가 도착했어요",
            ),
            rules = listOf(ignoreRule),
            categories = listOf(category),
        )

        assertEquals(NotificationDecision.IGNORE, result.decision)
        assertEquals(listOf(ignoreRule.id), result.matchedRuleIds)
    }

    @Test
    fun classifier_never_auto_promotes_to_ignore_without_a_rule() {
        val inputs = listOf(
            ClassificationInput(sender = "엄마", packageName = "com.kakao.talk", body = "오늘 저녁 몇 시야?"),
            ClassificationInput(packageName = "com.bank.app", body = "인증번호 123456"),
            ClassificationInput(
                packageName = "com.coupang.mobile",
                body = "장바구니 할인",
                quietHours = true,
            ),
            ClassificationInput(
                packageName = "com.news.app",
                body = "속보",
                duplicateCountInWindow = 5,
            ),
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
    fun always_priority_override_beats_base_ignore_rule() {
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
        val ignoreCategory = category(
            id = "cat-ignore-ads",
            action = CategoryAction.IGNORE,
            ruleIds = listOf(baseIgnore.id),
        )
        val priorityCategory = category(
            id = "cat-company-priority",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(priorityOverride.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.company.app",
                title = "사내 공지 — 긴급 광고",
                body = "긴급 사내 광고 알림",
            ),
            rules = listOf(baseIgnore, priorityOverride),
            categories = listOf(ignoreCategory, priorityCategory),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
        // Override shadows its base (Phase C contract) — only the override
        // id is surfaced.
        assertEquals(listOf(priorityOverride.id), result.matchedRuleIds)
    }

    @Test
    fun base_ignore_rule_still_wins_when_override_condition_absent() {
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
        val ignoreCategory = category(
            id = "cat-ignore-ads",
            action = CategoryAction.IGNORE,
            ruleIds = listOf(baseIgnore.id),
        )
        val priorityCategory = category(
            id = "cat-company-priority",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(priorityOverride.id),
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.promo.app",
                title = "광고",
                body = "새 광고",
            ),
            rules = listOf(baseIgnore, priorityOverride),
            categories = listOf(ignoreCategory, priorityCategory),
        )

        assertEquals(NotificationDecision.IGNORE, result.decision)
        assertEquals(listOf(baseIgnore.id), result.matchedRuleIds)
    }

    // endregion

    @Test
    fun no_rule_match_returns_empty_matched_rule_ids_when_rules_are_defined() {
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

    // region Phase P2 Task 6 — rule-type specificity ladder lifted to Category
    //
    // APP > KEYWORD > PERSON > SCHEDULE > REPEAT_BUNDLE. A Category whose
    // matched rule has a higher-ranked type wins over a Category with a
    // lower-ranked matched type, regardless of drag order.

    @Test
    fun app_rule_category_beats_keyword_rule_category_despite_later_drag_order() {
        // Both rules fire on the same notification. APP-type rule sits in a
        // Category the user dragged LOW (order = 5); KEYWORD-type rule sits
        // in a Category dragged HIGH (order = 0). The APP Category must
        // still win because APP is more specific than KEYWORD.
        val appRule = RuleUiModel(
            id = "r-app-kakao",
            title = "카카오톡",
            subtitle = "앱 분류",
            type = RuleTypeUi.APP,
            enabled = true,
            matchValue = "com.kakao.talk",
        )
        val keywordRule = RuleUiModel(
            id = "r-keyword-ad",
            title = "광고",
            subtitle = "키워드 분류",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "광고",
        )
        val appCategory = category(
            id = "cat-app",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(appRule.id),
            order = 5,
        )
        val keywordCategory = category(
            id = "cat-keyword",
            action = CategoryAction.IGNORE,
            ruleIds = listOf(keywordRule.id),
            order = 0,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.kakao.talk",
                title = "친구",
                body = "오늘 광고 이벤트 참여하세요",
            ),
            rules = listOf(appRule, keywordRule),
            categories = listOf(keywordCategory, appCategory),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
    }

    @Test
    fun keyword_rule_category_beats_person_rule_category() {
        val keywordRule = RuleUiModel(
            id = "r-keyword-otp",
            title = "OTP",
            subtitle = "키워드",
            type = RuleTypeUi.KEYWORD,
            enabled = true,
            matchValue = "OTP",
        )
        val personRule = RuleUiModel(
            id = "r-person-mom",
            title = "엄마",
            subtitle = "사람",
            type = RuleTypeUi.PERSON,
            enabled = true,
            matchValue = "엄마",
        )
        val keywordCategory = category(
            id = "cat-kw",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(keywordRule.id),
            order = 5,
        )
        val personCategory = category(
            id = "cat-person",
            action = CategoryAction.DIGEST,
            ruleIds = listOf(personRule.id),
            order = 0,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                sender = "엄마",
                packageName = "com.bank.app",
                title = "인증",
                body = "OTP 번호 123456",
            ),
            rules = listOf(keywordRule, personRule),
            categories = listOf(personCategory, keywordCategory),
        )

        assertEquals(NotificationDecision.PRIORITY, result.decision)
    }

    @Test
    fun schedule_rule_category_beats_repeat_bundle_rule_category() {
        val scheduleRule = RuleUiModel(
            id = "r-schedule",
            title = "업무",
            subtitle = "스케줄",
            type = RuleTypeUi.SCHEDULE,
            enabled = true,
            matchValue = "9-18",
        )
        val repeatRule = RuleUiModel(
            id = "r-repeat",
            title = "반복",
            subtitle = "반복묶음",
            type = RuleTypeUi.REPEAT_BUNDLE,
            enabled = true,
            matchValue = "2",
        )
        val scheduleCategory = category(
            id = "cat-schedule",
            action = CategoryAction.DIGEST,
            ruleIds = listOf(scheduleRule.id),
            order = 5,
        )
        val repeatCategory = category(
            id = "cat-repeat",
            action = CategoryAction.PRIORITY,
            ruleIds = listOf(repeatRule.id),
            order = 0,
        )

        val result = classifier.classify(
            input = ClassificationInput(
                packageName = "com.chat.app",
                body = "같은 알림",
                duplicateCountInWindow = 2,
                hourOfDay = 10,
            ),
            rules = listOf(scheduleRule, repeatRule),
            categories = listOf(repeatCategory, scheduleCategory),
        )

        assertEquals(NotificationDecision.DIGEST, result.decision)
    }

    // endregion

    private fun category(
        id: String,
        action: CategoryAction,
        ruleIds: List<String>,
        appPackageName: String? = null,
        order: Int = 0,
    ): Category = Category(
        id = id,
        name = id,
        appPackageName = appPackageName,
        ruleIds = ruleIds,
        action = action,
        order = order,
    )
}
