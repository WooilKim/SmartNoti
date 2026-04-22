package com.smartnoti.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val label: String,
    val route: String,
    val icon: ImageVector
)

// Priority (중요) tab was removed in Rules UX v2 Phase A Task 4 — reachable via
// Home "검토 대기" passthrough card.
//
// Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
// Task 11 collapses the bar to four entries:
//   Home / 정리함 / 분류 / Settings.
// Digest + Hidden are now hosted by the 정리함 tab (InboxScreen) as segmented
// sub-sections. The legacy 규칙 tab is demoted to a Settings sub-menu entry
// ("고급 규칙 편집"); the underlying route still exists for deep links.
val bottomNavItems = listOf(
    BottomNavItem("홈", Routes.Home.route, Icons.Outlined.Home),
    BottomNavItem("정리함", Routes.Inbox.route, Icons.Outlined.Inbox),
    BottomNavItem("분류", Routes.Categories.route, Icons.Outlined.Category),
    BottomNavItem("설정", Routes.Settings.route, Icons.Outlined.Settings),
)
