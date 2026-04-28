package com.smartnoti.app.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 4 (RED) — pin the [SenderRuleSuggestionCardSpec] copy / labels / PII
 * heuristic before the Composable wires it into NotificationDetailScreen.
 *
 * Following the same JVM-only pattern as [InboxSuggestionCardSpecTest], the
 * Composable correctness is exercised through a pure spec object so this test
 * runs without a Compose runtime. Five guards on `shouldShow`, one on the
 * body string, one on the labels-distinct invariant, one on the deny-list
 * case-insensitive match — eight cases total per plan.
 */
class SenderRuleSuggestionCardSpecTest {

    @Test
    fun bodyFor_inserts_title_in_quotes() {
        val body = SenderRuleSuggestionCardSpec.bodyFor("김동대(Special Recon)")

        assertTrue(
            "body should mention 발신자 prompt, was=$body",
            body.contains("발신자를 항상"),
        )
        assertTrue(
            "body should embed title in quotes, was=$body",
            body.contains("\"김동대(Special Recon)\""),
        )
        assertTrue(
            "body should mention [중요] destination label, was=$body",
            body.contains("[중요]"),
        )
    }

    @Test
    fun shouldShow_returns_false_when_setting_off() {
        val show = SenderRuleSuggestionCardSpec.shouldShow(
            title = "김동대(Special Recon)",
            hasExistingSenderRule = false,
            settingToggleOn = false,
        )
        assertFalse(show)
    }

    @Test
    fun shouldShow_returns_false_when_existing_rule() {
        val show = SenderRuleSuggestionCardSpec.shouldShow(
            title = "김동대(Special Recon)",
            hasExistingSenderRule = true,
            settingToggleOn = true,
        )
        assertFalse(show)
    }

    @Test
    fun shouldShow_returns_false_when_title_blank() {
        val show = SenderRuleSuggestionCardSpec.shouldShow(
            title = "   ",
            hasExistingSenderRule = false,
            settingToggleOn = true,
        )
        assertFalse(show)
    }

    @Test
    fun shouldShow_returns_false_when_title_exceeds_max_length() {
        val tooLong = "가".repeat(SenderRuleSuggestionCardSpec.MAX_TITLE_LENGTH + 1)
        val show = SenderRuleSuggestionCardSpec.shouldShow(
            title = tooLong,
            hasExistingSenderRule = false,
            settingToggleOn = true,
        )
        assertFalse(show)
    }

    @Test
    fun shouldShow_returns_false_when_title_in_deny_list_case_insensitive() {
        val systemSentinel = SenderRuleSuggestionCardSpec.shouldShow(
            title = "ANDROID SYSTEM",
            hasExistingSenderRule = false,
            settingToggleOn = true,
        )
        assertFalse(
            "Android System sentinel must be denied case-insensitively",
            systemSentinel,
        )

        val koreanSentinel = SenderRuleSuggestionCardSpec.shouldShow(
            title = "시스템 알림",
            hasExistingSenderRule = false,
            settingToggleOn = true,
        )
        assertFalse(koreanSentinel)
    }

    @Test
    fun shouldShow_returns_true_when_all_conditions_met() {
        val show = SenderRuleSuggestionCardSpec.shouldShow(
            title = "김동대(Special Recon)",
            hasExistingSenderRule = false,
            settingToggleOn = true,
        )
        assertTrue(show)
    }

    @Test
    fun labels_are_distinct_and_nonempty() {
        val accept = SenderRuleSuggestionCardSpec.LABEL_ACCEPT
        val dismiss = SenderRuleSuggestionCardSpec.LABEL_DISMISS

        assertTrue("accept label must be non-blank", accept.isNotBlank())
        assertTrue("dismiss label must be non-blank", dismiss.isNotBlank())
        assertNotEquals(accept, dismiss)
        assertEquals("예, 중요로", accept)
        assertEquals("무시", dismiss)
    }
}
