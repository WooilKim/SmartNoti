package com.smartnoti.app.ui.screens.hidden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.local.toHiddenGroups
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.ui.components.DigestGroupCard
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.launch

@Composable
fun HiddenNotificationsScreen(
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
    val filteredFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val filteredNotifications by filteredFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val groups = remember(filteredNotifications) {
        filteredNotifications.toHiddenGroups(hidePersistentNotifications = false)
    }
    val totalCount = remember(groups) { groups.sumOf { it.count } }

    var pendingClearAll by remember { mutableStateOf(false) }

    if (pendingClearAll) {
        AlertDialog(
            onDismissRequest = { pendingClearAll = false },
            title = { Text("숨긴 알림 ${totalCount}건을 모두 지울까요?") },
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
                    title = "숨겨진 알림 ${totalCount}건",
                    subtitle = "조용히로 분류된 알림을 앱별로 묶어 정리했어요. 그룹 카드에서 모두 복구하거나 모두 지울 수도 있어요.",
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp),
                )
            }
        }
        if (groups.isEmpty()) {
            item {
                EmptyState(
                    title = "아직 숨긴 알림이 없어요",
                    subtitle = "조용히 처리된 알림이 생기면 여기에 모여요",
                )
            }
        } else {
            item {
                SmartSurfaceCard {
                    Text(
                        text = "${groups.size}개 앱에서 ${totalCount}건을 숨겼어요.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "같은 앱의 여러 알림은 한 카드로 모아서 보여줘요. 탭하면 최신 내용을 바로 확인할 수 있어요.",
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
            items(groups, key = { it.id }) { group ->
                HiddenGroupCardWithBulkActions(
                    group = group,
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

@Composable
private fun HiddenGroupCardWithBulkActions(
    group: DigestGroupUiModel,
    onNotificationClick: (String) -> Unit,
    onRestoreAll: () -> Unit,
    onDeleteAll: () -> Unit,
) {
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
