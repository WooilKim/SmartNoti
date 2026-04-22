package com.smartnoti.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.smartnoti.app.ui.screens.categories.CategoriesScreen
import com.smartnoti.app.ui.screens.digest.DigestScreen
import com.smartnoti.app.ui.screens.hidden.HiddenDeepLinkFilterResolver
import com.smartnoti.app.ui.screens.hidden.HiddenNotificationsScreen
import com.smartnoti.app.ui.screens.hidden.HiddenScreenMode
import com.smartnoti.app.ui.screens.home.HomeScreen
import com.smartnoti.app.ui.screens.ignored.IgnoredArchiveScreen
import com.smartnoti.app.ui.screens.inbox.InboxScreen
import com.smartnoti.app.ui.screens.onboarding.OnboardingScreen
import com.smartnoti.app.ui.screens.priority.PriorityScreen
import com.smartnoti.app.ui.screens.rules.RulesScreen
import com.smartnoti.app.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    pendingNotificationEntry: ReplacementNotificationEntry? = null,
    onPendingNotificationConsumed: () -> Unit = {},
    pendingDeepLinkRoute: String? = null,
    onPendingDeepLinkRouteConsumed: () -> Unit = {},
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

    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6: the 무시됨 아카이브
    // route is only registered in the nav graph when the opt-in toggle is on.
    // We observe a minimal flat Boolean here so the nav host re-composes when
    // the user flips the toggle in Settings; callers that need full settings
    // state still read SettingsRepository.observeSettings() directly.
    val showIgnoredArchive: Boolean by produceState(initialValue = false, settings) {
        settings.observeSettings().collect { value = it.showIgnoredArchive }
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

    LaunchedEffect(pendingDeepLinkRoute, onboardingCompleted) {
        val route = pendingDeepLinkRoute
        if (onboardingCompleted && route != null) {
            navController.navigate(route) {
                launchSingleTop = true
            }
            onPendingDeepLinkRouteConsumed()
        }
    }

    fun navigateToTopLevel(route: String) {
        val activeRoute = navController.currentBackStackEntry?.destination?.route
        when (
            val action = navigationActionBuilder.build(
                currentRoute = activeRoute,
                targetRoute = route,
            )
        ) {
            TopLevelNavigationAction.NoOp -> Unit
            is TopLevelNavigationAction.Navigate -> {
                val startDestination = navController.graph.findStartDestination()
                if (action.route == startDestination.route) {
                    // `navigate(start) { popUpTo(start) saveState; launchSingleTop; restoreState }`
                    // is a silent no-op for some back-stack shapes on nav-compose 2.7.x
                    // (observed when the current destination was reached via a deep link
                    // rather than a user tap, e.g. entering Hidden from the silent-summary
                    // notification and then tapping the Home tab). Pop back to the start
                    // destination directly so the intent survives that code path.
                    navController.popBackStack(startDestination.id, inclusive = false, saveState = true)
                } else {
                    navController.navigate(action.route) {
                        popUpTo(startDestination.id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
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
                    onNavigate = ::navigateToTopLevel,
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = 160)) },
            popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 160)) },
            popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 160)) },
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
                    onPriorityClick = { navigateToTopLevel(Routes.Priority.route) },
                    onNotificationAccessClick = { navigateToTopLevel(Routes.Settings.route) },
                    onRulesClick = { navigateToTopLevel(Routes.Rules.route) },
                    onInsightClick = { navController.navigate(it) },
                    onCreateCategoryClick = { navigateToTopLevel(Routes.Categories.route) },
                    onSeeAllRecent = { navigateToTopLevel(Routes.Inbox.route) },
                )
            }
            composable(Routes.Priority.route) {
                PriorityScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) },
                    onCreateRuleClick = { _ ->
                        // Phase A Task 3 carves the inline "→ 규칙 만들기" entry point.
                        // For v1 it lands in the Rules tab; a dedicated rule-editor deep
                        // link is a Phase C task (hierarchical rules editor).
                        navigateToTopLevel(Routes.Rules.route)
                    },
                )
            }
            composable(Routes.Digest.route) {
                DigestScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) }
                )
            }
            // Plan `docs/plans/2026-04-22-categories-split-rules-actions.md`
            // Phase P3 Task 11 — 정리함 (Inbox) hosts Digest + Hidden in a
            // single screen with segmented sub-tabs. Legacy `Digest` / `Hidden`
            // routes remain registered above/below so deep-link tray
            // contentIntents keep working.
            composable(Routes.Inbox.route) {
                InboxScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.Rules.route,
                arguments = listOf(
                    navArgument("highlightRuleId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                RulesScreen(
                    contentPadding = paddingValues,
                    highlightRuleId = backStackEntry.arguments?.getString("highlightRuleId"),
                )
            }
            // 분류 (Categories) primary tab — plan Phase P3 Task 8.
            composable(Routes.Categories.route) {
                CategoriesScreen(
                    contentPadding = paddingValues,
                )
            }
            composable(Routes.Settings.route) {
                SettingsScreen(
                    contentPadding = paddingValues,
                    onInsightClick = { navController.navigate(it) },
                    onOpenIgnoredArchive = if (IgnoredArchiveNavGate.isButtonVisible(showIgnoredArchive)) {
                        {
                            navController.navigate(Routes.IgnoredArchive.route) {
                                launchSingleTop = true
                            }
                        }
                    } else {
                        null
                    },
                    // Plan `docs/plans/2026-04-22-categories-split-rules-actions.md`
                    // Phase P3 Task 11 demotes Rules from a top-level BottomNav
                    // entry to a Settings sub-menu. The "고급 규칙 편집" row
                    // in Settings uses this callback to navigate into the
                    // existing Rules screen via its stable route.
                    onOpenAdvancedRules = {
                        navController.navigate(Routes.Rules.create()) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            // Route registration contract lives in [IgnoredArchiveNavGate].
            // Plan `2026-04-22-ignored-archive-first-tap-nav-race` Task 2
            // (Option A): the route is registered unconditionally so the
            // Settings button lambda and the nav graph can never be gated by
            // two independently-observed reads of `showIgnoredArchive`. Entry
            // UX gating stays on the Settings button lambda above; no deep
            // link currently targets this route, so exposure is equivalent.
            composable(Routes.IgnoredArchive.route) {
                IgnoredArchiveScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) },
                )
            }
            composable(
                route = Routes.Hidden.route,
                arguments = listOf(
                    navArgument("sender") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("packageName") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                )
            ) { backStackEntry ->
                val initialFilter = HiddenDeepLinkFilterResolver.resolve(
                    sender = backStackEntry.arguments?.getString("sender"),
                    packageName = backStackEntry.arguments?.getString("packageName"),
                )
                HiddenNotificationsScreen(
                    contentPadding = paddingValues,
                    onNotificationClick = { navController.navigate(Routes.Detail.create(it)) },
                    onBack = { navController.popBackStack() },
                    mode = HiddenScreenMode.Standalone(initialFilter = initialFilter),
                )
            }
            composable(
                route = Routes.Detail.route,
                arguments = listOf(navArgument("notificationId") { type = NavType.StringType })
            ) { backStackEntry ->
                NotificationDetailScreen(
                    contentPadding = paddingValues,
                    notificationId = backStackEntry.arguments?.getString("notificationId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onRuleClick = { ruleId ->
                        // Phase B Task 3: "적용된 규칙" chips deep-link into the
                        // Rules tab with the tapped rule's id so the screen
                        // scrolls-to and flashes the matching row.
                        navController.navigate(Routes.Rules.create(highlightRuleId = ruleId)) {
                            launchSingleTop = true
                        }
                    },
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
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
