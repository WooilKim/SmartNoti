package com.smartnoti.app.ui.screens.onboarding

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
    private val classifier: NotificationClassifier = NotificationClassifier(
        vipSenders = DEFAULT_VIP_SENDERS,
        priorityKeywords = DEFAULT_PRIORITY_KEYWORDS,
        shoppingPackages = DEFAULT_SHOPPING_PACKAGES,
    ),
) {
    suspend fun applySelection(
        rulesRepository: RulesRepository,
        settingsRepository: SettingsRepository,
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
        private val DEFAULT_SHOPPING_PACKAGES = setOf("com.coupang.mobile")
    }
}

data class OnboardingQuickStartApplicationResult(
    val rules: List<RuleUiModel>,
    val settings: SmartNotiSettings,
)
