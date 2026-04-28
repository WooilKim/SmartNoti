package com.smartnoti.app.ui.screens.inbox

import com.smartnoti.app.data.local.HighVolumeAppCandidate
import kotlin.math.roundToInt

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 6.
 *
 * Single source of truth for the [InboxSuggestionCard] copy / labels /
 * snooze duration. The Composable is a thin renderer over this object so
 * `InboxSuggestionCardSpecTest` (JVM, no Compose runtime) pins the user-
 * facing strings and constants for regression safety.
 *
 * Plan §Visual guidelines / Copy choices:
 *  - Eyebrow `"💡 제안"` is static (does not vary per candidate).
 *  - Body mentions `appName`, `"최근 7일간 평균"`, the rounded daily count, and
 *    invites the user to switch to DIGEST auto-bundling. Average is rounded
 *    half-up via `Double.roundToInt()`.
 *  - Three buttons are `예, 묶을게요` / `나중에` / `무시` in row order.
 *  - `[나중에]` snooze duration is exactly 24h in millis.
 */
object InboxSuggestionCardSpec {

    /** Static eyebrow shown above the body. Never varies per candidate. */
    fun eyebrowFor(@Suppress("UNUSED_PARAMETER") candidate: HighVolumeAppCandidate): String {
        return EYEBROW
    }

    /**
     * Body copy. Example: "네이버에서 최근 7일간 평균 24건/일이 와있어요. 자동 묶음 처리 (DIGEST) 로 변경할까요?".
     * Average rounded half-up (10.5 → 11, 24.0 → 24).
     */
    fun bodyFor(candidate: HighVolumeAppCandidate): String {
        val rounded = candidate.avgPerDay.roundToInt()
        return "${candidate.appName}에서 최근 7일간 평균 ${rounded}건/일이 와있어요. " +
            "자동 묶음 처리 (DIGEST) 로 변경할까요?"
    }

    const val EYEBROW: String = "💡 제안"
    const val LABEL_ACCEPT: String = "예, 묶을게요"
    const val LABEL_SNOOZE: String = "나중에"
    const val LABEL_DISMISS: String = "무시"

    /**
     * 24h in millis. Pinned by
     * `InboxSuggestionCardSpecTest.snooze_duration_is_twenty_four_hours_in_millis` —
     * flipping this changes user-facing snooze UX and trips that test.
     */
    const val SNOOZE_DURATION_MILLIS: Long = 24L * 60L * 60L * 1000L
}
