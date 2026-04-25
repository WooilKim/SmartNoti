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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.smartnoti.app.domain.usecase.RuleDraftFactory
import com.smartnoti.app.domain.usecase.UnassignedRulesDetector
import com.smartnoti.app.ui.components.RuleRow
import com.smartnoti.app.ui.components.RuleRowPresentation
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val unassignedRulesDetector = remember { UnassignedRulesDetector() }
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
    // Rule.action was removed. The list / filter / grouping surfaces still
    // reason about an action per rule, so we derive it by looking up the
    // owning Category. Plan `2026-04-24-rule-editor-remove-action-dropdown.md`
    // Task 6 builds on the same index — rules with no owning Category fall
    // into the "미분류" bucket rendered above the action groups.
    val ruleActionsIndex = remember(categories) { RuleCategoryActionIndex(categories) }
    val ruleActions = remember(rules, ruleActionsIndex) {
        rules.mapNotNull { rule ->
            ruleActionsIndex.actionFor(rule.id)?.let { rule.id to it }
        }.toMap()
    }
    val unassignedRules = remember(rules, categories) {
        unassignedRulesDetector.detect(rules, categories)
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
    // Re-derive the unassigned subset against the current filter — when the
    // user has selected a specific action chip we hide the 미분류 group so
    // the filter is not visually contradicted.
    val visibleUnassignedRules = remember(unassignedRules, selectedActionFilter) {
        if (selectedActionFilter != null) emptyList() else unassignedRules
    }
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleUiModel?>(null) }
    var draftTitle by remember { mutableStateOf("") }
    var draftMatchValue by remember { mutableStateOf("") }
    var draftType by remember { mutableStateOf(RuleTypeUi.PERSON) }
    var scheduleStartHour by remember { mutableStateOf("9") }
    var scheduleEndHour by remember { mutableStateOf("18") }
    // Phase C Task 4: "기존 규칙의 예외로 만들기" switch + dropdown state.
    var draftOverrideEnabled by remember { mutableStateOf(false) }
    var draftOverrideOf by remember { mutableStateOf<String?>(null) }

    // Plan `2026-04-24-rule-editor-remove-action-dropdown.md` Task 5: after
    // saving a freshly created Rule we surface a ModalBottomSheet asking the
    // user to attach it to a Category. The state lives on the screen so an
    // inline dismissal leaves the rule as a 미분류 draft.
    var pendingCategoryAssignmentRuleId by remember { mutableStateOf<String?>(null) }
    val pendingCategoryAssignmentRule = pendingCategoryAssignmentRuleId
        ?.let { id -> rules.firstOrNull { it.id == id } }

    fun startCreate() {
        editingRule = null
        draftTitle = ""
        draftMatchValue = ""
        draftType = RuleTypeUi.PERSON
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
        // Plan `2026-04-24-rule-editor-remove-action-dropdown.md` Task 6:
        // surface 미분류 (unassigned) rules in their own group above the
        // action-categorized groups. The group is hidden when an action
        // filter is active so the user's filter intent isn't contradicted.
        if (visibleUnassignedRules.isNotEmpty()) {
            item(key = "group:unassigned") {
                SectionLabel(
                    title = "미분류",
                    subtitle = "분류에 추가되기 전까지 어떤 알림도 분류하지 않아요. 카드를 탭하면 분류에 추가할 수 있어요.",
                )
            }
            items(
                items = visibleUnassignedRules,
                key = { rule -> "unassigned:${rule.id}" },
            ) { rule ->
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
                Column(
                    modifier = Modifier
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
                        }
                        .clickable { pendingCategoryAssignmentRuleId = rule.id },
                ) {
                    RuleRow(
                        rule = rule,
                        action = RuleActionUi.SILENT,
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
                        presentation = RuleRowPresentation.Unassigned,
                    )
                }
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
                    // Plan `2026-04-24-rule-editor-remove-action-dropdown.md`
                    // Tasks 3+4: the editor no longer asks for a "처리 방식"
                    // (action) here. A Rule is a pure condition matcher, the
                    // action lives on the owning Category. After save, the
                    // post-save sheet (Task 5) walks the user through picking
                    // a Category — until then the rule sits in the 미분류
                    // bucket and matches no notifications.
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
                            existingId = editingRule?.id,
                            enabled = editingRule?.enabled ?: true,
                            overrideOf = draftOverrideOf.takeIf { draftOverrideEnabled },
                        )
                        // Plan `2026-04-24-rule-editor-remove-action-dropdown.md`
                        // Tasks 4+5: persist the rule only — the silent 1:1
                        // companion-Category auto-upsert is gone. For brand-new
                        // rules we trigger the post-save assignment sheet so the
                        // user can pick (or create) a Category. Editing an
                        // existing rule keeps the dialog-close behavior — the
                        // rule already has its owning Category from the prior
                        // assignment, and re-prompting on every edit would be
                        // noise.
                        val isNewRule = editingRule == null
                        scope.launch {
                            repository.upsertRule(newRule)
                            if (isNewRule) {
                                pendingCategoryAssignmentRuleId = newRule.id
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

    // Plan `2026-04-24-rule-editor-remove-action-dropdown.md` Task 5: Category
    // assignment sheet. ModalBottomSheet preserves context (the rules list
    // stays underneath) so the user can dismiss with "나중에" and the rule
    // simply lingers in the 미분류 bucket. Tapping a Category appends the
    // rule's id to that Category's `ruleIds`, which immediately retires the
    // rule from 미분류 (RuleCategoryActionIndex picks up the new ownership).
    if (pendingCategoryAssignmentRule != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { pendingCategoryAssignmentRuleId = null },
            sheetState = sheetState,
        ) {
            CategoryAssignSheetContent(
                rule = pendingCategoryAssignmentRule,
                categories = categories,
                onCategorySelected = { categoryId ->
                    scope.launch {
                        categoriesRepository.appendRuleIdToCategory(
                            categoryId = categoryId,
                            ruleId = pendingCategoryAssignmentRule.id,
                        )
                    }
                    pendingCategoryAssignmentRuleId = null
                },
                onDismiss = { pendingCategoryAssignmentRuleId = null },
            )
        }
    }
}

@Composable
private fun CategoryAssignSheetContent(
    rule: RuleUiModel,
    categories: List<Category>,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "이 규칙을 어떤 분류에 추가하시겠어요?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "“${rule.title.ifBlank { rule.matchValue }}” 규칙은 분류에 추가되기 전까지 어떤 알림도 분류하지 않아요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (categories.isEmpty()) {
            Text(
                text = "아직 만들어진 분류가 없어요. 분류 탭에서 새 분류를 만든 뒤 다시 규칙을 추가해 보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    CategoryAssignRow(
                        category = category,
                        onClick = { onCategorySelected(category.id) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("나중에 분류에 추가")
            }
        }
    }
}

@Composable
private fun CategoryAssignRow(
    category: Category,
    onClick: () -> Unit,
) {
    SmartSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category.name.ifBlank { "분류" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "규칙 ${category.ruleIds.size}개 · ${categoryActionLabel(category.action)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "추가",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun categoryActionLabel(action: com.smartnoti.app.domain.model.CategoryAction): String = when (action) {
    com.smartnoti.app.domain.model.CategoryAction.PRIORITY -> "즉시 전달"
    com.smartnoti.app.domain.model.CategoryAction.DIGEST -> "Digest"
    com.smartnoti.app.domain.model.CategoryAction.SILENT -> "조용히"
    com.smartnoti.app.domain.model.CategoryAction.IGNORE -> "무시"
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
