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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
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
 *
 * Plan `docs/plans/2026-04-22-categories-empty-state-inline-cta.md` wraps
 * the list + FAB in a local [Scaffold] so the FAB is routed through the
 * Material 3 `floatingActionButton` slot: this lays the FAB above the
 * AppBottomBar inset carried in by [contentPadding]. Without this, a
 * plain `Box(fillMaxSize())` with a `.align(BottomEnd)` FAB renders
 * beneath the parent Scaffold's bottom bar and becomes unreachable. The
 * same plan adds an empty-state inline CTA so first-run users can enter
 * the editor directly from where their eyes already are.
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

    val detailCategory = detailTargetId?.let { id -> categories.firstOrNull { it.id == id } }
    if (detailTargetId != null && detailCategory == null) {
        // Underlying Category was deleted out from under us.
        detailTargetId = null
    }

    if (detailCategory != null) {
        Box(modifier = Modifier.fillMaxSize()) {
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
            EditorOverlay(
                target = editorTarget,
                categories = categories,
                rules = rules,
                capturedApps = capturedApps,
                onDismiss = { editorTarget = null },
                onSaved = { savedId ->
                    editorTarget = null
                    if (detailTargetId != null) detailTargetId = savedId
                },
                onDelete = { deletedId ->
                    editorTarget = null
                    if (detailTargetId == deletedId) detailTargetId = null
                    deleteCategory(deletedId)
                },
            )
        }
        return
    }

    // Local Scaffold is intentional: the parent AppNavHost Scaffold owns
    // the bottomBar and passes its bottom inset via `contentPadding`. We
    // consume that inset on this Scaffold's modifier so the FAB sits
    // above the bottom navigation. `containerColor = Transparent` avoids
    // re-painting the app background underneath the parent.
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editorTarget = CategoryEditorTarget.New },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                text = { Text(CategoriesEmptyStateAction.LABEL) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
                            action = {
                                FilledTonalButton(
                                    onClick = { editorTarget = CategoryEditorTarget.New },
                                ) {
                                    Text(CategoriesEmptyStateAction.LABEL)
                                }
                            },
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

            EditorOverlay(
                target = editorTarget,
                categories = categories,
                rules = rules,
                capturedApps = capturedApps,
                onDismiss = { editorTarget = null },
                onSaved = { savedId ->
                    editorTarget = null
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
private fun EditorOverlay(
    target: CategoryEditorTarget?,
    categories: List<Category>,
    rules: List<com.smartnoti.app.domain.model.RuleUiModel>,
    capturedApps: List<com.smartnoti.app.data.local.CapturedAppSelectionItem>,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    if (target != null) {
        CategoryEditorScreen(
            target = target,
            categories = categories,
            rules = rules,
            capturedApps = capturedApps,
            onDismiss = onDismiss,
            onSaved = onSaved,
            onDelete = onDelete,
        )
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
