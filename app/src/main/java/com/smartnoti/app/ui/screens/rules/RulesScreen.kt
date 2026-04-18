package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.fake.FakeRuleRepository
import com.smartnoti.app.data.rules.RuleMoveDirection
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import com.smartnoti.app.ui.components.RuleRow
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val previewRepo = remember { FakeRuleRepository() }
    val repository = remember(context) { RulesRepository.getInstance(context) }
    val ruleFactory = remember { RuleDraftFactory() }
    val draftValidator = remember { RuleEditorDraftValidator() }
    val rules by repository.observeRules().collectAsState(initial = previewRepo.getRules())
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleUiModel?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftMatchValue by remember { mutableStateOf("") }
    var draftType by remember { mutableStateOf(RuleTypeUi.PERSON) }
    var draftAction by remember { mutableStateOf(RuleActionUi.ALWAYS_PRIORITY) }
    var scheduleStartHour by remember { mutableStateOf("9") }
    var scheduleEndHour by remember { mutableStateOf("18") }

    fun startCreate() {
        editingRule = null
        draftTitle = ""
        draftMatchValue = ""
        draftType = RuleTypeUi.PERSON
        draftAction = RuleActionUi.ALWAYS_PRIORITY
        scheduleStartHour = "9"
        scheduleEndHour = "18"
        showEditor = true
    }

    fun startEdit(rule: RuleUiModel) {
        editingRule = rule
        draftTitle = rule.title
        draftMatchValue = rule.matchValue
        draftType = rule.type
        draftAction = rule.action
        if (rule.type == RuleTypeUi.SCHEDULE) {
            val parts = rule.matchValue.split('-')
            scheduleStartHour = parts.getOrNull(0).orEmpty().ifBlank { "9" }
            scheduleEndHour = parts.getOrNull(1).orEmpty().ifBlank { "18" }
        }
        showEditor = true
    }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "Rules",
                title = "내 규칙",
                subtitle = "중요 연락, 앱, 키워드, 시간대를 운영 규칙처럼 정리해 알림 흐름을 직접 제어할 수 있어요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "직접 규칙 추가",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "사람 / 앱 / 키워드 규칙을 직접 만들어 알림 흐름을 제어할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { startCreate() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("새 규칙 추가")
                }
            }
        }
        item {
            SectionLabel(
                title = "활성 규칙 ${rules.size}개",
                subtitle = "우선순위 변경, 수정, 삭제는 각 규칙 카드에서 바로 처리할 수 있어요.",
            )
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
            title = {
                Text(
                    if (editingRule == null) "새 규칙 추가" else "규칙 수정",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionLabel(
                        title = "기본 정보",
                        subtitle = "나중에 빠르게 구분할 수 있도록 짧고 명확하게 입력하세요.",
                    )
                    OutlinedTextField(
                        value = draftTitle,
                        onValueChange = { draftTitle = it },
                        label = { Text("규칙 이름") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (draftType == RuleTypeUi.SCHEDULE) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            OutlinedTextField(
                                value = scheduleStartHour,
                                onValueChange = { scheduleStartHour = it.filter(Char::isDigit).take(2) },
                                label = { Text("시작") },
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = scheduleEndHour,
                                onValueChange = { scheduleEndHour = it.filter(Char::isDigit).take(2) },
                                label = { Text("종료") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
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
                    }
                    SectionLabel(title = "규칙 타입")
                    EnumSelectorRow(
                        options = listOf(
                            RuleTypeUi.PERSON,
                            RuleTypeUi.APP,
                            RuleTypeUi.KEYWORD,
                            RuleTypeUi.SCHEDULE,
                            RuleTypeUi.REPEAT_BUNDLE,
                        ),
                        selected = draftType,
                        label = { typeLabel(it) },
                        onSelect = {
                            draftType = it
                            if (it == RuleTypeUi.SCHEDULE) {
                                draftMatchValue = "${scheduleStartHour}-${scheduleEndHour}"
                            } else if (it == RuleTypeUi.REPEAT_BUNDLE && draftMatchValue.isBlank()) {
                                draftMatchValue = "3"
                            } else if (editingRule?.type == RuleTypeUi.SCHEDULE) {
                                draftMatchValue = ""
                            }
                        },
                    )
                    SectionLabel(title = "처리 방식")
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
                        val matchValue = if (draftType == RuleTypeUi.SCHEDULE) {
                            "${scheduleStartHour.ifBlank { "9" }}-${scheduleEndHour.ifBlank { "18" }}"
                        } else {
                            draftMatchValue
                        }
                        val newRule = ruleFactory.create(
                            title = draftTitle,
                            matchValue = matchValue,
                            type = draftType,
                            action = draftAction,
                            existingId = editingRule?.id,
                            enabled = editingRule?.enabled ?: true,
                        )
                        scope.launch { repository.upsertRule(newRule) }
                        showEditor = false
                    },
                    enabled = draftValidator.canSave(
                        title = draftTitle,
                        matchValue = draftMatchValue,
                        type = draftType,
                        scheduleStartHour = scheduleStartHour,
                        scheduleEndHour = scheduleEndHour,
                    ),
                ) {
                    Text(if (editingRule == null) "추가" else "저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("닫기")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> EnumSelectorRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
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
