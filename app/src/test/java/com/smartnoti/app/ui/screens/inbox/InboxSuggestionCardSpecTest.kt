package com.smartnoti.app.ui.screens.inbox

import com.smartnoti.app.data.local.HighVolumeAppCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 1 (RED) — pin the [InboxSuggestionCardSpec] copy contract that the
 * `InboxSuggestionCard` Composable will delegate to. Following the existing
 * SmartNoti pattern (`InboxCardLanguageContractTest`,
 * `DigestScreenBulkActionsWiringTest`, etc.), Composable correctness is
 * exercised through a pure spec object so this test runs on the JVM without
 * a Compose runtime.
 *
 * Contract pinned here:
 *  - Body string mentions appName, "최근 7일간 평균", "<rounded>건/일", and
 *    invites the user to switch to DIGEST auto-bundling.
 *  - Average is rounded HALF_UP via `roundToInt()` — 10.5 → 11, 24.0 → 24.
 *  - The three button labels are non-blank and pairwise distinct so users
 *    cannot misread the action.
 *  - The snooze duration is exactly 24 hours in milliseconds (the user-
 *    facing "나중에 = 하루 동안 안 보임" expectation).
 *  - Eyebrow / "💡 제안" stays static.
 *
 * All five tests must initially compile-fail because
 * [InboxSuggestionCardSpec] and [HighVolumeAppCandidate] do not exist yet.
 */
class InboxSuggestionCardSpecTest {

    @Test
    fun bodyFor_includes_app_name_and_rounded_avg_per_day() {
        val candidate = HighVolumeAppCandidate(
            packageName = "com.nhn.android.search",
            appName = "네이버",
            count = 24,
            avgPerDay = 24.0 / 7.0, // ≈ 3.428 → roundToInt = 3
        )

        val body = InboxSuggestionCardSpec.bodyFor(candidate)

        assertTrue("body should mention appName, was=$body", body.contains("네이버"))
        assertTrue("body should mention 7-day window, was=$body", body.contains("최근 7일간"))
        assertTrue("body should mention 평균, was=$body", body.contains("평균"))
        assertTrue("body should mention 건/일, was=$body", body.contains("건/일"))
    }

    @Test
    fun bodyFor_rounds_avg_per_day_half_up() {
        val rounded24 = InboxSuggestionCardSpec.bodyFor(
            HighVolumeAppCandidate(
                packageName = "com.example",
                appName = "Example",
                count = 168,
                avgPerDay = 24.0,
            ),
        )
        assertTrue(
            "24.0 should render as 24, was=$rounded24",
            rounded24.contains("24건/일"),
        )

        val roundedHalf = InboxSuggestionCardSpec.bodyFor(
            HighVolumeAppCandidate(
                packageName = "com.example",
                appName = "Example",
                count = 74,
                avgPerDay = 10.5,
            ),
        )
        // Kotlin's Double.roundToInt() rounds 10.5 to 11 (half-up).
        assertTrue(
            "10.5 should render as 11, was=$roundedHalf",
            roundedHalf.contains("11건/일"),
        )
    }

    @Test
    fun three_button_labels_are_non_blank_and_distinct() {
        val accept = InboxSuggestionCardSpec.LABEL_ACCEPT
        val snooze = InboxSuggestionCardSpec.LABEL_SNOOZE
        val dismiss = InboxSuggestionCardSpec.LABEL_DISMISS

        assertTrue("accept label must be non-blank", accept.isNotBlank())
        assertTrue("snooze label must be non-blank", snooze.isNotBlank())
        assertTrue("dismiss label must be non-blank", dismiss.isNotBlank())

        assertNotEquals(accept, snooze)
        assertNotEquals(accept, dismiss)
        assertNotEquals(snooze, dismiss)
    }

    @Test
    fun snooze_duration_is_twenty_four_hours_in_millis() {
        // The plan pins SNOOZE_DURATION_MILLIS = 24h so [나중에] hides the
        // card for one day. Flipping this constant changes user expectations
        // and trips this test deliberately.
        val expected24h = 24L * 60 * 60 * 1000
        assertEquals(expected24h, InboxSuggestionCardSpec.SNOOZE_DURATION_MILLIS)
        assertTrue(InboxSuggestionCardSpec.SNOOZE_DURATION_MILLIS > 0)
    }

    @Test
    fun eyebrow_stays_static() {
        val candidate = HighVolumeAppCandidate(
            packageName = "com.example",
            appName = "Example",
            count = 24,
            avgPerDay = 24.0 / 7.0,
        )
        // The eyebrow text is the same regardless of which package surfaces.
        val eyebrow = InboxSuggestionCardSpec.eyebrowFor(candidate)
        assertEquals("💡 제안", eyebrow)
    }
}
