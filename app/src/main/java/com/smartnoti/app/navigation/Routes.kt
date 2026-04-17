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
    data object Detail : Routes("detail/{notificationId}") {
        fun create(notificationId: String): String = "detail/${encodeRouteParam(notificationId)}"
    }
    data object Insight : Routes("insight/{filterType}/{filterValue}?range={range}") {
        fun createForApp(appName: String, range: String? = null): String {
            return buildInsightRoute(filterType = "app", filterValue = appName, range = range)
        }

        fun createForReason(reasonTag: String, range: String? = null): String {
            return buildInsightRoute(filterType = "reason", filterValue = reasonTag, range = range)
        }
    }
}

private fun encodeRouteParam(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")
}

private fun buildInsightRoute(filterType: String, filterValue: String, range: String?): String {
    val base = "insight/$filterType/${encodeRouteParam(filterValue)}"
    return if (range.isNullOrBlank()) {
        base
    } else {
        "$base?range=${encodeRouteParam(range)}"
    }
}
