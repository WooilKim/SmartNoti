package com.smartnoti.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.RuleUiModel

@Composable
fun RuleRow(
    rule: RuleUiModel,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.foundation.layout.Column {
                Text(rule.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(rule.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = rule.enabled, onCheckedChange = onCheckedChange)
        }
    }
}
