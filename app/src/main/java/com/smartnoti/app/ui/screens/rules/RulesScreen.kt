package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeRuleRepository
import com.smartnoti.app.data.rules.RuleMoveDirection
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import com.smartnoti.app.ui.components.RuleRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val previewRepo = remember { FakeRuleRepository() }
    val repository = remember(context) { RulesRepository.getInstance(context) }
    val ruleFactory = remember { RuleDraftFactory() }
    val rules by repository.observeRules().collectAsState(initial = previewRepo.getRules())
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleUiModel?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftMatchValue by remember { mutableStateOf("") }
    var draftType by remember { mutableStateOf(RuleTypeUi.PERSON) }
    var draftAction by remember { mutableStateOf(RuleActionUi.ALWAYS_PRIORITY) }

    fun startCreate() {
        editingRule = null
        draftTitle = ""
        draftMatchValue = ""
        draftType = RuleTypeUi.PERSON
        draftAction = RuleActionUi.ALWAYS_PRIORITY
        showEditor = true
    }

    fun startEdit(rule: RuleUiModel) {
        editingRule = rule
        draftTitle = rule.title
        draftMatchValue = rule.matchValue
        draftType = rule.type
        draftAction = rule.action
        showEditor = true
    }

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
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("직접 규칙 추가", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("사람 / 앱 / 키워드 규칙을 직접 만들어 알림 흐름을 제어할 수 있어요", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { startCreate() }) {
                        Text("새 규칙 추가")
                    }
                }
            }
        }
        items(rules, key = { it.id }) { rule ->
            RuleRow(
                rule = rule,
                onCheckedChange = { checked ->
                    scope.launch { repository.setRuleEnabled(rule.id, checked) }
                },
                onMoveUpClick = {
                    scope.launch { repository.moveRule(rule.id, RuleMoveDirection.UP) }
                },
                onMoveDownClick = {
                    scope.launch { repository.moveRule(rule.id, RuleMoveDirection.DOWN) }
                },
                onEditClick = { startEdit(rule) },
                onDeleteClick = {
                    scope.launch { repository.deleteRule(rule.id) }
                }
            )
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingRule == null) "새 규칙 추가" else "규칙 수정") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = { Text("규칙 이름") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftMatchValue,
                        onValueChange = { draftMatchValue = it },
                        label = { Text(matchLabelFor(draftType)) },
                        supportingText = {
                            if (draftType == RuleTypeUi.KEYWORD) {
                                Text("쉼표로 여러 키워드를 입력할 수 있어요. 예: 배포,장애,긴급")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("규칙 타입", style = MaterialTheme.typography.labelLarge)
                    EnumSelectorRow(
                        options = listOf(RuleTypeUi.PERSON, RuleTypeUi.APP, RuleTypeUi.KEYWORD),
                        selected = draftType,
                        label = { typeLabel(it) },
                        onSelect = { draftType = it },
                    )
                    Text("처리 방식", style = MaterialTheme.typography.labelLarge)
                    EnumSelectorRow(
                        options = listOf(RuleActionUi.ALWAYS_PRIORITY, RuleActionUi.DIGEST, RuleActionUi.SILENT),
                        selected = draftAction,
                        label = { actionLabel(it) },
                        onSelect = { draftAction = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newRule = ruleFactory.create(
                            title = draftTitle,
                            matchValue = draftMatchValue,
                            type = draftType,
                            action = draftAction,
                            existingId = editingRule?.id,
                            enabled = editingRule?.enabled ?: true,
                        )
                        scope.launch { repository.upsertRule(newRule) }
                        showEditor = false
                    },
                    enabled = draftTitle.isNotBlank() && draftMatchValue.isNotBlank(),
                ) {
                    Text(if (editingRule == null) "추가" else "저장")
                }
            },
            dismissButton = {
                Button(onClick = { showEditor = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

@Composable
private fun <T> EnumSelectorRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            Button(onClick = { onSelect(option) }) {
                Text(if (option == selected) "✓ ${label(option)}" else label(option))
            }
        }
    }
}

private fun typeLabel(type: RuleTypeUi): String = when (type) {
    RuleTypeUi.PERSON -> "사람"
    RuleTypeUi.APP -> "앱"
    RuleTypeUi.KEYWORD -> "키워드"
    RuleTypeUi.SCHEDULE -> "시간"
    RuleTypeUi.REPEAT_BUNDLE -> "반복"
}

private fun actionLabel(action: RuleActionUi): String = when (action) {
    RuleActionUi.ALWAYS_PRIORITY -> "즉시 전달"
    RuleActionUi.DIGEST -> "Digest"
    RuleActionUi.SILENT -> "조용히"
    RuleActionUi.CONTEXTUAL -> "상황별"
}

private fun matchLabelFor(type: RuleTypeUi): String = when (type) {
    RuleTypeUi.PERSON -> "이름 또는 발신자"
    RuleTypeUi.APP -> "패키지명"
    RuleTypeUi.KEYWORD -> "키워드"
    RuleTypeUi.SCHEDULE -> "시간 조건"
    RuleTypeUi.REPEAT_BUNDLE -> "반복 기준"
}
