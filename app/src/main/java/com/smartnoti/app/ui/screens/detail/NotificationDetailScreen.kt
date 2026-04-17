package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeNotificationRepository
import com.smartnoti.app.ui.components.ReasonChipRow
import com.smartnoti.app.ui.components.StatusBadge

@Composable
fun NotificationDetailScreen(
    contentPadding: PaddingValues,
    notificationId: String,
) {
    val repo = remember { FakeNotificationRepository() }
    val notification = remember(notificationId) { repo.getNotificationById(notificationId) }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("알림 상세", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(notification.appName, style = MaterialTheme.typography.labelLarge)
                    Text(notification.sender ?: notification.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(notification.body, style = MaterialTheme.typography.bodyLarge)
                    StatusBadge(notification.status)
                }
            }
        }
        item {
            Card {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("왜 이렇게 처리됐나요?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ReasonChipRow(notification.reasonTags)
                }
            }
        }
    }
}
