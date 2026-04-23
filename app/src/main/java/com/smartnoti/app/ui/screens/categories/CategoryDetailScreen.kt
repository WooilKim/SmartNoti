package com.smartnoti.app.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.screens.categories.components.CategoryConditionChips

/**
 * Category detail scaffold — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 8.
 *
 * Shows the Category's summary and its member rule list + edit/delete
 * actions. Recent-notification preview and richer member-rule affordances
 * are deferred — the scaffold only needs to surface enough that a user can
 * verify membership and jump back into the editor.
 */
@Composable
fun CategoryDetailScreen(
    contentPadding: PaddingValues,
    category: Category,
    rules: List<RuleUiModel>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    val memberRules = remember(category, rules) {
        val idSet = category.ruleIds.toSet()
        rules.filter { idSet.contains(it.id) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "뒤로")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "분류 상세",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = category.name.ifBlank { "분류" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                CategoryActionBadge(category.action)
            }
        }

        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "기본 정보",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "이 분류에 속한 알림은 위 배지의 전달 방식대로 처리돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!category.appPackageName.isNullOrBlank()) {
                    Text(
                        text = "연결된 앱 · ${category.appPackageName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "규칙 ${memberRules.size}개 · 순서 ${category.order}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Plan `2026-04-24-categories-condition-chips.md` Task 3:
                // Detail expands every condition (no truncation) so the
                // user can audit the full matcher chain before editing.
                CategoryConditionChips(
                    rules = memberRules,
                    action = category.action,
                    maxInline = Int.MAX_VALUE,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("편집")
                    }
                    OutlinedButton(onClick = { confirmingDelete = true }) {
                        Text("삭제")
                    }
                }
            }
        }

        item {
            SectionLabel(
                title = "소속 규칙",
                subtitle = if (memberRules.isEmpty()) {
                    "아직 이 분류에 연결된 규칙이 없어요. 편집에서 규칙을 추가하세요."
                } else {
                    "이 분류가 매칭 조건으로 사용하는 규칙들이에요."
                },
            )
        }

        if (memberRules.isEmpty()) {
            item {
                Text(
                    text = "연결된 규칙이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(memberRules.size) { index ->
                val rule = memberRules[index]
                SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = rule.title.ifBlank { rule.matchValue.ifBlank { rule.id } },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = rule.subtitle.ifBlank {
                            "${rule.type.name} · ${rule.matchValue.ifBlank { "(조건 미설정)" }}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("분류를 삭제할까요?") },
            text = {
                Text("이 분류만 삭제되며, 연결된 규칙 자체는 삭제되지 않아요.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("취소") }
            },
        )
    }
}
