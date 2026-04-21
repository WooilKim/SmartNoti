package com.smartnoti.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

@Composable
fun NotificationCard(
    model: NotificationUiModel,
    onClick: (String) -> Unit,
) {
    val (containerColor, borderColor) = when (model.status) {
        NotificationStatusUi.PRIORITY -> PriorityContainer.copy(alpha = 0.42f) to PriorityOnContainer.copy(alpha = 0.35f)
        NotificationStatusUi.DIGEST -> DigestContainer.copy(alpha = 0.34f) to DigestOnContainer.copy(alpha = 0.28f)
        NotificationStatusUi.SILENT -> SilentContainer.copy(alpha = 0.92f) to SilentOnContainer.copy(alpha = 0.22f)
        // IGNORE rows only surface in the opt-in archive view (Task 6 of plan
        // `2026-04-21-ignore-tier-fourth-decision`). Reuse the SILENT palette
        // at a lower opacity for now — the finalized neutral-gray token lands
        // with the archive screen UI in Task 6.
        NotificationStatusUi.IGNORE -> SilentContainer.copy(alpha = 0.60f) to SilentOnContainer.copy(alpha = 0.18f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(model.id) },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    model.appName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    model.receivedAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                model.sender ?: model.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                model.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            StatusBadge(model.status)
            ReasonChipRow(model.reasonTags)
        }
    }
}
