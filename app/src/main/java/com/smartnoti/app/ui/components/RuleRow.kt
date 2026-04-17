package com.smartnoti.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel

@Composable
fun RuleRow(
    rule: RuleUiModel,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUpClick: () -> Unit = {},
    onMoveDownClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(rule.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(rule.subtitle, style = MaterialTheme.typography.bodySmall)
                    if (rule.matchValue.isNotBlank()) {
                        Text(rule.matchValue.toDisplayMatchValue(rule.type), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Switch(checked = rule.enabled, onCheckedChange = onCheckedChange)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onMoveUpClick) {
                    Text("위로")
                }
                Button(onClick = onMoveDownClick) {
                    Text("아래로")
                }
                Button(onClick = onEditClick) {
                    Text("수정")
                }
                Button(onClick = onDeleteClick) {
                    Text("삭제")
                }
            }
        }
    }
}

private fun String.toDisplayMatchValue(type: RuleTypeUi): String = when (type) {
    RuleTypeUi.KEYWORD -> split(',').joinToString(", ") { it.trim() }
    RuleTypeUi.SCHEDULE -> replace("-", ":00 ~ ") + ":00"
    else -> this
}
