package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeRuleRepository
import com.smartnoti.app.ui.components.RuleRow

@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val repo = remember { FakeRuleRepository() }
    val rules = remember { mutableStateListOf(*repo.getRules().toTypedArray()) }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("내 규칙", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        items(rules, key = { it.id }) { rule ->
            RuleRow(rule = rule, onCheckedChange = { checked ->
                val index = rules.indexOfFirst { it.id == rule.id }
                if (index >= 0) rules[index] = rule.copy(enabled = checked)
            })
        }
    }
}
