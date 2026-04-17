package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeRuleRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.ui.components.RuleRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val previewRepo = remember { FakeRuleRepository() }
    val repository = remember(context) { RulesRepository.getInstance(context) }
    val rules by repository.observeRules().collectAsState(initial = previewRepo.getRules())
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("내 규칙", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Text(
                "사용자 피드백으로 만든 규칙과 기본 규칙을 함께 관리할 수 있어요",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(rules, key = { it.id }) { rule ->
            RuleRow(
                rule = rule,
                onCheckedChange = { checked ->
                    scope.launch {
                        repository.setRuleEnabled(rule.id, checked)
                    }
                }
            )
        }
    }
}
