package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationClassification
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.toDecision

class NotificationClassifier(
    private val vipSenders: Set<String>,
    private val priorityKeywords: Set<String>,
    private val shoppingPackages: Set<String>,
    @Suppress("UNUSED_PARAMETER") ruleConflictResolver: RuleConflictResolver = RuleConflictResolver(),
    private val categoryConflictResolver: CategoryConflictResolver = CategoryConflictResolver(),
    private val advertisingPrefixDetector: KoreanAdvertisingPrefixDetector = KoreanAdvertisingPrefixDetector(),
) {
    /**
     * Classify [input] against the user's Rule / Category graph.
     *
     * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P2
     * Task 5 rewires this path:
     *  1. Filter [rules] down to the enabled rules that match [input].
     *  2. Apply [RuleConflictResolver] to resolve **override vs base**
     *     collisions — a matched override drops its matched base from the
     *     set so `base+override → override` semantics (Phase C contract)
     *     still hold. Any rule without an override in the matched set
     *     passes through unchanged.
     *  3. Lift every surviving rule to every Category that owns it.
     *  4. Hand the resulting Category list to [CategoryConflictResolver]
     *     along with a `ruleId -> ruleType` map so the resolver can apply
     *     the Task 6 specificity ladder (APP > KEYWORD > PERSON > SCHEDULE
     *     > REPEAT_BUNDLE) + app-pin bonus + drag-order tie-break.
     *  5. Map the winning `Category.action` to a [NotificationDecision].
     *
     * When no user rule matches (or no Category owns the matched rules) the
     * legacy heuristic chain (VIP / priority keywords / quiet-hours shopping
     * / duplicate burst / SILENT default) runs so users with an empty
     * Category graph still see sensible routing.
     *
     * [NotificationClassification.matchedRuleIds] reports every matched rule
     * (after override collapse) in their list-order so Detail reason chips
     * can still explain every rule that fired — not just the one whose
     * Category happened to win.
     */
    /**
     * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md`
     * Tasks 3+4 add the optional [shoppingPackagesOverride] parameter so the
     * production hot path can thread `SmartNotiSettings.quietHoursPackages`
     * (the user-editable picker output) into the quiet-hours branch on every
     * call without rebuilding the classifier. When `null` (legacy callers,
     * unit-test fixtures, onboarding's auxiliary classifier), the
     * constructor-baked [shoppingPackages] applies — so existing call sites
     * preserve historical behavior. When non-null, the override fully
     * replaces the default for this single classification.
     *
     * The architecture follows option (B) from the plan because it mirrors
     * the per-input `duplicateThreshold` pattern (`2026-04-26-duplicate-
     * threshold-window-settings`): cheaper than re-creating the classifier
     * per notification and keeps the dynamic input adjacent to the other
     * settings-derived per-call knobs.
     */
    fun classify(
        input: ClassificationInput,
        rules: List<RuleUiModel> = emptyList(),
        categories: List<Category> = emptyList(),
        shoppingPackagesOverride: Set<String>? = null,
    ): NotificationClassification {
        val effectiveShoppingPackages = shoppingPackagesOverride ?: shoppingPackages
        val matchedRules = findMatchingRules(input, rules)
        if (matchedRules.isNotEmpty()) {
            val effectiveRules = collapseOverrides(matchedRules)
            val matchedRuleIds = effectiveRules.map { it.id }
            val owning = categories.filter { category ->
                category.ruleIds.any { ruleId -> ruleId in matchedRuleIds }
            }
            if (owning.isNotEmpty()) {
                // Issue #478 (Bug A): pre-compute KCC `(광고)` prefix presence
                // and hand it to the resolver. When true, the resolver drops
                // PRIORITY-action Categories from the matched set if any
                // non-PRIORITY peer matched too — so KCC-disclosed ads never
                // reach the priority tray even when their copy mentions an
                // IMPORTANT keyword (배송 / 결제 / 대출).
                val hasAdvertisingPrefix = advertisingPrefixDetector
                    .hasAdvertisingPrefix(body = input.body, title = input.title)
                val winner = categoryConflictResolver.resolve(
                    matched = owning,
                    allCategories = categories,
                    matchedRuleTypes = effectiveRules.associate { it.id to it.type },
                    hasAdvertisingPrefix = hasAdvertisingPrefix,
                )
                if (winner != null) {
                    return NotificationClassification(
                        decision = winner.action.toDecision(),
                        matchedRuleIds = matchedRuleIds,
                    )
                }
            }
            // Matched rules exist but no Category owns them — fall back to
            // SILENT so an orphaned rule cannot crash the notifier hot path.
            // The matched rule ids are still reported so Detail reason chips
            // can surface them.
            return NotificationClassification(
                decision = NotificationDecision.SILENT,
                matchedRuleIds = matchedRuleIds,
            )
        }

        if (input.sender != null && input.sender in vipSenders) {
            return NotificationClassification(NotificationDecision.PRIORITY)
        }

        val content = listOf(input.title, input.body).joinToString(" ")
        if (priorityKeywords.any { keyword -> content.contains(keyword, ignoreCase = true) }) {
            return NotificationClassification(NotificationDecision.PRIORITY)
        }

        if (input.packageName in effectiveShoppingPackages && input.quietHours) {
            return NotificationClassification(NotificationDecision.DIGEST)
        }

        // Plan `2026-04-26-duplicate-threshold-window-settings.md` Task 3:
        // generalize the historical hard-coded `>= 3` to the user-tunable
        // `input.duplicateThreshold` (default 3). The repository's setter
        // coerces to >= 1 so this comparison is always meaningful — a 0
        // would otherwise route every notification to DIGEST.
        if (input.duplicateCountInWindow >= input.duplicateThreshold) {
            return NotificationClassification(NotificationDecision.DIGEST)
        }

        return NotificationClassification(NotificationDecision.SILENT)
    }

    /**
     * Return the set of enabled rules that match [input], preserving the
     * ordering from [rules] so downstream tie-breaks stay deterministic.
     */
    private fun findMatchingRules(
        input: ClassificationInput,
        rules: List<RuleUiModel>,
    ): List<RuleUiModel> {
        val content = listOf(input.title, input.body).joinToString(" ")
        return rules.filter { rule -> rule.enabled && matches(rule, input, content) }
    }

    /**
     * Collapse base vs override collisions within the matched set: when an
     * override and its base both fired, drop the base so only the override
     * contributes to Category selection. This preserves the Phase C
     * contract exercised by `override_rule_wins_over_its_base_…` tests.
     *
     * Runs in O(n): we build the set of matched ids once and filter base
     * rules whose id is referenced by a matched override's `overrideOf`.
     */
    private fun collapseOverrides(matched: List<RuleUiModel>): List<RuleUiModel> {
        if (matched.size <= 1) return matched
        val matchedIds = matched.mapTo(mutableSetOf()) { it.id }
        val shadowedBaseIds = matched
            .mapNotNull { it.overrideOf }
            .filter { baseId -> baseId in matchedIds }
            .toSet()
        if (shadowedBaseIds.isEmpty()) return matched
        return matched.filterNot { it.id in shadowedBaseIds }
    }

    private fun matches(rule: RuleUiModel, input: ClassificationInput, content: String): Boolean =
        when (rule.type) {
            RuleTypeUi.PERSON -> !input.sender.isNullOrBlank() &&
                input.sender.equals(rule.matchValue, ignoreCase = true)
            RuleTypeUi.APP -> input.packageName.equals(rule.matchValue, ignoreCase = true)
            RuleTypeUi.KEYWORD -> rule.matchValue
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .any { keyword -> content.contains(keyword, ignoreCase = true) }
            RuleTypeUi.SCHEDULE -> input.hourOfDay != null &&
                matchesSchedule(rule.matchValue, input.hourOfDay)
            RuleTypeUi.REPEAT_BUNDLE -> matchesRepeatBundleThreshold(
                rule.matchValue,
                input.duplicateCountInWindow,
            )
            // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
            // Task 2. Substring + ignoreCase against the notification title
            // so messenger 1:1 DMs whose sender metadata is empty (Teams /
            // Slack / KakaoTalk) can still be promoted via "이 발신자" rules.
            // Blank matchValue is rejected so a freshly-saved draft rule
            // cannot promote every notification.
            RuleTypeUi.SENDER -> rule.matchValue.isNotBlank() &&
                input.title.contains(rule.matchValue, ignoreCase = true)
        }

    private fun matchesSchedule(schedule: String, hourOfDay: Int): Boolean {
        val parts = schedule.split('-')
        if (parts.size != 2) return false

        val start = parts[0].toIntOrNull() ?: return false
        val end = parts[1].toIntOrNull() ?: return false

        return if (start <= end) {
            hourOfDay in start until end
        } else {
            hourOfDay >= start || hourOfDay < end
        }
    }

    private fun matchesRepeatBundleThreshold(matchValue: String, duplicateCountInWindow: Int): Boolean {
        val threshold = matchValue.toIntOrNull() ?: return false
        return threshold > 0 && duplicateCountInWindow >= threshold
    }

}
