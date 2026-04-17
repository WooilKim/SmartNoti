package com.smartnoti.app.navigation

class TopLevelNavigationActionBuilder {
    fun build(
        currentRoute: String?,
        targetRoute: String,
        startRoute: String,
    ): TopLevelNavigationAction {
        if (currentRoute == targetRoute) {
            return TopLevelNavigationAction.NoOp
        }

        return if (targetRoute == startRoute) {
            TopLevelNavigationAction.PopToExisting(targetRoute)
        } else {
            TopLevelNavigationAction.Navigate(targetRoute)
        }
    }
}

sealed interface TopLevelNavigationAction {
    data object NoOp : TopLevelNavigationAction
    data class Navigate(val route: String) : TopLevelNavigationAction
    data class PopToExisting(val route: String) : TopLevelNavigationAction
}
