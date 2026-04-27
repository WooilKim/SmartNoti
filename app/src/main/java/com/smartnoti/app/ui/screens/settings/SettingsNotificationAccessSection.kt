package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.components.SettingsCardHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.GreenAccent

@Composable
internal fun NotificationAccessCard(
    summary: SettingsNotificationAccessSummary,
    onOpenSettings: () -> Unit,
) {
    val accentColor = if (summary.granted) GreenAccent else MaterialTheme.colorScheme.primary
    val impactTitle = if (summary.granted) "현재 반영 효과" else "켜면 생기는 변화"
    SmartSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            SettingsCardHeader(
                eyebrow = "알림 접근",
                title = "실제 알림 연결 상태",
                subtitle = summary.headline,
                modifier = Modifier.weight(1f),
            )
            ContextBadge(
                label = summary.statusLabel,
                containerColor = accentColor.copy(alpha = 0.18f),
                contentColor = accentColor,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = summary.supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "설정 경로",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = summary.pathDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.85f))
                Text(
                    text = impactTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                )
                Text(
                    text = summary.impactDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(
                    text = summary.actionLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
