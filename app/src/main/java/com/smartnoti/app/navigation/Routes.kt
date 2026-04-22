package com.smartnoti.app.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Routes(val route: String) {
    data object Onboarding : Routes("onboarding")
    data object Home : Routes("home")
    data object Priority : Routes("priority")
    data object Digest : Routes("digest")
    data object Rules : Routes("rules?highlightRuleId={highlightRuleId}") {
        /**
         * Deep-link into the Rules tab, optionally asking the screen to scroll
         * to and highlight a specific rule id. Used by the Detail screen's
         * "적용된 규칙" chips (plan `rules-ux-v2-inbox-restructure` Phase B
         * Task 3).
         *
         * - No arg ⇒ `"rules"`, which matches the route pattern because the
         *   `highlightRuleId` nav arg declares a `null` default in [AppNavHost].
         * - Blank strings are treated as absent so an empty query param can't
         *   ship through and drive the Rules screen to chase a nonexistent id.
         */
        fun create(highlightRuleId: String? = null): String {
            val trimmed = highlightRuleId?.trim().orEmpty()
            return if (trimmed.isEmpty()) {
                "rules"
            } else {
                "rules?highlightRuleId=${encodeRouteParam(trimmed)}"
            }
        }
    }
    /**
     * 분류 (Categories) primary tab — plan
     * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
     * Task 8. The list + editor live on this route; the Task 11 bottom-nav
     * reshape (removing Rules in favour of a 4-tab layout) is tracked
     * separately.
     */
    data object Categories : Routes("categories")
    data object Settings : Routes("settings")
    data object Hidden : Routes("hidden?sender={sender}&packageName={packageName}") {
        /**
         * Build a Hidden-screen destination URL.
         *
         * - No args ⇒ `"hidden"`, which matches the route pattern because both `{sender}`
         *   and `{packageName}` nav args declare defaults of `null` in [AppNavHost].
         * - `sender` wins over `packageName` when both are supplied — same precedence as
         *   [com.smartnoti.app.domain.usecase.SilentGroupKey], so callers can't smuggle a
         *   "sender + app" combined filter that the screen wouldn't know how to honour.
         * - Blank strings are treated as absent so a percent-encoded space cannot ship
         *   through as a filter the Hidden screen can never satisfy.
         *
         * Invoked by the tray group-summary contentIntent
         * (`SilentHiddenSummaryNotifier.createGroupContentIntent`) via [MainActivity]'s
         * deep-link extraction.
         */
        fun create(sender: String? = null, packageName: String? = null): String {
            val trimmedSender = sender?.trim().orEmpty()
            val trimmedPackage = packageName?.trim().orEmpty()
            return when {
                trimmedSender.isNotEmpty() ->
                    "hidden?sender=${encodeRouteParam(trimmedSender)}"
                trimmedPackage.isNotEmpty() ->
                    "hidden?packageName=${encodeRouteParam(trimmedPackage)}"
                else -> "hidden"
            }
        }
    }
    data object Detail : Routes("detail/{notificationId}") {
        fun create(notificationId: String): String = "detail/${encodeRouteParam(notificationId)}"
    }
    /**
     * Opt-in 무시됨 아카이브 screen (plan
     * `2026-04-21-ignore-tier-fourth-decision` Task 6). The route is only
     * registered with the nav graph when `SmartNotiSettings.showIgnoredArchive`
     * is true — the Settings toggle is the sole entry point. The archive is a
     * secondary route reached from Settings (no bottom-nav surface).
     */
    data object IgnoredArchive : Routes("ignored_archive")

    data object Insight : Routes("insight/{filterType}/{filterValue}?range={range}&source={source}") {
        fun createForApp(appName: String, range: String? = null, source: String? = null): String {
            return buildInsightRoute(filterType = "app", filterValue = appName, range = range, source = source)
        }

        fun createForReason(reasonTag: String, range: String? = null, source: String? = null): String {
            return buildInsightRoute(filterType = "reason", filterValue = reasonTag, range = range, source = source)
        }
    }
}

private fun encodeRouteParam(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")
}

private fun buildInsightRoute(filterType: String, filterValue: String, range: String?, source: String?): String {
    val base = "insight/$filterType/${encodeRouteParam(filterValue)}"
    val queryParams = buildList {
        if (!range.isNullOrBlank()) add("range=${encodeRouteParam(range)}")
        if (!source.isNullOrBlank()) add("source=${encodeRouteParam(source)}")
    }
    return if (queryParams.isEmpty()) {
        base
    } else {
        "$base?${queryParams.joinToString("&")}"
    }
}
