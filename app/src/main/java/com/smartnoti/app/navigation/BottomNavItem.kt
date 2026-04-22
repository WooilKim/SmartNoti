package com.smartnoti.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
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
// Rules uses `Routes.Rules.create()` (i.e. the bare "rules" URL) so bottom-nav
// taps land on the unfiltered list. The route *pattern* (with the optional
// `highlightRuleId` query placeholder) lives in `Routes.Rules.route` and is what
// AppNavHost registers against; when `create()` emits "rules", nav-compose
// resolves it to the same composable via the query param's `null` default.
// Plan `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3
// Task 8 introduces the "분류" primary tab. Task 11 will remove the legacy
// "규칙" tab and collapse the bottom bar to four items — until then both
// surfaces coexist so Phase P3 can land incrementally without stranding
// users mid-migration.
val bottomNavItems = listOf(
    BottomNavItem("홈", Routes.Home.route, Icons.Outlined.Home),
    BottomNavItem("정리함", Routes.Digest.route, Icons.Outlined.List),
    BottomNavItem("분류", Routes.Categories.route, Icons.Outlined.Category),
    BottomNavItem("규칙", Routes.Rules.create(), Icons.Outlined.Rule),
    BottomNavItem("설정", Routes.Settings.route, Icons.Outlined.Settings),
)
