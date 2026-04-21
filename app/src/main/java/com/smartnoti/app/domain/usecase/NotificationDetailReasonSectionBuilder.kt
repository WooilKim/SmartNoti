package com.smartnoti.app.domain.usecase

import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleUiModel

/**
 * Splits the raw `reasonTags` + `matchedRuleIds` into the two Detail sub-sections
 * introduced by plan `rules-ux-v2-inbox-restructure` Phase B Task 3:
 *
 * - [NotificationDetailReasonSections.classifierSignals] — grey, non-interactive
 *   chips describing classifier-internal factoids (발신자 있음, 조용한 시간,
 *   반복 알림 …). Sourced from `reasonTags` after filtering out entries that are
 *   owned by a matched rule.
 * - [NotificationDetailReasonSections.ruleHits] — blue, clickable chips that
 *   resolve `matchedRuleIds` against the current rules list. Each entry carries
 *   enough info for the UI to navigate back to the Rules tab with
 *   `highlightRuleId`.
 *
 * Stale rule ids (matched at capture time but since deleted) are dropped — the
 * UI should never point at a rule that no longer exists. Tags that duplicate a
 * matched rule's title are also dropped from the classifier signals so the same
 * label doesn't appear on both sides of the split.
 *
 * The two umbrella tags `사용자 규칙` and `온보딩 추천` are filtered from the
 * signals section whenever a rule hit is resolved — they are implicit in the
 * "적용된 규칙" section and would otherwise read as redundant chips. When no
 * rule hit resolves (rule deleted, or stale data), the tags stay so the user
 * still sees that a rule-based decision was intended.
 */
class NotificationDetailReasonSectionBuilder {

    fun build(
        notification: NotificationUiModel,
        rules: List<RuleUiModel>,
    ): NotificationDetailReasonSections {
        val rulesById = rules.associateBy { it.id }
        val resolvedRules = notification.matchedRuleIds
            .mapNotNull { rulesById[it] }
            .distinctBy { it.id }

        val ruleHits = resolvedRules.map { rule ->
            NotificationDetailRuleReference(
                ruleId = rule.id,
                title = rule.title,
                matchValue = rule.matchValue,
            )
        }

        val resolvedTitles = resolvedRules.map { it.title }.toSet()
        val suppressWhenRuleResolved = if (ruleHits.isNotEmpty()) {
            RULE_UMBRELLA_TAGS
        } else {
            emptySet()
        }

        val classifierSignals = notification.reasonTags.filterNot { tag ->
            tag in resolvedTitles || tag in suppressWhenRuleResolved
        }

        return NotificationDetailReasonSections(
            classifierSignals = classifierSignals,
            ruleHits = ruleHits,
        )
    }

    companion object {
        private val RULE_UMBRELLA_TAGS = setOf("사용자 규칙", "온보딩 추천")
    }
}

data class NotificationDetailReasonSections(
    val classifierSignals: List<String>,
    val ruleHits: List<NotificationDetailRuleReference>,
)

data class NotificationDetailRuleReference(
    val ruleId: String,
    val title: String,
    val matchValue: String,
)
