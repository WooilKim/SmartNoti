package com.smartnoti.app.ui.screens.rules

import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RuleMoveDirection
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val repository = remember(context) { RulesRepository.getInstance(context) }
    val notificationRepository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val ruleFactory = remember { RuleDraftFactory() }
    val draftValidator = remember { RuleEditorDraftValidator() }
    val appSuggestionBuilder = remember { RuleEditorAppSuggestionBuilder() }
    val repeatThresholdController = remember { RuleEditorRepeatBundleThresholdController() }
    val listPresentationBuilder = remember { RuleListPresentationBuilder() }
    val listFilterApplicator = remember { RuleListFilterApplicator() }
    val listGroupingBuilder = remember { RuleListGroupingBuilder() }
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = SmartNotiSettings())
    val capturedAppsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeCapturedAppsFiltered(settings.hidePersistentNotifications)
    }
    val capturedApps by capturedAppsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val appSuggestions = remember(capturedApps) { appSuggestionBuilder.build(capturedApps) }
    val rules by repository.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val listPresentation = remember(rules) { listPresentationBuilder.build(rules) }
    var selectedActionFilter by rememberSaveable { mutableStateOf<RuleActionUi?>(null) }
    val visibleRules = remember(rules, selectedActionFilter) {
        listFilterApplicator.apply(rules, selectedActionFilter)
    }
    val groupedVisibleRules = remember(visibleRules) { listGroupingBuilder.build(visibleRules) }
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
                    text = "사람 / 앱 / 키워드 / 반복 규칙을 직접 만들고, 앱 규칙은 최근 캡처된 앱에서 바로 고를 수 있어요.",
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
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "활성 규칙 ${rules.size}개",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listPresentation.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listPresentation.filters.forEach { filter ->
                        FilterChip(
                            selected = selectedActionFilter == filter.action,
                            onClick = {
                                selectedActionFilter = if (selectedActionFilter == filter.action) {
                                    null
                                } else {
                                    filter.action
                                }
                            },
                            label = { Text(filter.label) },
                        )
                    }
                }
                Text(
                    text = if (selectedActionFilter == null) {
                        "우선순위 변경, 수정, 삭제는 각 규칙 카드에서 바로 처리할 수 있어요."
                    } else {
                        "선택한 처리 방식 규칙 ${visibleRules.size}개만 보고 있어요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groupedVisibleRules.forEach { group ->
            item(key = "group:${group.action.name}") {
                SectionLabel(
                    title = group.title,
                    subtitle = group.subtitle,
                )
            }
            items(group.rules, key = { it.id }) { rule ->
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
                    } else if (draftType == RuleTypeUi.REPEAT_BUNDLE) {
                        RepeatBundleThresholdEditor(
                            value = repeatThresholdController.normalize(draftMatchValue),
                            presets = repeatThresholdController.presets,
                            onValueChange = { updated ->
                                draftMatchValue = repeatThresholdController.normalize(updated)
                            },
                            onDecrease = {
                                draftMatchValue = repeatThresholdController.decrement(draftMatchValue)
                            },
                            onIncrease = {
                                draftMatchValue = repeatThresholdController.increment(draftMatchValue)
                            },
                            onPresetClick = { preset ->
                                draftMatchValue = preset.value
                            },
                        )
                    } else {
                        OutlinedTextField(
                            value = draftMatchValue,
                            onValueChange = { draftMatchValue = it },
                            label = { Text(matchLabelFor(draftType)) },
                            supportingText = {
                                if (draftType == RuleTypeUi.KEYWORD) {
                                    Text("쉼표로 여러 키워드를 입력할 수 있어요. 예: 배포,장애,긴급")
                                } else if (draftType == RuleTypeUi.APP) {
                                    val message = if (appSuggestions.isEmpty()) {
                                        "아직 캡처된 앱이 없어요. 실제 알림이 들어오면 여기서 바로 선택할 수 있어요."
                                    } else {
                                        "아래 최근 캡처된 앱에서 바로 선택하거나 패키지명을 직접 입력할 수 있어요."
                                    }
                                    Text(message)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (draftType == RuleTypeUi.APP && appSuggestions.isNotEmpty()) {
                            SectionLabel(
                                title = "최근 캡처된 앱",
                                subtitle = "알림이 자주 들어오는 앱부터 보여줘요.",
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                appSuggestions.forEach { suggestion ->
                                    RuleEditorAppSuggestionRow(
                                        suggestion = suggestion,
                                        selected = draftMatchValue == suggestion.packageName,
                                        onClick = {
                                            draftTitle = suggestion.appName
                                            draftMatchValue = suggestion.packageName
                                        },
                                    )
                                }
                            }
                        }
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
                            } else if (it == RuleTypeUi.REPEAT_BUNDLE) {
                                draftMatchValue = repeatThresholdController.normalize(
                                    draftMatchValue.ifBlank { "3" }
                                )
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
private fun RepeatBundleThresholdEditor(
    value: String,
    presets: List<RepeatBundleThresholdPreset>,
    onValueChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onPresetClick: (RepeatBundleThresholdPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(
            title = "반복 기준",
            subtitle = "같은 알림이 몇 번 이상 반복되면 이 규칙을 적용할지 정해요.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDecrease) {
                Icon(Icons.Outlined.Remove, contentDescription = "반복 기준 낮추기")
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("반복 횟수") },
                supportingText = { Text("예: 3회 이상 반복되면 적용") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onIncrease) {
                Icon(Icons.Outlined.Add, contentDescription = "반복 기준 높이기")
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            presets.forEach { preset ->
                FilterChip(
                    selected = value == preset.value,
                    onClick = { onPresetClick(preset) },
                    label = { Text(preset.label) },
                )
            }
        }
        OutlinedButton(
            onClick = { onPresetClick(presets[1]) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("추천 기준으로 되돌리기")
        }
    }
}

@Composable
private fun RuleEditorAppSuggestionRow(
    suggestion: RuleEditorAppSuggestion,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = suggestion.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = suggestion.supportingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (selected) "선택됨" else "선택",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
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
