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
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

@Composable
fun StatusBadge(status: NotificationStatusUi) {
    val (label, bgColor, fgColor) = when (status) {
        NotificationStatusUi.PRIORITY -> Triple("즉시 전달", PriorityContainer, PriorityOnContainer)
        NotificationStatusUi.DIGEST -> Triple("Digest", DigestContainer, DigestOnContainer)
        NotificationStatusUi.SILENT -> Triple("조용히 정리", SilentContainer, SilentOnContainer)
    }
    ContextBadge(
        label = label,
        containerColor = bgColor,
        contentColor = fgColor,
    )
}

@Composable
fun ContextBadge(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        modifier = modifier
            .background(color = containerColor, shape = RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
