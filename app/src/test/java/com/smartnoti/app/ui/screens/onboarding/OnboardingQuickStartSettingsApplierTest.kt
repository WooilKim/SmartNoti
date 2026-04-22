package com.smartnoti.app.ui.screens.onboarding

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import com.smartnoti.app.notification.NotificationSuppressionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingQuickStartSettingsApplierTest {

    private val ruleApplier = OnboardingQuickStartRuleApplier(RuleDraftFactory())
    private val applier = OnboardingQuickStartSettingsApplier(ruleApplier)

    @Test
    fun accepting_quick_start_can_produce_rule_and_suppression_updates_together() {
        val result = applier.buildApplicationResult(
            existingRules = emptyList(),
            existingSettings = SmartNotiSettings(),
            selectedPresetIds = setOf(
                OnboardingQuickStartPresetId.PROMO_QUIETING,
                OnboardingQuickStartPresetId.REPEAT_BUNDLING,
                OnboardingQuickStartPresetId.IMPORTANT_PRIORITY,
            ),
            currentNotifications = listOf(
                notification(
                    id = "promo-1",
                    packageName = "com.example.shop",
                    title = "오늘만 특가",
                    body = "쿠폰이 도착했어요",
                ),
                notification(
                    id = "repeat-1",
                    packageName = "com.example.community",
                    title = "새 댓글",
                    body = "댓글이 달렸어요",
                ),
                notification(
                    id = "repeat-2",
                    packageName = "com.example.community",
                    title = "새 댓글",
                    body = "댓글이 달렸어요",
                ),
                notification(
                    id = "repeat-3",
                    packageName = "com.example.community",
                    title = "새 댓글",
                    body = "댓글이 달렸어요",
                ),
            ),
        )

        assertEquals(
            listOf("중요 알림", "프로모션 알림", "반복 알림"),
            result.rules.map { it.title },
        )
        assertTrue(result.settings.suppressSourceForDigestAndSilent)
        assertEquals(
            setOf("com.example.shop", "com.example.community"),
            result.settings.suppressedSourceApps,
        )
    }

    @Test
    fun promo_and_repeat_presets_merge_inferred_packages_with_existing_suppressed_apps() {
        val result = applier.buildApplicationResult(
            existingRules = emptyList(),
            existingSettings = SmartNotiSettings(
                suppressSourceForDigestAndSilent = false,
                suppressedSourceApps = setOf("com.example.chat"),
            ),
            selectedPresetIds = setOf(
                OnboardingQuickStartPresetId.PROMO_QUIETING,
                OnboardingQuickStartPresetId.REPEAT_BUNDLING,
            ),
            currentNotifications = listOf(
                notification(
                    id = "promo-1",
                    packageName = "com.example.shop",
                    title = "주말 세일",
                    body = "혜택을 확인해 보세요",
                ),
                notification(
                    id = "repeat-1",
                    packageName = "com.example.community",
                    title = "새 댓글",
                    body = "댓글이 달렸어요",
                ),
                notification(
                    id = "repeat-2",
                    packageName = "com.example.community",
                    title = "새 댓글",
                    body = "댓글이 달렸어요",
                ),
                notification(
                    id = "repeat-3",
                    packageName = "com.example.community",
                    title = "새 댓글",
                    body = "댓글이 달렸어요",
                ),
            ),
        )

        assertEquals(
            setOf("com.example.chat", "com.example.shop", "com.example.community"),
            result.settings.suppressedSourceApps,
        )
    }

    @Test
    fun suppress_source_setting_is_enabled_when_targetable_preset_is_selected() {
        val result = applier.buildApplicationResult(
            existingRules = emptyList(),
            existingSettings = SmartNotiSettings(),
            selectedPresetIds = setOf(OnboardingQuickStartPresetId.PROMO_QUIETING),
            currentNotifications = emptyList(),
        )

        assertTrue(result.settings.suppressSourceForDigestAndSilent)
    }

    @Test
    fun important_only_selection_does_not_blindly_suppress_all_captured_apps() {
        val result = applier.buildApplicationResult(
            existingRules = emptyList(),
            existingSettings = SmartNotiSettings(),
            selectedPresetIds = setOf(OnboardingQuickStartPresetId.IMPORTANT_PRIORITY),
            currentNotifications = listOf(
                notification(
                    id = "important-1",
                    packageName = "com.example.bank",
                    title = "결제가 완료됐어요",
                    body = "10,000원 결제",
                ),
                notification(
                    id = "general-1",
                    packageName = "com.example.chat",
                    title = "새 메시지",
                    body = "안녕하세요",
                ),
            ),
        )

        assertFalse(result.settings.suppressSourceForDigestAndSilent)
        assertTrue(result.settings.suppressedSourceApps.isEmpty())
        assertEquals(1, result.rules.size)
    }

    @Test
    fun onboarding_applied_settings_match_existing_suppression_policy_expectations() {
        val result = applier.buildApplicationResult(
            existingRules = emptyList(),
            existingSettings = SmartNotiSettings(),
            selectedPresetIds = setOf(OnboardingQuickStartPresetId.PROMO_QUIETING),
            currentNotifications = listOf(
                notification(
                    id = "promo-1",
                    packageName = "com.example.shop",
                    title = "오늘만 특가",
                    body = "쿠폰이 도착했어요",
                ),
            ),
        )

        assertTrue(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = result.settings.suppressSourceForDigestAndSilent,
                suppressedApps = result.settings.suppressedSourceApps,
                packageName = "com.example.shop",
                decision = com.smartnoti.app.domain.model.NotificationDecision.DIGEST,
            ),
        )
        assertFalse(
            NotificationSuppressionPolicy.shouldSuppressSourceNotification(
                suppressDigestAndSilent = result.settings.suppressSourceForDigestAndSilent,
                suppressedApps = result.settings.suppressedSourceApps,
                packageName = "com.example.shop",
                decision = com.smartnoti.app.domain.model.NotificationDecision.PRIORITY,
            ),
        )
    }

    private fun notification(
        id: String,
        packageName: String,
        title: String,
        body: String,
        sender: String? = null,
    ) = NotificationUiModel(
        id = id,
        appName = packageName.substringAfterLast('.'),
        packageName = packageName,
        sender = sender,
        title = title,
        body = body,
        receivedAtLabel = "방금",
        status = NotificationStatusUi.SILENT,
        reasonTags = emptyList(),
    )
}
