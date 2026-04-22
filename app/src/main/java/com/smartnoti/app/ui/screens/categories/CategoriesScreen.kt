package com.smartnoti.app.ui.screens.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.launch

/**
 * Primary 분류 (Categories) tab — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 8.
 *
 * Scaffold scope: list Categories with icon / name / action badge / rule
 * count + FAB → editor. Tapping a row opens [CategoryDetailScreen] which
 * routes back into the Task 9 editor. Richer detail content (recent
 * notification preview, etc.) is deferred to a follow-up under Task 9.
 *
 * Composes repositories directly (matching the pattern used by the existing
 * Rules tab): no dedicated ViewModel is introduced because a Compose-local
 * remember is sufficient for the drawer/dialog state this scaffold needs.
 */
@Composable
fun CategoriesScreen(
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val categoriesRepository = remember(context) { CategoriesRepository.getInstance(context) }
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val notificationRepository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val scope = rememberCoroutineScope()

    val categories by categoriesRepository.observeCategories()
        .collectAsStateWithLifecycle(initialValue = emptyList<Category>())
    val rules by rulesRepository.observeRules().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = SmartNotiSettings())
    val capturedAppsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeCapturedAppsFiltered(settings.hidePersistentNotifications)
    }
    val capturedApps by capturedAppsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editorTarget by remember { mutableStateOf<CategoryEditorTarget?>(null) }
    var detailTargetId by remember { mutableStateOf<String?>(null) }

    fun deleteCategory(categoryId: String) {
        scope.launch { categoriesRepository.deleteCategory(categoryId) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val detailCategory = detailTargetId?.let { id -> categories.firstOrNull { it.id == id } }
        if (detailTargetId != null && detailCategory == null) {
            // Underlying Category was deleted out from under us.
            detailTargetId = null
        }

        if (detailCategory != null) {
            CategoryDetailScreen(
                contentPadding = contentPadding,
                category = detailCategory,
                rules = rules,
                onBack = { detailTargetId = null },
                onEdit = { editorTarget = CategoryEditorTarget.Edit(detailCategory.id) },
                onDelete = {
                    detailTargetId = null
                    deleteCategory(detailCategory.id)
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    // Leave room so the FAB doesn't overlap the last card.
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ScreenHeader(
                        eyebrow = "분류",
                        title = "내 분류",
                        subtitle = "알림을 어떻게 전달할지 결정하는 분류예요. 규칙들을 분류로 묶어 한 번에 관리할 수 있어요.",
                    )
                }
                if (categories.isEmpty()) {
                    item {
                        EmptyState(
                            title = "아직 분류가 없어요",
                            subtitle = "새 분류를 추가해 규칙을 묶고 전달 방식을 지정해 보세요.",
                            icon = Icons.Outlined.Category,
                        )
                    }
                } else {
                    items(items = categories, key = { it.id }) { category ->
                        CategoryRow(
                            category = category,
                            ruleCount = category.ruleIds.size,
                            onClick = { detailTargetId = category.id },
                        )
                    }
                }
            }

            ExtendedFloatingActionButton(
                onClick = { editorTarget = CategoryEditorTarget.New },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                text = { Text("새 분류 추가") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 24.dp),
            )
        }

        val target = editorTarget
        if (target != null) {
            CategoryEditorScreen(
                target = target,
                categories = categories,
                rules = rules,
                capturedApps = capturedApps,
                onDismiss = { editorTarget = null },
                onSaved = { savedId ->
                    editorTarget = null
                    // If we edited from the detail surface, stay there on the
                    // refreshed Category; a brand-new Category simply lands in
                    // the list and the user can tap in when they want.
                    if (detailTargetId != null) detailTargetId = savedId
                },
                onDelete = { deletedId ->
                    editorTarget = null
                    if (detailTargetId == deletedId) detailTargetId = null
                    deleteCategory(deletedId)
                },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    ruleCount: Int,
    onClick: () -> Unit,
) {
    SmartSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = category.name.ifBlank { "분류" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                val subtitle = buildString {
                    if (!category.appPackageName.isNullOrBlank()) {
                        append(category.appPackageName)
                        append(" · ")
                    }
                    append("규칙 ${ruleCount}개")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CategoryActionBadge(category.action)
        }
    }
}
