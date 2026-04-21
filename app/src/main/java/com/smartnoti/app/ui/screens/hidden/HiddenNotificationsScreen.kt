package com.smartnoti.app.ui.screens.hidden

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.smartnoti.app.data.local.toHiddenGroups
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.usecase.SilentGroupKey
import com.smartnoti.app.ui.components.DigestGroupCard
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import kotlinx.coroutines.launch

/**
 * Hidden 화면. SILENT 알림을 **보관 중** / **처리됨** 두 탭으로 분리해 보여준다.
 *
 * - 기본 탭: [SilentMode.ARCHIVED] (아직 확인 대기 중인 알림).
 * - [SilentMode.PROCESSED] 탭은 사용자가 "처리 완료" 로 넘긴 알림 + 구버전 null
 *   silentMode 을 가진 legacy row 를 포함. (plan Open question 4 마이그레이션 결정)
 *
 * 참조: `docs/plans/2026-04-21-silent-archive-vs-process-split.md` Task 4.
 */
@Composable
fun HiddenNotificationsScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onBack: () -> Unit,
    initialFilter: SilentGroupKey? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = SmartNotiSettings())
    val filteredFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val filteredNotifications by filteredFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val archivedGroups = remember(filteredNotifications) {
        filteredNotifications.toHiddenGroups(
            hidePersistentNotifications = false,
            silentModeFilter = SilentMode.ARCHIVED,
        )
    }
    val processedGroups = remember(filteredNotifications) {
        filteredNotifications.toHiddenGroups(
            hidePersistentNotifications = false,
            silentModeFilter = SilentMode.PROCESSED,
        )
    }
    val archivedCount = remember(archivedGroups) { archivedGroups.sumOf { it.count } }
    val processedCount = remember(processedGroups) { processedGroups.sumOf { it.count } }

    var selectedTab by rememberSaveable { mutableStateOf(HiddenTab.Archived) }
    var pendingClearAll by remember { mutableStateOf(false) }

    // Deep-link filter arriving from the tray group summary snaps the user back to the
    // ARCHIVED tab (that's where sender/app groups live; PROCESSED items won't have a
    // matching group summary in the tray by construction).
    LaunchedEffect(initialFilter) {
        if (initialFilter != null) {
            selectedTab = HiddenTab.Archived
        }
    }

    val visibleGroups = when (selectedTab) {
        HiddenTab.Archived -> archivedGroups
        HiddenTab.Processed -> processedGroups
    }
    val visibleCount = when (selectedTab) {
        HiddenTab.Archived -> archivedCount
        HiddenTab.Processed -> processedCount
    }

    val highlightedGroupId = remember(initialFilter, visibleGroups) {
        initialFilter?.let { filter ->
            visibleGroups.firstOrNull { group ->
                HiddenDeepLinkFilterResolver.matchesGroup(
                    filter = filter,
                    groupPackageName = group.items.firstOrNull()?.packageName.orEmpty(),
                    senders = group.items.map { it.sender },
                )
            }?.id
        }
    }

    val listState = rememberLazyListState()
    // Scroll to the deep-linked group once it materialises in the list. Uses group id as
    // the key so re-composition after the repository stream flips initial empty → non-empty
    // still triggers exactly one scroll.
    LaunchedEffect(highlightedGroupId, visibleGroups) {
        val targetId = highlightedGroupId ?: return@LaunchedEffect
        val indexInVisible = visibleGroups.indexOfFirst { it.id == targetId }
        if (indexInVisible < 0) return@LaunchedEffect
        // Leading items (header row + tab row + summary card) occupy the first 3 slots when
        // the list is non-empty; target index in LazyColumn = 3 + indexInVisible.
        val leadingItems = 3
        listState.animateScrollToItem(leadingItems + indexInVisible)
    }

    if (pendingClearAll) {
        AlertDialog(
            onDismissRequest = { pendingClearAll = false },
            title = { Text("숨긴 알림 ${archivedCount + processedCount}건을 모두 지울까요?") },
            text = { Text("SmartNoti 내 기록만 지우는 거라, 시스템 알림센터에는 영향이 없어요. 이후에 같은 앱에서 조용히 분류된 알림이 오면 다시 모여요.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.deleteAllSilent() }
                    pendingClearAll = false
                }) {
                    Text("모두 지우기")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearAll = false }) {
                    Text("취소")
                }
            },
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "뒤로 가기",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                ScreenHeader(
                    eyebrow = "숨긴 알림",
                    title = "보관 ${archivedCount}건 · 처리 ${processedCount}건",
                    subtitle = "'보관 중' 은 아직 확인하지 않은 알림, '처리됨' 은 이미 훑어본 알림이에요. 탭으로 오가며 정리하세요.",
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp),
                )
            }
        }
        item {
            HiddenTabRow(
                selected = selectedTab,
                archivedCount = archivedCount,
                processedCount = processedCount,
                onSelected = { selectedTab = it },
            )
        }
        if (visibleGroups.isEmpty()) {
            item {
                HiddenTabEmptyState(tab = selectedTab)
            }
        } else {
            item {
                SmartSurfaceCard {
                    Text(
                        text = when (selectedTab) {
                            HiddenTab.Archived -> "${visibleGroups.size}개 앱에서 ${visibleCount}건을 보관 중이에요."
                            HiddenTab.Processed -> "${visibleGroups.size}개 앱에서 ${visibleCount}건을 처리했어요."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when (selectedTab) {
                            HiddenTab.Archived -> "같은 앱의 여러 알림은 한 카드로 모아서 보여줘요. 탭하면 최신 내용을 바로 확인할 수 있어요."
                            HiddenTab.Processed -> "이미 확인했거나 이전 버전에서 넘어온 알림이에요. 필요하면 한 번에 지울 수 있어요."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { pendingClearAll = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("전체 숨긴 알림 모두 지우기")
                    }
                }
            }
            items(visibleGroups, key = { it.id }) { group ->
                HiddenGroupCardWithBulkActions(
                    group = group,
                    isHighlighted = group.id == highlightedGroupId,
                    onNotificationClick = onNotificationClick,
                    onRestoreAll = {
                        scope.launch { repository.restoreSilentToPriorityByPackage(group.items.first().packageName) }
                    },
                    onDeleteAll = {
                        scope.launch { repository.deleteSilentByPackage(group.items.first().packageName) }
                    },
                )
            }
        }
    }
}

/** Persistable selection for the Hidden 탭 segmented control. */
enum class HiddenTab { Archived, Processed }

@Composable
private fun HiddenTabRow(
    selected: HiddenTab,
    archivedCount: Int,
    processedCount: Int,
    onSelected: (HiddenTab) -> Unit,
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
            HiddenTabSegment(
                label = "보관 중",
                count = archivedCount,
                isSelected = selected == HiddenTab.Archived,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(HiddenTab.Archived) },
            )
            HiddenTabSegment(
                label = "처리됨",
                count = processedCount,
                isSelected = selected == HiddenTab.Processed,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(HiddenTab.Processed) },
            )
        }
    }
}

@Composable
private fun HiddenTabSegment(
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

@Composable
private fun HiddenTabEmptyState(tab: HiddenTab) {
    when (tab) {
        HiddenTab.Archived -> EmptyState(
            title = "보관 중인 알림이 없어요",
            subtitle = "지금은 모두 처리됐어요. 새로 조용히 분류된 알림이 오면 여기에 먼저 모여요.",
        )
        HiddenTab.Processed -> EmptyState(
            title = "처리된 알림이 없어요",
            subtitle = "보관 중 알림을 상세에서 '처리 완료로 표시' 하면 이 탭에 쌓여요.",
        )
    }
}

@Composable
private fun HiddenGroupCardWithBulkActions(
    group: DigestGroupUiModel,
    isHighlighted: Boolean,
    onNotificationClick: (String) -> Unit,
    onRestoreAll: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    val highlightBorder by animateColorAsState(
        targetValue = if (isHighlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        label = "hiddenGroupHighlightBorder",
    )
    val highlightModifier = if (isHighlighted) {
        Modifier.border(
            width = 1.5.dp,
            color = highlightBorder,
            shape = RoundedCornerShape(16.dp),
        )
    } else {
        Modifier
    }
    Box(modifier = highlightModifier) {
        DigestGroupCard(
            model = group,
            onNotificationClick = onNotificationClick,
            collapsible = true,
            bulkActions = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRestoreAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("모두 중요로 복구")
                    }
                    OutlinedButton(
                        onClick = onDeleteAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("모두 지우기")
                    }
                }
            },
        )
    }
}
