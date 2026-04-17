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
    data object Insight : Routes("insight/{filterType}/{filterValue}") {
        fun createForApp(appName: String): String = "insight/app/${encodeRouteParam(appName)}"
        fun createForReason(reasonTag: String): String = "insight/reason/${encodeRouteParam(reasonTag)}"
    }
}

private fun encodeRouteParam(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")
}
