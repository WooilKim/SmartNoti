package com.smartnoti.app.ui.screens.categories

import com.smartnoti.app.domain.model.CategoryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pin the [UncategorizedPromptPrefillResolver] decision matrix for plan
 * `docs/plans/2026-04-26-uncategorized-prompt-editor-autoopen.md` Task 4.
 *
 * The resolver is the JVM-testable extract of `CategoriesScreen`'s prefill
 * auto-open behaviour. Compose-side `rememberSaveable` wiring is not
 * exercised here — only the pure decision (truth table + prefill shape)
 * which is what the bugs actually live in.
 */
class UncategorizedPromptPrefillResolverTest {

    // -- shouldAutoOpen --------------------------------------------------

    @Test
    fun should_auto_open_when_package_present_and_not_consumed() {
        assertTrue(
            UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = "com.example.app",
                prefillLabel = "예시",
                alreadyConsumed = false,
            ),
        )
    }

    @Test
    fun should_auto_open_when_package_present_even_with_blank_label() {
        // Plan: label is allowed to be absent — editor will leave the name
        // field empty but the dropdown still pins.
        assertTrue(
            UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = "com.example.app",
                prefillLabel = "",
                alreadyConsumed = false,
            ),
        )
        assertTrue(
            UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = "com.example.app",
                prefillLabel = null,
                alreadyConsumed = false,
            ),
        )
    }

    @Test
    fun should_not_auto_open_when_package_blank_or_null() {
        // Label alone is meaningless — editor needs the package to pin its
        // app dropdown. Match Routes.Categories.create's contract.
        assertFalse(
            UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = null,
                prefillLabel = "예시",
                alreadyConsumed = false,
            ),
        )
        assertFalse(
            UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = "  ",
                prefillLabel = "예시",
                alreadyConsumed = false,
            ),
        )
        assertFalse(
            UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = null,
                prefillLabel = null,
                alreadyConsumed = false,
            ),
        )
    }

    @Test
    fun should_not_auto_open_when_already_consumed() {
        // rememberSaveable lock prevents the modal trap where dismissing
        // the dialog triggers an immediate re-open on recomposition.
        assertFalse(
            UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = "com.example.app",
                prefillLabel = "예시",
                alreadyConsumed = true,
            ),
        )
    }

    // -- buildPrefill ---------------------------------------------------

    @Test
    fun build_prefill_uses_priority_default_action() {
        // Plan-fixed: PRIORITY default. DIGEST/IGNORE would silently
        // classify the new app the user just signalled they want to watch.
        val prefill = UncategorizedPromptPrefillResolver.buildPrefill(
            prefillPackage = "com.example.app",
            prefillLabel = "예시",
        )
        assertEquals(CategoryAction.PRIORITY, prefill.defaultAction)
    }

    @Test
    fun build_prefill_passes_through_package_and_label() {
        val prefill = UncategorizedPromptPrefillResolver.buildPrefill(
            prefillPackage = "com.example.app",
            prefillLabel = "예시 앱",
        )
        assertEquals("com.example.app", prefill.appPackageName)
        assertEquals("예시 앱", prefill.name)
    }

    @Test
    fun build_prefill_trims_whitespace_and_normalises_blank_label_to_empty_name() {
        val prefill = UncategorizedPromptPrefillResolver.buildPrefill(
            prefillPackage = "  com.example.app  ",
            prefillLabel = "   ",
        )
        assertEquals("com.example.app", prefill.appPackageName)
        assertEquals("", prefill.name)
    }

    @Test
    fun build_prefill_has_no_pending_rule_or_seed_rule_ids() {
        // Home prompt is an app-level signal — the new Category should be
        // an app-pin only. Sender keyword rules are opt-in via the editor's
        // rule selector after the dialog opens.
        val prefill = UncategorizedPromptPrefillResolver.buildPrefill(
            prefillPackage = "com.example.app",
            prefillLabel = "예시",
        )
        assertNull(prefill.pendingRule)
        assertTrue(prefill.seedExistingRuleIds.isEmpty())
    }

    @Test
    fun build_prefill_with_blank_package_yields_null_app_package_name() {
        // Defensive: callers must gate buildPrefill with shouldAutoOpen
        // (which already rejects blank package). If the gating is bypassed
        // we still don't smuggle a stray label as a valid pin.
        val prefill = UncategorizedPromptPrefillResolver.buildPrefill(
            prefillPackage = "  ",
            prefillLabel = "예시",
        )
        assertNull(prefill.appPackageName)
        assertEquals("예시", prefill.name)
    }
}
