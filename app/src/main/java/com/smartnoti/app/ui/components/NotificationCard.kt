package com.smartnoti.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.NotificationUiModel

@Composable
fun NotificationCard(
    model: NotificationUiModel,
    onClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(model.id) }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(model.appName, style = MaterialTheme.typography.labelLarge)
                Text(model.receivedAtLabel, style = MaterialTheme.typography.labelMedium)
            }
            Text(model.sender ?: model.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(model.body, style = MaterialTheme.typography.bodyMedium)
            StatusBadge(model.status)
            ReasonChipRow(model.reasonTags)
        }
    }
}
