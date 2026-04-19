package com.smartnoti.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.usecase.InsightDrillDownSource
import com.smartnoti.app.notification.OnboardingActiveNotificationBootstrapCoordinator
import com.smartnoti.app.ui.components.AppBottomBar
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.smartnoti.app.ui.screens.detail.InsightDrillDownScreen
import com.smartnoti.app.ui.screens.detail.NotificationDetailScreen
import com.smartnoti.app.ui.screens.digest.DigestScreen
import com.smartnoti.app.ui.screens.home.HomeScreen
import com.smartnoti.app.ui.screens.onboarding.OnboardingScreen
import com.smartnoti.app.ui.screens.priority.PriorityScreen
import com.smartnoti.app.ui.screens.rules.RulesScreen
import com.smartnoti.app.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    pendingNotificationEntry: ReplacementNotificationEntry? = null,
    onPendingNotificationConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository.getInstance(context) }
    val onboardingBootstrapCoordinator = remember {
        OnboardingActiveNotificationBootstrapCoordinator.create(context)
    }
    val completed: Boolean? by produceState<Boolean?>(initialValue = null, settings) {
        settings.observeOnboardingCompleted().collect { value = it }
    }

    val onboardingCompleted = completed ?: run {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    val navController = rememberNavController()
    val navigationScope = rememberCoroutineScope()
    val navigationActionBuilder = remember { TopLevelNavigationActionBuilder() }
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = currentRoute != null && currentRoute != Routes.Onboarding.route
    val startDestination = if (onboardingCompleted) Routes.Home.route else Routes.Onboarding.route

    LaunchedEffect(onboardingCompleted, currentRoute) {
        when {
            onboardingCompleted && currentRoute == Routes.Onboarding.route -> {
                navController.navigate(Routes.Home.route) {
                    popUpTo(Routes.Onboarding.route) { inclusive = true }
                    launchSingleTop = true
                }
            }
            !onboardingCompleted && currentRoute != null && currentRoute != Routes.Onboarding.route -> {
                navController.navigate(Routes.Onboarding.route) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(pendingNotificationEntry, onboardingCompleted) {
        val pendingEntry = pendingNotificationEntry
        if (onboardingCompleted && pendingEntry != null) {
            navController.navigate(pendingEntry.parentRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            navController.navigate(Routes.Detail.create(pendingEntry.notificationId)) {
                launchSingleTop = true
            }
            onPendingNotificationConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    items = bottomNavItems,
                    onNavigate = { route ->
                        when (
                            val action = navigationActionBuilder.build(
                                currentRoute = currentRoute,
                                targetRoute = route,
                                startRoute = startDestination,
                            )
                        ) {
                            TopLevelNavigationAction.NoOp -> Unit
                            is TopLevelNavigationAction.PopToExisting -> {
                                navController.popBackStack(action.route, inclusive = false)
                            }
                            is TopLevelNavigationAction.Navigate -> {
                                navController.navigate(action.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Routes.Onboarding.route) {
                OnboardingScreen(
                    onCompleted = {
                        navigationScope.launch {
                            settings.setOnboardingCompleted(true)
                            onboardingBootstrapCoordinator.requestBootstrapForFirstOnboardingCompletion()
                            navController.navigate(Routes.Home.route) {
                                popUpTo(Routes.Onboarding.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
            composable(Routes.Home.route) {
                HomeScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) },
                    onPriorityClick = { navController.navigate(Routes.Priority.route) },
                    onDigestClick = { navController.navigate(Routes.Digest.route) },
                    onNotificationAccessClick = { navController.navigate(Routes.Settings.route) },
                    onRulesClick = { navController.navigate(Routes.Rules.route) },
                    onInsightClick = { navController.navigate(it) },
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
                SettingsScreen(
                    contentPadding = paddingValues,
                    onInsightClick = { navController.navigate(it) },
                )
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
            composable(
                route = Routes.Insight.route,
                arguments = listOf(
                    navArgument("filterType") { type = NavType.StringType },
                    navArgument("filterValue") { type = NavType.StringType },
                    navArgument("range") {
                        type = NavType.StringType
                        defaultValue = "recent_24_hours"
                        nullable = false
                    },
                    navArgument("source") {
                        type = NavType.StringType
                        defaultValue = InsightDrillDownSource.GENERAL.routeValue
                        nullable = false
                    },
                )
            ) { backStackEntry ->
                InsightDrillDownScreen(
                    contentPadding = paddingValues,
                    filterType = backStackEntry.arguments?.getString("filterType").orEmpty(),
                    filterValue = backStackEntry.arguments?.getString("filterValue").orEmpty(),
                    initialRange = backStackEntry.arguments?.getString("range").orEmpty(),
                    source = backStackEntry.arguments?.getString("source").orEmpty(),
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) },
                    onInsightClick = { navController.navigate(it) },
                )
            }
        }
    }
}
