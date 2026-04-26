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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.CategoryEditorPrefill
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.screens.categories.components.CategoryConditionChips
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
    onOpenNotification: (notificationId: String) -> Unit = {},
    // Plan `2026-04-26-uncategorized-prompt-editor-autoopen` Tasks 3+5:
    // optional nav args carried in by Home's uncategorized-prompt card.
    // When the package is non-blank and the consumed lock has not been
    // tripped this composition, the editor auto-opens with the supplied
    // app pre-pinned. Both default to null so existing call sites
    // (BottomNav, deep links, tests) compile unchanged.
    prefillPackage: String? = null,
    prefillLabel: String? = null,
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
    // Plan `2026-04-26-category-detail-recent-notifications-preview.md` Task 5:
    // collect the same notifications stream the inbox uses (only persistent-
    // notification filter applied; IGNORE rows are intentionally included so
    // Detail's preview reflects what the Category actually catches).
    val notificationsFlow = remember(notificationRepository, settings.hidePersistentNotifications) {
        notificationRepository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val notifications by notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var editorTarget by remember { mutableStateOf<CategoryEditorTarget?>(null) }
    var detailTargetId by remember { mutableStateOf<String?>(null) }
    // Plan `2026-04-26-uncategorized-prompt-editor-autoopen` Task 5: snapshot
    // the prefill at the moment we auto-open so a later FAB tap (after the
    // user dismisses the auto-opened dialog) opens an empty editor — the
    // FAB path never sets [newEditorPrefill]. Cleared on dismiss/save/delete.
    var newEditorPrefill by remember { mutableStateOf<CategoryEditorPrefill?>(null) }
    // rememberSaveable so the lock survives configuration changes (rotation /
    // process death). Once true, no nav-arg-driven re-open ever fires for
    // this back-stack entry; a fresh navigation event with new args creates
    // a new entry with a fresh saveable, so future Home prompt taps still
    // work.
    var prefillConsumed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(prefillPackage, prefillLabel) {
        if (UncategorizedPromptPrefillResolver.shouldAutoOpen(
                prefillPackage = prefillPackage,
                prefillLabel = prefillLabel,
                alreadyConsumed = prefillConsumed,
            )
        ) {
            newEditorPrefill = UncategorizedPromptPrefillResolver.buildPrefill(
                prefillPackage = prefillPackage,
                prefillLabel = prefillLabel,
            )
            editorTarget = CategoryEditorTarget.New
            prefillConsumed = true
        }
    }

    fun deleteCategory(categoryId: String) {
        scope.launch { categoriesRepository.deleteCategory(categoryId) }
    }

    val detailCategory = detailTargetId?.let { id -> categories.firstOrNull { it.id == id } }
    if (detailTargetId != null && detailCategory == null) {
        // Underlying Category was deleted out from under us.
        detailTargetId = null
    }

    if (detailCategory != null) {
        val recentNotifications = remember(detailCategory, notifications) {
            CategoryRecentNotificationsSelector.select(
                category = detailCategory,
                notifications = notifications,
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            CategoryDetailScreen(
                contentPadding = contentPadding,
                category = detailCategory,
                rules = rules,
                recentNotifications = recentNotifications,
                onBack = { detailTargetId = null },
                onEdit = { editorTarget = CategoryEditorTarget.Edit(detailCategory.id) },
                onDelete = {
                    detailTargetId = null
                    deleteCategory(detailCategory.id)
                },
                onOpenNotification = onOpenNotification,
            )
            EditorOverlay(
                target = editorTarget,
                categories = categories,
                rules = rules,
                capturedApps = capturedApps,
                prefill = newEditorPrefill.takeIf { editorTarget == CategoryEditorTarget.New },
                onDismiss = {
                    editorTarget = null
                    newEditorPrefill = null
                },
                onSaved = { savedCategory ->
                    editorTarget = null
                    newEditorPrefill = null
                    if (detailTargetId != null) detailTargetId = savedCategory.id
                },
                onDelete = { deletedId ->
                    editorTarget = null
                    newEditorPrefill = null
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
                        // Plan `2026-04-24-categories-condition-chips.md`
                        // Task 3: surface the matched-rule conditions inline
                        // on the card so users see "조건 → 분류 → 액션"
                        // before drilling into Detail. maxInline = 2 keeps
                        // the row to one visual line on a phone-width card;
                        // 3+ rules collapse to "외 N개".
                        val memberRules = category.memberRulesIn(rules)
                        CategoryRow(
                            category = category,
                            ruleCount = category.ruleIds.size,
                            memberRules = memberRules,
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
                prefill = newEditorPrefill.takeIf { editorTarget == CategoryEditorTarget.New },
                onDismiss = {
                    editorTarget = null
                    newEditorPrefill = null
                },
                onSaved = { savedCategory ->
                    editorTarget = null
                    newEditorPrefill = null
                    if (detailTargetId != null) detailTargetId = savedCategory.id
                },
                onDelete = { deletedId ->
                    editorTarget = null
                    newEditorPrefill = null
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
    onSaved: (Category) -> Unit,
    onDelete: (String) -> Unit,
    prefill: CategoryEditorPrefill? = null,
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
            prefill = prefill,
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    ruleCount: Int,
    memberRules: List<RuleUiModel>,
    onClick: () -> Unit,
) {
    SmartSurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
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
            CategoryConditionChips(
                rules = memberRules,
                action = category.action,
                maxInline = 2,
            )
        }
    }
}

/**
 * Resolve this Category's [Category.ruleIds] against the live rules list,
 * preserving the Category's own ordering so the chip output matches the
 * order users see in the editor's multi-select.
 */
private fun Category.memberRulesIn(allRules: List<RuleUiModel>): List<RuleUiModel> {
    if (ruleIds.isEmpty() || allRules.isEmpty()) return emptyList()
    val byId = allRules.associateBy { it.id }
    return ruleIds.mapNotNull { byId[it] }
}
