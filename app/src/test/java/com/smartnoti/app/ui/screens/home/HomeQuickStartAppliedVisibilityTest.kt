package com.smartnoti.app.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
 * Task 10 Home declutter: `HomeQuickStartAppliedCard` is demoted with a 7-day
 * TTL + tap-to-ack. The visibility gate is a pure function so the Home
 * composable can remember the decision without a ViewModel round-trip.
 */
class HomeQuickStartAppliedVisibilityTest {

    private val sevenDays = HomeQuickStartAppliedVisibility.TTL_MILLIS
    private val now = 1_700_000_000_000L

    @Test
    fun hides_card_when_summary_is_null() {
        assertFalse(
            HomeQuickStartAppliedVisibility.shouldShow(
                hasSummary = false,
                acknowledgedAtMillis = 0L,
                nowMillis = now,
            )
        )
    }

    @Test
    fun shows_card_when_summary_present_and_never_acknowledged() {
        assertTrue(
            HomeQuickStartAppliedVisibility.shouldShow(
                hasSummary = true,
                acknowledgedAtMillis = 0L,
                nowMillis = now,
            )
        )
    }

    @Test
    fun hides_card_within_ttl_after_acknowledgement() {
        assertFalse(
            HomeQuickStartAppliedVisibility.shouldShow(
                hasSummary = true,
                acknowledgedAtMillis = now - (sevenDays - 1L),
                nowMillis = now,
            )
        )
    }

    @Test
    fun shows_card_again_after_ttl_elapses() {
        assertTrue(
            HomeQuickStartAppliedVisibility.shouldShow(
                hasSummary = true,
                acknowledgedAtMillis = now - sevenDays,
                nowMillis = now,
            )
        )
    }

    @Test
    fun hides_when_summary_absent_even_if_ttl_elapsed() {
        assertFalse(
            HomeQuickStartAppliedVisibility.shouldShow(
                hasSummary = false,
                acknowledgedAtMillis = now - sevenDays - 1L,
                nowMillis = now,
            )
        )
    }
}
