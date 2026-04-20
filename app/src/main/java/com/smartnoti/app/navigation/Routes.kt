package com.smartnoti.app.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Routes(val route: String) {
    data object Onboarding : Routes("onboarding")
    data object Home : Routes("home")
    data object Priority : Routes("priority")
    data object Digest : Routes("digest")
    data object Rules : Routes("rules")
    data object Settings : Routes("settings")
    data object Hidden : Routes("hidden")
    data object Detail : Routes("detail/{notificationId}") {
        fun create(notificationId: String): String = "detail/${encodeRouteParam(notificationId)}"
    }
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
