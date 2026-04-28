package com.smartnoti.app.ui.screens.detail

/**
 * Plan
 * `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
 * Task 4.
 *
 * Single source of truth for the [SenderRuleSuggestionCard] copy / labels /
 * PII heuristic. The Composable is a thin renderer over this object so
 * `SenderRuleSuggestionCardSpecTest` (JVM, no Compose runtime) pins the
 * user-facing strings and visibility rules for regression safety.
 *
 * `shouldShow` enforces five guards before the card is allowed onto the
 * `NotificationDetailScreen`:
 *  - Settings toggle ON (default true; user can opt out in Settings).
 *  - No matching SENDER rule already exists for this title (avoid asking
 *    the same user for the same sender twice).
 *  - Title is non-blank.
 *  - Title length ≤ [MAX_TITLE_LENGTH] (long headlines are unlikely to be
 *    1:1 DM sender names — heuristic guard against news / promo titles).
 *  - Title is not in [SYSTEM_TITLE_DENY_LIST] (case-insensitive exact
 *    match) — system notifications are never useful sender-rule targets.
 *
 * Per Plan §Risks, the heuristic prefers false-negatives (skip a card we
 * could have shown) over false-positives (offer a SENDER rule for a system
 * notification the user does not want elevated).
 */
object SenderRuleSuggestionCardSpec {

    /**
     * Body copy. Example:
     *   `이 발신자를 항상 [중요]로 분류할까요?\n"김동대(Special Recon)"`.
     * The literal `[중요]` echoes the PRIORITY destination Category label.
     */
    fun bodyFor(title: String): String {
        return "이 발신자를 항상 [중요]로 분류할까요?\n\"$title\""
    }

    /**
     * Returns true when the suggestion card is allowed to render. All five
     * guards must pass — the explicit booleans let the caller pre-compute
     * `hasExistingSenderRule` (rule list scan) and `settingToggleOn`
     * (Settings repo) outside the spec so this function stays pure.
     */
    fun shouldShow(
        title: String,
        hasExistingSenderRule: Boolean,
        settingToggleOn: Boolean,
    ): Boolean {
        if (!settingToggleOn) return false
        if (hasExistingSenderRule) return false
        if (title.isBlank()) return false
        if (title.length > MAX_TITLE_LENGTH) return false
        if (SYSTEM_TITLE_DENY_LIST.any { it.equals(title, ignoreCase = true) }) return false
        return true
    }

    /**
     * Maximum title length the heuristic accepts as a likely 1:1 DM sender
     * name. News headlines / promo titles routinely exceed this — denying
     * them protects users from clicking [LABEL_ACCEPT] on a non-sender.
     */
    const val MAX_TITLE_LENGTH: Int = 60

    /**
     * Exact-match (case-insensitive) deny list of system-notification
     * titles. Additional sentinels surfaced in ADB e2e are added here in
     * later tasks.
     */
    val SYSTEM_TITLE_DENY_LIST: Set<String> = setOf(
        "Android System",
        "System notifications",
        "시스템 알림",
        "system_alerts",
    )

    const val LABEL_ACCEPT: String = "예, 중요로"
    const val LABEL_DISMISS: String = "무시"
}
