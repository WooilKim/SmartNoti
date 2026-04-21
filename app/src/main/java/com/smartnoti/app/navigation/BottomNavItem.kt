package com.smartnoti.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)

// Priority (중요) tab removed in Rules UX v2 Phase A Task 4. The Priority
// composable is still registered in AppNavHost and reachable via the Home
// "검토 대기" passthrough card, so the user journey keeps working without a
// dedicated bottom-nav slot. See docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md.
val bottomNavItems = listOf(
    BottomNavItem("홈", Routes.Home.route, Icons.Outlined.Home),
    BottomNavItem("정리함", Routes.Digest.route, Icons.Outlined.List),
    BottomNavItem("규칙", Routes.Rules.route, Icons.Outlined.Rule),
    BottomNavItem("설정", Routes.Settings.route, Icons.Outlined.Settings),
)
