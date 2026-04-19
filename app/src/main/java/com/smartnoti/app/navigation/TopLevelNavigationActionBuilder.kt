package com.smartnoti.app.navigation

class TopLevelNavigationActionBuilder {
    fun build(
        currentRoute: String?,
        targetRoute: String,
    ): TopLevelNavigationAction {
        if (currentRoute == targetRoute) {
            return TopLevelNavigationAction.NoOp
        }

        return TopLevelNavigationAction.Navigate(targetRoute)
    }
}

sealed interface TopLevelNavigationAction {
    data object NoOp : TopLevelNavigationAction
    data class Navigate(val route: String) : TopLevelNavigationAction
}
