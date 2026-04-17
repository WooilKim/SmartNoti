package com.smartnoti.app.ui.screens.digest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeNotificationRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.ui.components.DigestGroupCard
import com.smartnoti.app.ui.components.EmptyState

@Composable
fun DigestScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val previewRepo = remember { FakeNotificationRepository() }
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val liveGroups by repository.observeDigestGroups().collectAsState(initial = emptyList())
    val groups = remember(liveGroups) {
        if (liveGroups.isNotEmpty()) liveGroups else previewRepo.getDigestGroups()
    }

    if (groups.isEmpty()) {
        EmptyState(title = "아직 정리된 알림이 없어요", subtitle = "반복되거나 덜 급한 알림을 여기에 모아둘게요")
        return
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("알림 정리함", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("지금 바로 보지 않아도 됐던 알림을 모아두었어요", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(groups) { group ->
            DigestGroupCard(model = group, onNotificationClick = onNotificationClick)
        }
    }
}
