package com.smartnoti.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.DigestGroupUiModel

@Composable
fun DigestGroupCard(
    model: DigestGroupUiModel,
    onNotificationClick: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${model.appName} ${model.count}건", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(model.summary, style = MaterialTheme.typography.bodyMedium)
            model.items.take(3).forEach { item ->
                NotificationCard(model = item, onClick = onNotificationClick)
            }
        }
    }
}
