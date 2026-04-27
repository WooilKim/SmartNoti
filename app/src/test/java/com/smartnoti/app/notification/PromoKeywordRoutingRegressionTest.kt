package com.smartnoti.app.notification

import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.NotificationDecision
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.SourceNotificationSuppressionState
import com.smartnoti.app.domain.model.VibrationMode
import com.smartnoti.app.domain.usecase.CategoryConflictResolver
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartCategoryApplier
import com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartPresetId
import com.smartnoti.app.ui.screens.onboarding.OnboardingQuickStartRuleApplier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-27-fix-issue-478-promo-keyword-not-routing.md`
 * Task 1 — six hypothesis-isolating regression tests for issue #478 ("(광고)"
 * notifications not routing to DIGEST + not being cancelled from the system
 * tray for a user who picked the PROMO_QUIETING quick-start preset).
 *
 * Each `@Test` reproduces a single hypothesis (H1..H6). RED-by-design: the
 * subset that actually fails on `main` today identifies the root cause
 * branch. Task 2 patches *that* branch only — the remaining tests survive
 * as regression guards.
 *
 * Hypotheses (mirrored from the plan):
 *  - H1: PROMO_QUIETING preset does not produce the expected KEYWORD rule.
 *  - H2: The produced rule lands with `enabled = false`.
 *  - H3: The owning Category does not map to `CategoryAction.DIGEST`.
 *  - H4: `SmartNotiSettings.suppressSourceForDigestAndSilent` default flipped
 *        back to `false` (regression of the 2026-04-24 default-ON change).
 *  - H5: `SuppressedSourceAppsAutoExpansionPolicy` does not add the package on
 *        the first DIGEST capture, so the same processing cycle leaves the
 *        source notification in the tray.
 *  - H6: `NotificationDecisionPipeline` ("(광고)" title + DIGEST + suppression
 *        ON) does not call `cancelSource` exactly once and persist a DIGEST
 *        row. End-to-end branch test through the production pipeline.
 *
 * If multiple hypotheses are RED simultaneously, plan-implementer pauses and
 * reports per the plan's "Multi-RED" risk.
 */
class PromoKeywordRoutingRegressionTest {

    // -- H1 -----------------------------------------------------------------

    @Test
    fun h1_promo_quieting_preset_produces_enabled_keyword_rule_with_canonical_match_value() {
        val applier = OnboardingQuickStartRuleApplier(RuleDraftFactory())

        val rules = applier.buildRules(setOf(OnboardingQuickStartPresetId.PROMO_QUIETING))

        // The plan pins the exact match value the onboarding preset must emit
        // so a future copy edit does not silently drop "광고" from the list.
        assertEquals(1, rules.size)
        val rule = rules.single()
        assertEquals(RuleTypeUi.KEYWORD, rule.type)
        assertEquals("광고,프로모션,쿠폰,세일,특가,이벤트,혜택", rule.matchValue)
        assertTrue(
            "PROMO_QUIETING preset must produce an enabled rule (otherwise the classifier silently ignores it).",
            rule.enabled,
        )
    }

    // -- H2 -----------------------------------------------------------------

    @Test
    fun h2_merge_rules_preserves_enabled_flag_when_existing_promo_rule_is_disabled() {
        // The existing rule was previously disabled by the user. Re-applying
        // the PROMO_QUIETING preset MUST flip it back to enabled so the
        // classifier picks it up — keeping it disabled would silently leave
        // "(광고)" notifications unmatched.
        val factory = RuleDraftFactory()
        val applier = OnboardingQuickStartRuleApplier(factory)

        val existingDisabled = factory.create(
            title = "예전 프로모션 규칙",
            matchValue = "광고,프로모션,쿠폰,세일,특가,이벤트,혜택",
            type = RuleTypeUi.KEYWORD,
            enabled = false,
        )

        val merged = applier.mergeRules(
            existingRules = listOf(existingDisabled),
            selectedPresetIds = setOf(OnboardingQuickStartPresetId.PROMO_QUIETING),
        )

        assertEquals(1, merged.size)
        assertTrue(
            "mergeRules must promote the rule to enabled=true when the preset is re-selected; otherwise PROMO_QUIETING re-onboarding has no effect.",
            merged.single().enabled,
        )
    }

    // -- H3 -----------------------------------------------------------------

    @Test
    fun h3_promo_quieting_owning_category_uses_digest_action_and_resolver_picks_it_for_keyword_match() {
        val ruleApplier = OnboardingQuickStartRuleApplier(RuleDraftFactory())
        val categoryApplier = OnboardingQuickStartCategoryApplier()

        val rulesByPreset = ruleApplier.buildRulesByPresetId(
            setOf(OnboardingQuickStartPresetId.PROMO_QUIETING),
        )
        val categories = categoryApplier.buildCategoriesByPresetId(rulesByPreset)

        val promoCategory = categories.single()
        assertEquals(
            "PROMO_QUIETING owning Category must default to CategoryAction.DIGEST.",
            CategoryAction.DIGEST,
            promoCategory.action,
        )

        // Now exercise the conflict resolver with the ONE matched rule (the
        // promo KEYWORD rule). A keyword-only match against an unpinned
        // Category must still let the resolver pick the DIGEST Category as
        // the winner (no tie-break against any other Category — the user
        // only selected PROMO_QUIETING).
        val promoRule = rulesByPreset.values.single()
        val resolver = CategoryConflictResolver()
        val winner = resolver.resolve(
            matched = listOf(promoCategory),
            allCategories = listOf(promoCategory),
            matchedRuleTypes = mapOf(promoRule.id to RuleTypeUi.KEYWORD),
        )
        assertNotNull("Resolver must pick the matched promo Category.", winner)
        assertEquals(CategoryAction.DIGEST, winner!!.action)
    }

    // -- H4 -----------------------------------------------------------------

    @Test
    fun h4_smart_noti_settings_default_keeps_suppress_source_for_digest_and_silent_on() {
        // Plan `2026-04-24-duplicate-notifications-suppress-defaults-ac.md`
        // Task 4 flipped this default to `true`. Issue #478 may be a regression
        // of that flip; pin it here so any accidental revert turns this test
        // RED before the user notices the (광고) tray entry persisting.
        val defaults = SmartNotiSettings()
        assertTrue(
            "suppressSourceForDigestAndSilent must default to true so fresh installs cancel DIGEST source-tray entries.",
            defaults.suppressSourceForDigestAndSilent,
        )
    }

    // -- H5 -----------------------------------------------------------------

    @Test
    fun h5_auto_expansion_adds_promo_package_on_first_digest_so_same_cycle_cancels_source() {
        // The plan calls out: "first (광고) notification 에서 packageName 을
        // suppressedSourceApps 에 자동 추가해 같은 처리 cycle 에서 cancel 이
        // 적용되는지." Today's policy returns `null` when `currentApps` is
        // empty (opt-out semantic), which means a user with the default
        // setup never has their package added — they rely on the empty-set
        // semantic in `NotificationSuppressionPolicy` instead. If issue #478
        // is rooted here (e.g., a regression that made `currentApps` non-empty
        // by accident OR a regression that broke the empty-set opt-out path),
        // this test surfaces it: the package should *either* be auto-expanded
        // OR `NotificationSuppressionPolicy` should report `true` for the
        // empty-set case. We assert the auto-expansion contract here as the
        // narrower / stronger guarantee — Task 2 may relax this if the actual
        // root cause is in the suppression policy instead.
        val expanded = SuppressedSourceAppsAutoExpansionPolicy.expandedAppsOrNull(
            decision = NotificationDecision.DIGEST,
            suppressSourceForDigestAndSilent = true,
            packageName = "com.smartnoti.testnotifier",
            currentApps = emptySet(),
            excludedApps = emptySet(),
        )

        assertEquals(
            "First DIGEST capture for an unseen package must auto-expand the suppressed-source apps so the same processing cycle can cancel the source tray entry.",
            setOf("com.smartnoti.testnotifier"),
            expanded,
        )
    }

    // -- H6 -----------------------------------------------------------------

    @Test
    fun h6_pipeline_for_promo_titled_digest_with_default_settings_cancels_source_once_and_saves_digest() = runTest {
        // End-to-end through the production NotificationDecisionPipeline.
        // Inputs mirror the user's reported scenario: title starts with
        // "(광고)", classifier produces DIGEST (NotificationStatusUi.DIGEST),
        // suppression ON, suppressed apps EMPTY (default install). Expected:
        //   1. `cancelSource` is called exactly once (so the system tray
        //      drops the original notification).
        //   2. `save` is called once with status=DIGEST and the
        //      sourceSuppressionState recording the cancel.
        //   3. A replacement notification is posted (the user wants the
        //      DIGEST to surface inside SmartNoti, not the original).
        // If the suppression branch regresses (H6 root cause), `cancelSource`
        // count drops to 0 and the saved row's `replacementNotificationIssued`
        // is `false` — both assertions surface it.
        val actions = RecordingSourceTrayActions()
        val pipeline = NotificationDecisionPipeline(actions)

        val baseNotification = NotificationUiModel(
            id = "promo-1",
            appName = "TestNotifier",
            packageName = "com.smartnoti.testnotifier",
            sender = null,
            title = "(광고) 오늘만 특가",
            body = "세일 안내",
            receivedAtLabel = "방금",
            status = NotificationStatusUi.DIGEST,
            reasonTags = listOf("keyword:광고"),
            score = null,
            isBundled = false,
            isPersistent = false,
            alertLevel = AlertLevel.NONE,
            vibrationMode = VibrationMode.OFF,
            headsUpEnabled = false,
            lockScreenVisibility = LockScreenVisibilityMode.SECRET,
        )

        pipeline.dispatch(
            NotificationDecisionPipeline.DispatchInput(
                baseNotification = baseNotification,
                sourceEntryKey = "com.smartnoti.testnotifier|promo-1",
                packageName = "com.smartnoti.testnotifier",
                appName = "TestNotifier",
                postedAtMillis = 10_000L,
                contentSignature = "promo-sig",
                settings = SmartNotiSettings(),
                isPersistent = false,
                shouldBypassPersistentHiding = false,
                isProtectedSourceNotification = false,
            )
        )

        assertEquals(
            "(광고) DIGEST capture with default settings must cancel the source-tray entry exactly once.",
            listOf("com.smartnoti.testnotifier|promo-1"),
            actions.cancelledKeys,
        )
        assertEquals(
            "(광고) DIGEST capture must post a SmartNoti replacement notification.",
            1,
            actions.replacementCalls,
        )
        val saved = actions.savedNotifications.single()
        assertEquals(
            "Saved row must record DIGEST status so it shows up in the Digest view.",
            NotificationStatusUi.DIGEST,
            saved.notification.status,
        )
        assertEquals(
            "Saved row must record CANCEL_ATTEMPTED so the audit trail reflects the cancel attempt.",
            SourceNotificationSuppressionState.CANCEL_ATTEMPTED,
            saved.notification.sourceSuppressionState,
        )
        assertTrue(
            "replacementNotificationIssued must flip to true when the pipeline posts the SmartNoti replacement.",
            saved.notification.replacementNotificationIssued,
        )
        // Sanity: with `currentApps` empty (default install), the pipeline
        // should *either* auto-expand (H5) OR rely on the empty-set opt-out.
        // We do not pin which branch handles it here — H5's assertion does.
        // Pin only that the DIGEST cancel actually happened.
        assertNull(
            "IGNORE-only branch must not fire for DIGEST capture.",
            saved.notification.silentMode,
        )

        // Suppress the "unused" warning on the helper while keeping it
        // available for future regression deltas.
        @Suppress("UNUSED_VARIABLE")
        val unused: Category? = null
        @Suppress("UNUSED_VARIABLE")
        val unusedRule: RuleUiModel? = null
    }
}
