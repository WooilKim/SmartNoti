package com.smartnoti.app.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.launch

/**
 * Task 9 Category editor — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3.
 *
 * Renders as an [AlertDialog] (matching the existing Rule editor pattern)
 * so it composes over any current surface without needing a dedicated
 * navigation route. Supports both new-Category and edit-existing flows via
 * [CategoryEditorTarget].
 *
 * Persistence is routed through [CategoriesRepository.upsertCategory] /
 * [CategoriesRepository.deleteCategory]. Save is gated by
 * [CategoryEditorDraftValidator]; broader product-level validation
 * (uniqueness of name, etc.) is deliberately out of scope — see the plan's
 * "Out" list.
 */
sealed interface CategoryEditorTarget {
    data object New : CategoryEditorTarget
    data class Edit(val categoryId: String) : CategoryEditorTarget
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorScreen(
    target: CategoryEditorTarget,
    categories: List<Category>,
    rules: List<RuleUiModel>,
    capturedApps: List<CapturedAppSelectionItem>,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val context = LocalContext.current
    val categoriesRepository = remember(context) { CategoriesRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val validator = remember { CategoryEditorDraftValidator() }

    val editingCategory = remember(target, categories) {
        when (target) {
            CategoryEditorTarget.New -> null
            is CategoryEditorTarget.Edit -> categories.firstOrNull { it.id == target.categoryId }
        }
    }

    var draftName by remember(editingCategory) {
        mutableStateOf(editingCategory?.name.orEmpty())
    }
    var draftAppPackage by remember(editingCategory) {
        mutableStateOf(editingCategory?.appPackageName)
    }
    var draftSelectedRuleIds by remember(editingCategory) {
        mutableStateOf(editingCategory?.ruleIds?.toSet() ?: emptySet())
    }
    var draftAction by remember(editingCategory) {
        mutableStateOf(editingCategory?.action ?: CategoryAction.PRIORITY)
    }

    var actionMenuExpanded by remember { mutableStateOf(false) }
    var appMenuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    val canSave = validator.canSave(
        name = draftName,
        selectedRuleIds = draftSelectedRuleIds.toList(),
    )

    val title = if (editingCategory == null) "새 분류" else "분류 편집"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("분류 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("전달 방식", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(
                            onClick = { actionMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = draftAction.displayLabel(),
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = actionMenuExpanded,
                            onDismissRequest = { actionMenuExpanded = false },
                        ) {
                            CategoryAction.values().forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.displayLabel()) },
                                    onClick = {
                                        draftAction = action
                                        actionMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Optional app association picker. "특정 앱 없음" = null.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "연결할 앱 (선택)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { appMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = draftAppPackage?.let { pkg ->
                                    capturedApps.firstOrNull { it.packageName == pkg }
                                        ?.appName
                                        ?: pkg
                                } ?: "특정 앱 없음",
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Outlined.ArrowDropDown,
                                contentDescription = null,
                            )
                        }
                        DropdownMenu(
                            expanded = appMenuExpanded,
                            onDismissRequest = { appMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("특정 앱 없음") },
                                onClick = {
                                    draftAppPackage = null
                                    appMenuExpanded = false
                                },
                            )
                            capturedApps.forEach { app ->
                                DropdownMenuItem(
                                    text = { Text("${app.appName} · ${app.packageName}") },
                                    onClick = {
                                        draftAppPackage = app.packageName
                                        appMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Rule multi-select. FilterChip keeps the picker compact
                // without dragging in the RuleRow component's reorder/
                // delete affordances, which don't belong in a picker.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "소속 규칙 (최소 1개)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (rules.isEmpty()) {
                        Text(
                            "아직 규칙이 없어요. 설정 > 고급 규칙 편집에서 먼저 규칙을 만들어 주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rules.forEach { rule ->
                                val selected = draftSelectedRuleIds.contains(rule.id)
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        draftSelectedRuleIds = if (selected) {
                                            draftSelectedRuleIds - rule.id
                                        } else {
                                            draftSelectedRuleIds + rule.id
                                        }
                                    },
                                    label = {
                                        Text(
                                            rule.title.ifBlank {
                                                rule.matchValue.ifBlank { rule.id }
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                // Delete surfaces only for existing Categories.
                if (editingCategory != null) {
                    AssistChip(
                        onClick = { confirmingDelete = true },
                        label = { Text("이 분류 삭제") },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    val persisted = persistCategory(
                        editing = editingCategory,
                        name = draftName.trim(),
                        appPackageName = draftAppPackage,
                        selectedRuleIds = draftSelectedRuleIds.toList(),
                        action = draftAction,
                        currentCategoriesCount = categories.size,
                    )
                    scope.launch {
                        categoriesRepository.upsertCategory(persisted)
                    }
                    onSaved(persisted.id)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(if (editingCategory == null) "추가" else "저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )

    if (confirmingDelete && editingCategory != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("분류를 삭제할까요?") },
            text = {
                Text("이 분류만 삭제되며, 연결된 규칙 자체는 삭제되지 않아요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(editingCategory.id)
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("취소") }
            },
        )
    }
}

/**
 * Build the Category to persist. Extracted for clarity — the editor
 * composable is already nearing its useful LOC budget and this function is
 * straightforward enough to read on its own.
 *
 * New-Category ids use a `cat-user-<epoch>` prefix that avoids colliding
 * with the migration's `cat-from-rule-<ruleId>` scheme.
 */
private fun persistCategory(
    editing: Category?,
    name: String,
    appPackageName: String?,
    selectedRuleIds: List<String>,
    action: CategoryAction,
    currentCategoriesCount: Int,
): Category {
    return Category(
        id = editing?.id ?: "cat-user-${System.currentTimeMillis()}",
        name = name,
        appPackageName = appPackageName?.takeIf { it.isNotBlank() },
        ruleIds = selectedRuleIds,
        action = action,
        order = editing?.order ?: currentCategoriesCount,
    )
}
