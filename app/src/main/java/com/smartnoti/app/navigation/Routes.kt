package com.smartnoti.app.navigation

import android.net.Uri

sealed class Routes(val route: String) {
    data object Onboarding : Routes("onboarding")
    data object Home : Routes("home")
    data object Priority : Routes("priority")
    data object Digest : Routes("digest")
    data object Rules : Routes("rules")
    data object Settings : Routes("settings")
    data object Detail : Routes("detail/{notificationId}") {
        fun create(notificationId: String): String = "detail/${Uri.encode(notificationId)}"
    }
}
