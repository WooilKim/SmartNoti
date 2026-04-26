package com.smartnoti.app.ui.screens.onboarding

import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.ClassificationInput
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.NotificationClassifier
import kotlinx.coroutines.flow.first

class OnboardingQuickStartSettingsApplier(
    private val ruleApplier: OnboardingQuickStartRuleApplier,
    private val categoryApplier: OnboardingQuickStartCategoryApplier =
        OnboardingQuickStartCategoryApplier(),
    private val classifier: NotificationClassifier = NotificationClassifier(
        vipSenders = DEFAULT_VIP_SENDERS,
        priorityKeywords = DEFAULT_PRIORITY_KEYWORDS,
        shoppingPackages = DEFAULT_SHOPPING_PACKAGES,
    ),
) {
    suspend fun applySelection(
        rulesRepository: RulesRepository,
        settingsRepository: SettingsRepository,
        categoriesRepository: CategoriesRepository,
        notificationRepository: NotificationRepository,
        selectedPresetIds: Set<OnboardingQuickStartPresetId>,
    ) {
        val result = buildApplicationResult(
            existingRules = rulesRepository.currentConfiguredRules(),
            existingSettings = settingsRepository.observeSettings().first(),
            selectedPresetIds = selectedPresetIds,
            currentNotifications = notificationRepository.observeAll().first(),
        )
        rulesRepository.replaceAllRules(result.rules)
        settingsRepository.setSuppressSourceForDigestAndSilent(result.settings.suppressSourceForDigestAndSilent)
        settingsRepository.setSuppressedSourceApps(result.settings.suppressedSourceApps)

        // Plan `docs/plans/2026-04-23-onboarding-quick-start-seed-categories.md` —
        // mirror the rule seed in `CategoriesRepository` so 분류 탭 / Detail
        // "분류 변경" 시트가 첫 진입부터 비어 있지 않고, `home-uncategorized-prompt`
        // cover 가 의미 있게 동작한다. Rule upsert 가 끝난 *뒤* 에 카테고리를
        // upsert 해 ruleIds 가 가리키는 rule 이 이미 영속화돼 있게 한다.
        // Category id 가 `cat-onboarding-<presetId.lowercase>` 결정적이라
        // 동일 selection 재적용은 in-place upsert 로 흡수돼 누적되지 않는다.
        val mergedRulesByPresetId = mergedRulesByPresetId(result.rules, selectedPresetIds)
        categoryApplier
            .buildCategoriesByPresetId(mergedRulesByPresetId)
            .forEach { category -> categoriesRepository.upsertCategory(category) }
    }

    /**
     * Resolve the merged-rule (the one actually persisted in
     * [RulesRepository.replaceAllRules] above) for each selected quick-start
     * preset by matching on the preset draft's `(type, matchValue)`.
     *
     * Why match on identity instead of the raw draft id: `mergeRules` swaps
     * the new rule's id for an existing user-owned rule's id when the
     * `(type, matchValue)` collides, so `selectedPreset.toRule().id` may
     * differ from what landed in storage. Using the merged rule's id keeps
     * the Category's `ruleIds` in sync with the persisted Rules.
     */
    private fun mergedRulesByPresetId(
        mergedRules: List<RuleUiModel>,
        selectedPresetIds: Set<OnboardingQuickStartPresetId>,
    ): Map<OnboardingQuickStartPresetId, RuleUiModel> {
        val draftsByPresetId = ruleApplier.buildRulesByPresetId(selectedPresetIds)
        return draftsByPresetId.mapNotNull { (presetId, draft) ->
            val merged = mergedRules.firstOrNull { rule ->
                rule.type == draft.type && rule.matchValue == draft.matchValue
            } ?: return@mapNotNull null
            presetId to merged
        }.toMap()
    }

    fun buildApplicationResult(
        existingRules: List<RuleUiModel>,
        existingSettings: SmartNotiSettings,
        selectedPresetIds: Set<OnboardingQuickStartPresetId>,
        currentNotifications: List<NotificationUiModel>,
    ): OnboardingQuickStartApplicationResult {
        val mergedRules = ruleApplier.mergeRules(
            existingRules = existingRules,
            selectedPresetIds = selectedPresetIds,
        )
        val inferredSuppressedPackages = inferSuppressedPackages(
            selectedPresetIds = selectedPresetIds,
            currentNotifications = currentNotifications,
            mergedRules = mergedRules,
        )
        val shouldEnableSuppression = selectedPresetIds.any { it in suppressionTargetablePresetIds }
        val updatedSettings = if (shouldEnableSuppression) {
            existingSettings.copy(
                suppressSourceForDigestAndSilent = true,
                suppressedSourceApps = existingSettings.suppressedSourceApps + inferredSuppressedPackages,
            )
        } else {
            existingSettings
        }
        return OnboardingQuickStartApplicationResult(
            rules = mergedRules,
            settings = updatedSettings,
        )
    }

    private fun inferSuppressedPackages(
        selectedPresetIds: Set<OnboardingQuickStartPresetId>,
        currentNotifications: List<NotificationUiModel>,
        mergedRules: List<RuleUiModel>,
    ): Set<String> {
        if (currentNotifications.isEmpty()) return emptySet()

        val quickStartRulesByPresetId = ruleApplier.buildRulesByPresetId(selectedPresetIds)
        val promoKeywords = quickStartRulesByPresetId[OnboardingQuickStartPresetId.PROMO_QUIETING]
            ?.matchValue
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        val repeatThreshold = quickStartRulesByPresetId[OnboardingQuickStartPresetId.REPEAT_BUNDLING]
            ?.matchValue
            ?.toIntOrNull()
        val duplicateCounts = currentNotifications
            .groupingBy { it.repeatGroupingKey() }
            .eachCount()

        return currentNotifications.mapNotNullTo(linkedSetOf()) { notification ->
            val quietingPresetMatched = when {
                OnboardingQuickStartPresetId.PROMO_QUIETING in selectedPresetIds &&
                    notification.matchesAnyKeyword(promoKeywords) -> true
                OnboardingQuickStartPresetId.REPEAT_BUNDLING in selectedPresetIds &&
                    repeatThreshold != null &&
                    (duplicateCounts[notification.repeatGroupingKey()] ?: 0) >= repeatThreshold -> true
                else -> false
            }
            if (!quietingPresetMatched) return@mapNotNullTo null

            val decision = classifier.classify(
                input = ClassificationInput(
                    sender = notification.sender,
                    packageName = notification.packageName,
                    title = notification.title,
                    body = notification.body,
                    quietHours = false,
                    duplicateCountInWindow = duplicateCounts[notification.repeatGroupingKey()] ?: 0,
                    hourOfDay = DEFAULT_CLASSIFICATION_HOUR,
                ),
                rules = mergedRules,
            ).decision
            when (decision) {
                NotificationDecision.PRIORITY -> null
                NotificationDecision.DIGEST,
                NotificationDecision.SILENT,
                NotificationDecision.IGNORE,
                -> notification.packageName.takeIf(String::isNotBlank)
            }
        }
    }

    private fun NotificationUiModel.repeatGroupingKey(): String {
        return listOf(packageName, title.trim(), body.trim()).joinToString("|")
    }

    private fun NotificationUiModel.matchesAnyKeyword(keywords: Set<String>): Boolean {
        if (keywords.isEmpty()) return false
        val content = listOf(title, body).joinToString(" ")
        return keywords.any { keyword -> content.contains(keyword, ignoreCase = true) }
    }

    companion object {
        private const val DEFAULT_CLASSIFICATION_HOUR = 12
        private val suppressionTargetablePresetIds = setOf(
            OnboardingQuickStartPresetId.PROMO_QUIETING,
            OnboardingQuickStartPresetId.REPEAT_BUNDLING,
        )
        private val DEFAULT_VIP_SENDERS = setOf("엄마", "팀장")
        private val DEFAULT_PRIORITY_KEYWORDS = setOf("인증번호", "OTP", "결제")
        // Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md`
        // Task 4 SSOT consolidation: the onboarding auxiliary classifier and
        // the persisted settings default both pull from the same companion
        // const so they cannot drift. Onboarding still uses the static
        // default rather than the live settings flow because this code runs
        // *before* the user has had a chance to edit the picker — the live
        // value is the same default at that point anyway.
        private val DEFAULT_SHOPPING_PACKAGES =
            SmartNotiSettings.DEFAULT_QUIET_HOURS_PACKAGES
    }
}

data class OnboardingQuickStartApplicationResult(
    val rules: List<RuleUiModel>,
    val settings: SmartNotiSettings,
)
