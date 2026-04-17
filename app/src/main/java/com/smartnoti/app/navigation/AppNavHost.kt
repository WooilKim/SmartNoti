package com.smartnoti.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartnoti.app.ui.components.AppBottomBar
import com.smartnoti.app.ui.screens.detail.NotificationDetailScreen
import com.smartnoti.app.ui.screens.digest.DigestScreen
import com.smartnoti.app.ui.screens.home.HomeScreen
import com.smartnoti.app.ui.screens.onboarding.OnboardingScreen
import com.smartnoti.app.ui.screens.priority.PriorityScreen
import com.smartnoti.app.ui.screens.rules.RulesScreen
import com.smartnoti.app.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showBottomBar = currentRoute != Routes.Onboarding.route

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    items = bottomNavItems,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Routes.Onboarding.route,
        ) {
            composable(Routes.Onboarding.route) {
                OnboardingScreen(
                    onContinue = {
                        navController.navigate(Routes.Home.route) {
                            popUpTo(Routes.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Routes.Home.route) {
                HomeScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) },
                    onPriorityClick = { navController.navigate(Routes.Priority.route) },
                    onDigestClick = { navController.navigate(Routes.Digest.route) }
                )
            }
            composable(Routes.Priority.route) {
                PriorityScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) }
                )
            }
            composable(Routes.Digest.route) {
                DigestScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) }
                )
            }
            composable(Routes.Rules.route) {
                RulesScreen(contentPadding = paddingValues)
            }
            composable(Routes.Settings.route) {
                SettingsScreen(contentPadding = paddingValues)
            }
            composable(
                route = Routes.Detail.route,
                arguments = listOf(navArgument("notificationId") { type = NavType.StringType })
            ) { backStackEntry ->
                NotificationDetailScreen(
                    contentPadding = paddingValues,
                    notificationId = backStackEntry.arguments?.getString("notificationId").orEmpty()
                )
            }
        }
    }
}
