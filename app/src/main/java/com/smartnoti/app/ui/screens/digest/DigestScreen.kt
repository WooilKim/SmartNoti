package com.smartnoti.app.ui.screens.digest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.ui.components.DigestGroupCard
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.launch

@Composable
fun DigestScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = com.smartnoti.app.data.settings.SmartNotiSettings())
    val groupsFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeDigestGroupsFiltered(settings.hidePersistentNotifications)
    }
    val groups by groupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    if (groups.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "정리함",
                    title = "알림 정리함",
                    subtitle = "덜 급한 알림은 그룹으로 정리해 한 번에 훑어볼 수 있어요.",
                )
            }
            item {
                EmptyState(
                    title = "아직 정리된 알림이 없어요",
                    subtitle = "반복되거나 덜 급한 알림을 여기에 모아둘게요",
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "정리함",
                title = "알림 정리함",
                subtitle = "지금 바로 보지 않아도 되는 알림을 앱별로 묶어 더 빠르게 정리할 수 있어요.",
            )
        }
        item {
            SmartSurfaceCard {
                Text(
                    text = "현재 ${groups.size}개의 묶음이 준비되어 있어요.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "요약과 최근 항목을 함께 보여줘서 반복 알림을 한눈에 스캔할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(groups, key = { it.id }) { group ->
            val spec = remember(group.id) { digestGroupBulkActionsSpec(group) }
            DigestGroupCard(
                model = group,
                onNotificationClick = onNotificationClick,
                bulkActions = spec?.let {
                    {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.restoreDigestToPriorityByPackage(it.packageName)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(it.restoreLabel)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.deleteDigestByPackage(it.packageName)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(it.deleteLabel)
                            }
                        }
                    }
                },
            )
        }
    }
}

/**
 * Plan `docs/plans/2026-04-26-inbox-digest-group-bulk-actions.md` Task 4.
 *
 * Pure view-state for the Digest sub-tab's per-group bulk action row. Extracted
 * so the `bulkActions` slot wiring contract (packageName key + button copy) can
 * be unit-tested without a Compose runtime — mirrors the codebase pattern used
 * by `DigestGroupCardPreviewState` and `RuleRowDescriptionBuilder`.
 *
 * Returns `null` when the group has no rows (defensive: `items.first()` would
 * throw on an empty group). Real `observeDigestGroupsFiltered` flows never emit
 * empty groups today, so this branch is a future-proof guard rather than a
 * currently-reachable case.
 */
data class DigestGroupBulkActionsSpec(
    val packageName: String,
    val restoreLabel: String,
    val deleteLabel: String,
)

internal fun digestGroupBulkActionsSpec(group: DigestGroupUiModel): DigestGroupBulkActionsSpec? {
    val firstItem = group.items.firstOrNull() ?: return null
    return DigestGroupBulkActionsSpec(
        packageName = firstItem.packageName,
        restoreLabel = "모두 중요로 변경",
        deleteLabel = "모두 지우기",
    )
}
