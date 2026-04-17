package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.QuickActionCard
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onPriorityClick: () -> Unit,
    onDigestClick: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val recent by repository.observeAll().collectAsState(initial = emptyList())
    val priorityCount = recent.count { it.status == NotificationStatusUi.PRIORITY }
    val digestCount = recent.count { it.status == NotificationStatusUi.DIGEST }
    val silentCount = recent.count { it.status == NotificationStatusUi.SILENT }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "SmartNoti",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "중요한 알림만 먼저 보여드리고 있어요",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "오늘 알림 ${recent.size}개 중 중요한 ${priorityCount}개를 먼저 전달했어요",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("즉시", priorityCount, PriorityContainer, PriorityOnContainer)
                        StatPill("Digest", digestCount, DigestContainer, DigestOnContainer)
                        StatPill("조용히", silentCount, SilentContainer, SilentOnContainer)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionCard(
                    title = "중요 알림",
                    subtitle = "지금 봐야 할 알림 ${priorityCount}개",
                    onClick = onPriorityClick,
                )
                QuickActionCard(
                    title = "정리함",
                    subtitle = "묶인 알림 ${digestCount}개",
                    onClick = onDigestClick,
                )
            }
        }
        item {
            Text(
                "방금 정리된 알림",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "아직 쌓인 알림이 없어요",
                    subtitle = "알림 접근 권한을 허용하면 실제 캡처된 알림만 여기에 보여드릴게요",
                )
            }
        } else {
            items(recent) { notification ->
                NotificationCard(model = notification, onClick = onNotificationClick)
            }
        }
    }
}

@Composable
private fun StatPill(label: String, count: Int, bgColor: Color, fgColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            "$label $count",
            style = MaterialTheme.typography.labelSmall,
            color = fgColor,
        )
    }
}
