package com.smartnoti.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.NotificationStatusUi

@Composable
fun StatusBadge(status: NotificationStatusUi) {
    val (label, color) = when (status) {
        NotificationStatusUi.PRIORITY -> "즉시 전달" to Color(0xFF2D6BFF)
        NotificationStatusUi.DIGEST -> "Digest" to Color(0xFF946200)
        NotificationStatusUi.SILENT -> "조용히 정리" to Color(0xFF4B5563)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier
            .background(color = color, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
