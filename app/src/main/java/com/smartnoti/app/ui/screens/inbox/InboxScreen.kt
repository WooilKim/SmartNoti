package com.smartnoti.app.ui.screens.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.data.local.toHiddenGroups
import com.smartnoti.app.domain.model.InboxSortMode
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.screens.digest.DigestScreen
import com.smartnoti.app.ui.screens.hidden.HiddenNotificationsScreen
import com.smartnoti.app.ui.screens.hidden.HiddenScreenMode
import com.smartnoti.app.ui.theme.BorderSubtle
import kotlinx.coroutines.launch

/**
 * 정리함 (Inbox) screen — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11.
 *
 * One screen, three sub-tabs:
 * - **Digest** groups (reuses [DigestScreen]'s content)
 * - **보관 중** ARCHIVED SILENT (reuses [HiddenNotificationsScreen] ARCHIVED
 *   tab content)
 * - **처리됨** PROCESSED SILENT (reuses HiddenNotificationsScreen PROCESSED)
 *
 * The screen avoids re-implementing list/state logic by delegating each
 * sub-tab to the existing screen composable. Header and sub-tab segment live
 * here; the nested screens supply their own bodies and bulk actions.
 *
 * Deep links into the legacy Digest / Hidden routes still work — AppNavHost
 * keeps them registered so tray group-summary contentIntents and onboarding
 * flows don't break mid-migration.
 */
@Composable
fun InboxScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = SmartNotiSettings())
    // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 3.
    // Resolve the persisted sort mode once per recomposition. Falls back to
    // RECENT if the on-disk value drifts to an unknown enum name (defense
    // against a future enum rename — the migration in `applyPendingMigrations`
    // and the `setInboxSortMode` setter both write valid `name` only, so this
    // branch is unreachable today).
    val sortMode = remember(settings.inboxSortMode) {
        runCatching { InboxSortMode.valueOf(settings.inboxSortMode) }
            .getOrDefault(InboxSortMode.RECENT)
    }
    val filteredFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val filteredNotifications by filteredFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val digestGroupsFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeDigestGroupsFiltered(settings.hidePersistentNotifications)
    }
    val digestGroups by digestGroupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedCount = remember(filteredNotifications) {
        filteredNotifications
            .toHiddenGroups(
                hidePersistentNotifications = false,
                silentModeFilter = SilentMode.ARCHIVED,
            )
            .sumOf { it.count }
    }
    val processedCount = remember(filteredNotifications) {
        filteredNotifications
            .toHiddenGroups(
                hidePersistentNotifications = false,
                silentModeFilter = SilentMode.PROCESSED,
            )
            .sumOf { it.count }
    }
    val digestCount = remember(digestGroups) { digestGroups.sumOf { it.count } }

    var selectedTab by rememberSaveable { mutableStateOf(InboxTab.Digest) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                eyebrow = "정리함",
                title = "알림 정리함",
                subtitle = "Digest 묶음과 숨긴 알림을 한 화면에서 훑어볼 수 있어요.",
            )
            // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 3.
            // Sort mode dropdown sits between the header and the sub-tab row
            // so a single selection applies across all three sub-tabs. The
            // setter is fire-and-forget on the `rememberCoroutineScope`; the
            // observed `settings` flow re-emits with the new value and the
            // resolved `sortMode` recomposes downstream consumers.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                InboxSortDropdown(
                    currentMode = sortMode,
                    onSelect = { mode ->
                        scope.launch { settingsRepository.setInboxSortMode(mode) }
                    },
                )
            }
            InboxTabRow(
                selected = selectedTab,
                digestCount = digestCount,
                archivedCount = archivedCount,
                processedCount = processedCount,
                onSelected = { selectedTab = it },
            )
        }
        val innerPadding = PaddingValues(0.dp)
        when (selectedTab) {
            InboxTab.Digest -> DigestScreen(
                contentPadding = innerPadding,
                onNotificationClick = onNotificationClick,
            )
            // Plan `2026-04-22-inbox-denest-and-home-recent-truncate` Task 2:
            // outer Inbox tab is the single source of truth. Embed the Hidden
            // screen so it skips its own ScreenHeader + ARCHIVED/PROCESSED
            // segment row and renders only the body matching the outer pick.
            //
            // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 3:
            // forward the resolved [sortMode] into the embed so the inner
            // group-list helper applies the same mode for the user.
            InboxTab.Archived,
            InboxTab.Processed -> HiddenNotificationsScreen(
                contentPadding = innerPadding,
                onNotificationClick = onNotificationClick,
                onBack = onBack,
                mode = InboxToHiddenScreenModeMapper.mapToMode(selectedTab),
                sortMode = sortMode,
            )
        }
    }
}

/** Inbox top-level sub-tabs. Persisted via `rememberSaveable`. */
enum class InboxTab { Digest, Archived, Processed }

@Composable
private fun InboxTabRow(
    selected: InboxTab,
    digestCount: Int,
    archivedCount: Int,
    processedCount: Int,
    onSelected: (InboxTab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InboxTabSegment(
                label = "Digest",
                count = digestCount,
                isSelected = selected == InboxTab.Digest,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(InboxTab.Digest) },
            )
            InboxTabSegment(
                label = "보관 중",
                count = archivedCount,
                isSelected = selected == InboxTab.Archived,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(InboxTab.Archived) },
            )
            InboxTabSegment(
                label = "처리됨",
                count = processedCount,
                isSelected = selected == InboxTab.Processed,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(InboxTab.Processed) },
            )
        }
    }
}

@Composable
private fun InboxTabSegment(
    label: String,
    count: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val labelColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape = shape),
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "$label · ${count}건",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
            )
        }
    }
}
