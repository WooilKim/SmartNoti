package com.smartnoti.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem("홈", Routes.Home.route, Icons.Outlined.Home),
    BottomNavItem("중요", Routes.Priority.route, Icons.Outlined.Notifications),
    BottomNavItem("정리함", Routes.Digest.route, Icons.Outlined.List),
    BottomNavItem("규칙", Routes.Rules.route, Icons.Outlined.Rule),
    BottomNavItem("설정", Routes.Settings.route, Icons.Outlined.Settings),
)
