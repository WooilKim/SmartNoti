package com.smartnoti.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ReasonChipRow(tags: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        tags.forEach { tag ->
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .background(Color(0xFF24324B), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
