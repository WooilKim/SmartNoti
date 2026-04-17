package com.smartnoti.app.ui.screens.priority

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeNotificationRepository
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard

@Composable
fun PriorityScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
) {
    val repo = remember { FakeNotificationRepository() }
    val notifications = remember { repo.getPriorityNotifications() }

    if (notifications.isEmpty()) {
        EmptyState(title = "아직 중요한 알림이 없어요", subtitle = "SmartNoti가 필요한 순간에만 바로 보여드릴게요")
        return
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("중요 알림", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        items(notifications) { notification ->
            NotificationCard(model = notification, onClick = onNotificationClick)
        }
    }
}
