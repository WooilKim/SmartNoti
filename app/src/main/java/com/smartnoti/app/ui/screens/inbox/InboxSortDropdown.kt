package com.smartnoti.app.ui.screens.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.InboxSortMode

/**
 * Plan `docs/plans/2026-04-27-inbox-sort-by-priority-or-app.md` Task 3.
 *
 * Compact dropdown row that sits between the unified inbox `ScreenHeader` and
 * the `InboxTabRow`, exposing the user's [InboxSortMode] selection. Tapping the
 * row opens a Material3 [DropdownMenu] with three options
 * (`최신순 / 중요도순 / 앱별 묶기`); selecting an option fires [onSelect] and
 * dismisses the menu.
 *
 * The label-mapping helper [labelFor] is intentionally separate so it can be
 * exercised by a pure JVM unit test without spinning up a Compose runtime —
 * the dropdown's three-mode contract is a regression we want guarded long-term
 * (`InboxSortDropdownLabelTest`, plan Task 4).
 */
@Composable
fun InboxSortDropdown(
    currentMode: InboxSortMode,
    onSelect: (InboxSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "정렬:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = labelFor(currentMode),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "정렬 모드 선택",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            InboxSortMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = { Text(labelFor(mode)) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Pure label-mapping helper for [InboxSortMode].
 *
 * Kept separate from the Composable so a JVM unit test can pin the contract
 * without instantiating Compose. Adding a new [InboxSortMode] enum value is
 * intentionally a compile error here — the `when` is exhaustive.
 */
fun labelFor(mode: InboxSortMode): String = when (mode) {
    InboxSortMode.RECENT -> "최신순"
    InboxSortMode.IMPORTANCE -> "중요도순"
    InboxSortMode.BY_APP -> "앱별 묶기"
}
