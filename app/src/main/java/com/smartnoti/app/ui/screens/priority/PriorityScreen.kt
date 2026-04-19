package com.smartnoti.app.ui.screens.priority

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard

@Composable
fun PriorityScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = com.smartnoti.app.data.settings.SmartNotiSettings())
    val notificationsFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observePriorityFiltered(settings.hidePersistentNotifications)
    }
    val notifications by notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    if (notifications.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Priority",
                    title = "중요 알림",
                    subtitle = "지금 바로 확인해야 하는 알림만 차분하게 모아두었어요.",
                )
            }
            item {
                EmptyState(
                    title = "아직 중요한 알림이 없어요",
                    subtitle = "SmartNoti가 필요한 순간에만 바로 보여드릴게요",
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
                eyebrow = "Priority",
                title = "중요 알림",
                subtitle = "긴급하지만 시끄럽지 않게, 지금 먼저 봐야 할 알림만 남겼어요.",
            )
        }
        item {
            SmartSurfaceCard {
                Text(
                    text = "총 ${notifications.size}건의 알림이 즉시 전달 대기 중이에요.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "알림 이유와 상태를 함께 보여줘서 왜 중요한지 빠르게 판단할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(notifications) { notification ->
            NotificationCard(model = notification, onClick = onNotificationClick)
        }
    }
}
