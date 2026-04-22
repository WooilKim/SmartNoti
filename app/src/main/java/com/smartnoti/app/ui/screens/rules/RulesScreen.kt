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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RuleMoveDirection
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.RuleCategoryActionIndex
import com.smartnoti.app.domain.usecase.RuleCategoryActionIndex.Companion.toCategoryActionOrNull
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import com.smartnoti.app.ui.components.RuleRow
import com.smartnoti.app.ui.components.RuleRowPresentation
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(
    contentPadding: PaddingValues,
    highlightRuleId: String? = null,
) {
    val context = LocalContext.current
    val repository = remember(context) { RulesRepository.getInstance(context) }
    val categoriesRepository = remember(context) { CategoriesRepository.getInstance(context) }
    val notificationRepository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val ruleFactory = remember { RuleDraftFactory() }
    val draftValidator = remember { RuleEditorDraftValidator() }
    val appSuggestionBuilder = remember { RuleEditorAppSuggestionBuilder() }
    val repeatThresholdController = remember { RuleEditorRepeatBundleThresholdController() }
    val listPresentationBuilder = remember { RuleListPresentationBuilder() }
    val listFilterApplicator = remember { RuleListFilterApplicator() }
    val listGroupingBuilder = remember { RuleListGroupingBuilder() }
    val listHierarchyBuilder = remember { RuleListHierarchyBuilder() }
    val overrideOptionsBuilder = remember { RuleEditorOverrideOptionsBuilder() }
    val overrideSupersetValidator = remember { RuleOverrideSupersetValidator() }
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = SmartNotiSettings())
    val capturedAppsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeCapturedAppsFiltered(settings.hidePersistentNotifications)
    }
    val capturedApps by capturedAppsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val appSuggestions = remember(capturedApps) { appSuggestionBuilder.build(capturedApps) }
    val rules by repository.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by categoriesRepository.observeCategories()
        .collectAsStateWithLifecycle(initialValue = emptyList<Category>())
    // Plan `2026-04-22-categories-split-rules-actions.md` Phase P1 Task 4:
    // Rule.action was removed. The editor / list / filter / grouping
    // surfaces still reason about an action per rule, so we derive it by
    // looking up the owning Category.
    val ruleActions = remember(categories) {
        val index = RuleCategoryActionIndex(categories)
        rules.associate { rule -> rule.id to index.actionForOrDefault(rule.id) }
    }
    val listPresentation = remember(rules, ruleActions) {
        listPresentationBuilder.build(rules, ruleActions)
    }
    var selectedActionFilter by rememberSaveable { mutableStateOf<RuleActionUi?>(null) }
    val visibleRules = remember(rules, selectedActionFilter, ruleActions) {
        listFilterApplicator.apply(rules, selectedActionFilter, ruleActions)
    }
    val groupedVisibleRules = remember(visibleRules, ruleActions) {
        listGroupingBuilder.build(visibleRules, ruleActions)
    }
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleUiModel?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftMatchValue by remember { mutableStateOf("") }
    var draftType by remember { mutableStateOf(RuleTypeUi.PERSON) }
    var draftAction by remember { mutableStateOf(RuleActionUi.ALWAYS_PRIORITY) }
    var scheduleStartHour by remember { mutableStateOf("9") }
    var scheduleEndHour by remember { mutableStateOf("18") }
    // Phase C Task 4: "기존 규칙의 예외로 만들기" switch + dropdown state.
    var draftOverrideEnabled by remember { mutableStateOf(false) }
    var draftOverrideOf by remember { mutableStateOf<String?>(null) }

    fun startCreate() {
        editingRule = null
        draftTitle = ""
        draftMatchValue = ""
        draftType = RuleTypeUi.PERSON
        draftAction = RuleActionUi.ALWAYS_PRIORITY
        scheduleStartHour = "9"
        scheduleEndHour = "18"
        draftOverrideEnabled = false
        draftOverrideOf = null
        showEditor = true
    }

    fun startEdit(rule: RuleUiModel) {
        editingRule = rule
        draftTitle = rule.title
        draftMatchValue = rule.matchValue
        draftType = rule.type
        draftAction = ruleActions[rule.id] ?: RuleActionUi.ALWAYS_PRIORITY
        if (rule.type == RuleTypeUi.SCHEDULE) {
            val parts = rule.matchValue.split('-')
            scheduleStartHour = parts.getOrNull(0).orEmpty().ifBlank { "9" }
            scheduleEndHour = parts.getOrNull(1).orEmpty().ifBlank { "18" }
        }
        draftOverrideEnabled = rule.overrideOf != null
        draftOverrideOf = rule.overrideOf
        showEditor = true
    }

    val listState = rememberLazyListState()
    var highlightedRuleId by rememberSaveable { mutableStateOf<String?>(null) }

    // Detail screen's "적용된 규칙" chips deep-link via `highlightRuleId` (plan
    // `rules-ux-v2-inbox-restructure` Phase B Task 3). We echo it into local
    // state so the flash fades even if the user stays on the screen, and we
    // drive one scroll per deep-link arrival.
    LaunchedEffect(highlightRuleId, rules) {
        val target = highlightRuleId ?: return@LaunchedEffect
        val visibleRulesSnapshot = listFilterApplicator.apply(rules, selectedActionFilter, ruleActions)
        if (visibleRulesSnapshot.none { it.id == target }) {
            // Filter is hiding the target — drop it so the user sees every rule.
            selectedActionFilter = null
        }
        highlightedRuleId = target
        val indexInFullList = rules.indexOfFirst { it.id == target }
        if (indexInFullList >= 0) {
            // Approximate: header + "직접 규칙 추가" + "활성 규칙" cards = 3 sticky items,
            // plus one SectionLabel per group that comes before the target. This is
            // good enough for the "land near the rule" UX without threading
            // LazyListItemInfo back from each row.
            runCatching { listState.animateScrollToItem(indexInFullList + 3) }
        }
        delay(HIGHLIGHT_FLASH_DURATION_MILLIS)
        highlightedRuleId = null
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "규칙",
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
            // Tree-per-group: base rules keep their override children nested
            // with an indent + "이 규칙의 예외" banner (Phase C Task 3).
            val hierarchy = listHierarchyBuilder.build(
                visibleRules = group.rules,
                allRules = rules,
            )
            val flattenedNodes = hierarchy.flatMap { node ->
                listOf(node to 0) + node.children.map { child -> child to 1 }
            }
            items(
                items = flattenedNodes,
                key = { (node, _) -> node.rule.id },
            ) { (node, depth) ->
                val rule = node.rule
                val isHighlighted = rule.id == highlightedRuleId
                val highlightColor by animateColorAsState(
                    targetValue = if (isHighlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                    },
                    animationSpec = tween(durationMillis = 400),
                    label = "ruleHighlight",
                )
                val baseTitle = (node.overrideState as? RuleOverrideState.Override)
                    ?.baseRuleId
                    ?.let { baseId -> rules.firstOrNull { it.id == baseId }?.title }
                val presentation = ruleRowPresentationFor(node = node, baseTitle = baseTitle)
                Column(
                    modifier = Modifier
                        .padding(start = (depth * 16).dp)
                        .let { mod ->
                            if (isHighlighted) {
                                mod.border(
                                    width = 2.dp,
                                    color = highlightColor,
                                    shape = RoundedCornerShape(16.dp),
                                )
                            } else {
                                mod
                            }
                        },
                ) {
                    RuleRow(
                        rule = rule,
                        action = ruleActions[rule.id] ?: RuleActionUi.SILENT,
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
                        },
                        presentation = presentation,
                    )
                }
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
                    // Plan 2026-04-21-ignore-tier-fourth-decision Task 5 step 1
                    // — IGNORE joins the action dropdown. Copy deliberately
                    // spells out "(즉시 삭제)" so the destructive outcome
                    // reads at a glance; CONTEXTUAL stays off the editor
                    // menu because it is system-picked, not user-authored.
                    EnumSelectorRow(
                        options = listOf(
                            RuleActionUi.ALWAYS_PRIORITY,
                            RuleActionUi.DIGEST,
                            RuleActionUi.SILENT,
                            RuleActionUi.IGNORE,
                        ),
                        selected = draftAction,
                        label = { actionLabel(it) },
                        onSelect = { draftAction = it },
                    )
                    // Plan rules-ux-v2-inbox-restructure Phase C Task 4. The
                    // override section is collapsed by default so the common
                    // "add a plain rule" flow stays uncluttered; it expands
                    // into a base-rule dropdown + superset warning when the
                    // switch is on.
                    val overrideCandidates = remember(rules, editingRule?.id) {
                        overrideOptionsBuilder.build(
                            allRules = rules,
                            editingRuleId = editingRule?.id,
                        )
                    }
                    RuleOverrideEditorSection(
                        overrideEnabled = draftOverrideEnabled,
                        onOverrideEnabledChange = { next ->
                            draftOverrideEnabled = next
                            if (!next) draftOverrideOf = null
                        },
                        candidates = overrideCandidates,
                        selectedBaseId = draftOverrideOf,
                        onBaseSelected = { draftOverrideOf = it },
                        supersetWarningMessage = run {
                            if (!draftOverrideEnabled || draftOverrideOf == null) return@run null
                            val previewMatchValue = if (draftType == RuleTypeUi.SCHEDULE) {
                                "${scheduleStartHour.ifBlank { "9" }}-${scheduleEndHour.ifBlank { "18" }}"
                            } else {
                                draftMatchValue
                            }
                            val preview = ruleFactory.create(
                                title = draftTitle.ifBlank { "draft" },
                                matchValue = previewMatchValue,
                                type = draftType,
                                action = draftAction,
                                existingId = editingRule?.id,
                                overrideOf = draftOverrideOf,
                            )
                            val verdict = overrideSupersetValidator.validate(preview, rules)
                            (verdict as? RuleOverrideSupersetValidator.Verdict.Warning)
                                ?.let { supersetWarningMessage(it.reason) }
                        },
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
                            overrideOf = draftOverrideOf.takeIf { draftOverrideEnabled },
                        )
                        // Plan `2026-04-22-categories-split-rules-actions.md`
                        // Phase P1 Task 4: the rule itself no longer carries
                        // an action. Upsert a 1:1 Category alongside so the
                        // selected `draftAction` survives. Category id mirrors
                        // the migration's `cat-from-rule-<ruleId>` scheme so
                        // a subsequent migration pass stays idempotent.
                        val draftCategoryAction = draftAction.toCategoryActionOrNull()
                        scope.launch {
                            repository.upsertRule(newRule)
                            if (draftCategoryAction != null) {
                                val existingCategory = categoriesRepository.currentCategories()
                                    .firstOrNull { it.ruleIds.contains(newRule.id) }
                                val category = Category(
                                    id = existingCategory?.id ?: "cat-from-rule-${newRule.id}",
                                    name = existingCategory?.name ?: newRule.matchValue.ifBlank { newRule.title },
                                    appPackageName = if (newRule.type == RuleTypeUi.APP) {
                                        newRule.matchValue
                                    } else {
                                        existingCategory?.appPackageName
                                    },
                                    ruleIds = existingCategory?.ruleIds?.takeIf { it.contains(newRule.id) }
                                        ?: listOf(newRule.id),
                                    action = draftCategoryAction,
                                    order = existingCategory?.order ?: 0,
                                )
                                categoriesRepository.upsertCategory(category)
                            }
                        }
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
    // Task 5 of plan `2026-04-21-ignore-tier-fourth-decision` finalizes the
    // editor copy — "(즉시 삭제)" spells out the destructive outcome so a
    // user tapping the chip understands it is not another "조용히" tier.
    RuleActionUi.IGNORE -> "무시 (즉시 삭제)"
}

private fun matchLabelFor(type: RuleTypeUi): String = when (type) {
    RuleTypeUi.PERSON -> "이름 또는 발신자"
    RuleTypeUi.APP -> "패키지명"
    RuleTypeUi.KEYWORD -> "키워드"
    RuleTypeUi.SCHEDULE -> "시간 조건"
    RuleTypeUi.REPEAT_BUNDLE -> "반복 기준"
}

// How long the border-flash lingers after a Detail-chip deep-link lands on a
// rule. Kept short enough that the user's attention snaps to the row without
// feeling obstructive.
private const val HIGHLIGHT_FLASH_DURATION_MILLIS = 1800L

@Composable
private fun RuleOverrideEditorSection(
    overrideEnabled: Boolean,
    onOverrideEnabledChange: (Boolean) -> Unit,
    candidates: List<RuleUiModel>,
    selectedBaseId: String?,
    onBaseSelected: (String?) -> Unit,
    supersetWarningMessage: String?,
) {
    // Plan rules-ux-v2-inbox-restructure Phase C Task 4: "기존 규칙의 예외로
    // 만들기" toggle + base-rule dropdown. The section renders as a section
    // label + switch row, and only mounts the dropdown when the switch is on
    // so the dialog doesn't feel crowded for plain-rule creation.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(
            title = "예외 규칙",
            subtitle = "다른 규칙이 먼저 적용되어도 이 규칙이 우선하도록 예외로 설정할 수 있어요.",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "기존 규칙의 예외로 만들기",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = overrideEnabled,
                onCheckedChange = onOverrideEnabledChange,
                enabled = candidates.isNotEmpty() || overrideEnabled,
            )
        }
        if (overrideEnabled) {
            if (candidates.isEmpty()) {
                Text(
                    text = "예외로 지정할 기준 규칙이 없어요. 먼저 기본 규칙을 만들어 보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                RuleOverrideBaseDropdown(
                    candidates = candidates,
                    selectedBaseId = selectedBaseId,
                    onBaseSelected = onBaseSelected,
                )
            }
            if (supersetWarningMessage != null) {
                Text(
                    text = supersetWarningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RuleOverrideBaseDropdown(
    candidates: List<RuleUiModel>,
    selectedBaseId: String?,
    onBaseSelected: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = candidates.firstOrNull { it.id == selectedBaseId }?.title
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "어느 규칙의 예외인가요?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssistChip(
            onClick = { expanded = true },
            label = {
                Text(selectedTitle ?: "규칙 선택")
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            candidates.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.title) },
                    onClick = {
                        onBaseSelected(candidate.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun supersetWarningMessage(reason: RuleOverrideSupersetValidator.Reason): String = when (reason) {
    RuleOverrideSupersetValidator.Reason.BASE_MISSING ->
        "기준 규칙을 찾을 수 없어요. 삭제됐거나 아직 저장되지 않은 규칙일 수 있어요."
    RuleOverrideSupersetValidator.Reason.TYPE_MISMATCH ->
        "기준 규칙과 타입이 달라요. 같은 타입으로 맞추면 더 정확하게 동작해요."
    RuleOverrideSupersetValidator.Reason.KEYWORD_NOT_SUPERSET ->
        "기준 규칙의 키워드를 모두 포함해야 예외가 정상적으로 동작해요."
    RuleOverrideSupersetValidator.Reason.VALUE_MISMATCH ->
        "기준 규칙과 조건 값이 달라서 예외가 적용되지 않을 수 있어요."
}

private fun ruleRowPresentationFor(
    node: RuleListNode,
    baseTitle: String?,
): RuleRowPresentation {
    val broken = node.brokenReason
    if (broken != null) {
        return RuleRowPresentation.BrokenOverride(
            reasonMessage = when (broken) {
                RuleOverrideBrokenReason.SelfReference ->
                    "이 규칙은 자기 자신을 예외로 지정해 동작하지 않아요."
                is RuleOverrideBrokenReason.BaseMissing ->
                    "기준이 되는 규칙이 삭제돼 예외가 동작하지 않아요."
                is RuleOverrideBrokenReason.BaseIsOverride ->
                    "다른 예외의 예외로 지정돼 있어 동작하지 않아요."
            },
        )
    }
    return when (node.overrideState) {
        RuleOverrideState.Base -> RuleRowPresentation.Base
        is RuleOverrideState.Override -> RuleRowPresentation.Override(baseTitle = baseTitle)
    }
}
