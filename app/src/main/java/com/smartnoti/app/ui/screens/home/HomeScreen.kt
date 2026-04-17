package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeNotificationRepository
import com.smartnoti.app.notification.SmartNotiNotificationStore
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.QuickActionCard

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onPriorityClick: () -> Unit,
    onDigestClick: () -> Unit,
) {
    val repo by remember { androidx.compose.runtime.mutableStateOf(FakeNotificationRepository()) }
    val captured by SmartNotiNotificationStore.capturedNotifications.collectAsState()
    val recent = remember(captured) {
        if (captured.isNotEmpty()) captured + repo.getRecentNotifications()
        else repo.getRecentNotifications()
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("좋은 오전이에요", style = MaterialTheme.typography.labelLarge)
            Text("SmartNoti가 중요한 알림만 먼저 보여드리고 있어요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("오늘 알림 34개 중 중요한 7개만 먼저 보여드렸어요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("즉시 전달 7 · Digest 19 · 조용히 정리 8", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickActionCard(title = "중요 알림", subtitle = "지금 봐야 할 알림 보기", onClick = onPriorityClick)
                QuickActionCard(title = "정리함", subtitle = "묶인 알림 확인", onClick = onDigestClick)
            }
        }
        item {
            Text("방금 정리된 알림", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(recent) { notification ->
            NotificationCard(model = notification, onClick = onNotificationClick)
        }
    }
}
