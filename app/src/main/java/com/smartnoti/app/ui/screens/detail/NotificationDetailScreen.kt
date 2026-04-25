package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.usecase.AssignNotificationToCategoryUseCase
import com.smartnoti.app.domain.usecase.CategoryEditorPrefill
import com.smartnoti.app.domain.usecase.NotificationDetailDeliveryProfileSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailOnboardingRecommendationSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailReasonSectionBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailSourceSuppressionSummaryBuilder
import com.smartnoti.app.domain.usecase.shouldShowDetailCard
import com.smartnoti.app.notification.MarkSilentProcessedTrayCancelChain
import com.smartnoti.app.notification.SmartNotiNotificationListenerService
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.ReasonChipRow
import com.smartnoti.app.ui.components.RuleHitChipRow
import com.smartnoti.app.ui.components.StatusBadge
import com.smartnoti.app.ui.notification.CategoryAssignBottomSheet
import com.smartnoti.app.ui.notification.ChangeCategorySheetState
import com.smartnoti.app.ui.screens.categories.CategoryEditorScreen
import com.smartnoti.app.ui.screens.categories.CategoryEditorTarget
import kotlinx.coroutines.launch

@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "뒤로 가기",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
fun NotificationDetailScreen(
    contentPadding: PaddingValues,
    notificationId: String,
    onBack: () -> Unit,
    onRuleClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val categoriesRepository = remember(context) { CategoriesRepository.getInstance(context) }
    val deliveryProfileSummaryBuilder = remember { NotificationDetailDeliveryProfileSummaryBuilder() }
    val onboardingRecommendationSummaryBuilder = remember { NotificationDetailOnboardingRecommendationSummaryBuilder() }
    val sourceSuppressionSummaryBuilder = remember { NotificationDetailSourceSuppressionSummaryBuilder() }
    val reasonSectionBuilder = remember { NotificationDetailReasonSectionBuilder() }
    val scope = rememberCoroutineScope()
    // Plan `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md` Task 2:
    // single SnackbarHostState shared by the two reclassify call sites so
    // both Path A (assign-to-existing) and Path B (created-new) can render a
    // brief confirmation toast after the assign sheet / editor dismisses.
    val snackbarHostState = remember { SnackbarHostState() }
    val confirmationMessageBuilder = remember { DetailReclassifyConfirmationMessageBuilder() }
    val liveNotification by repository.observeNotification(notificationId).collectAsState(initial = null)
    val notification = liveNotification
    val rules by rulesRepository.observeRules().collectAsState(initial = emptyList<RuleUiModel>())
    val categories by categoriesRepository.observeCategories().collectAsState(initial = emptyList<Category>())
    // Plan `2026-04-22-categories-runtime-wiring-fix.md` Task 2: the four
    // action buttons are replaced with a single "분류 변경" CTA that opens
    // a ModalBottomSheet. Local state governs whether the sheet or the
    // editor dialog is up; dismissing either returns to the Detail card.
    var showAssignSheet by remember { mutableStateOf(false) }
    var pendingPrefill by remember { mutableStateOf<CategoryEditorPrefill?>(null) }
    val assignUseCase = remember(rulesRepository, categoriesRepository) {
        AssignNotificationToCategoryUseCase(
            ports = object : AssignNotificationToCategoryUseCase.Ports {
                override suspend fun upsertRule(rule: RuleUiModel) {
                    rulesRepository.upsertRule(rule)
                }
                override suspend fun appendRuleIdToCategory(
                    categoryId: String,
                    ruleId: String,
                ) {
                    categoriesRepository.appendRuleIdToCategory(categoryId, ruleId)
                }
            },
        )
    }
    val reasonSections = remember(notification, rules) {
        notification?.let { reasonSectionBuilder.build(it, rules) }
    }
    val deliveryProfileSummary = remember(notification) {
        notification?.let(deliveryProfileSummaryBuilder::build)
    }
    val onboardingRecommendationSummary = remember(notification) {
        notification?.let(onboardingRecommendationSummaryBuilder::build)
    }
    val sourceSuppressionSummary = remember(notification) {
        notification?.takeIf {
            it.sourceSuppressionState.shouldShowDetailCard(it.replacementNotificationIssued)
        }?.let {
            sourceSuppressionSummaryBuilder.build(
                suppressionState = it.sourceSuppressionState,
                replacementNotificationIssued = it.replacementNotificationIssued,
            )
        }
    }

    if (notification == null) {
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DetailTopBar(title = "알림 상세", onBack = onBack)
            }
            item {
                EmptyState(
                    title = "알림을 찾을 수 없어요",
                    subtitle = "이미 삭제됐거나 아직 저장되지 않은 실제 알림일 수 있어요",
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailTopBar(title = "알림 상세", onBack = onBack)
        }
        // SnackbarHost overlay below — Plan
        // `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md` Task 2.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        notification.appName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        notification.sender ?: notification.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        notification.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusBadge(notification.status)
                }
            }
        }
        val sections = reasonSections
        val hasReasonContent = sections != null &&
            (sections.classifierSignals.isNotEmpty() || sections.ruleHits.isNotEmpty())
        if (hasReasonContent) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            "왜 이렇게 처리됐나요?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (sections?.classifierSignals?.isNotEmpty() == true) {
                            ReasonSubSection(
                                title = "SmartNoti 가 본 신호",
                                description = "분류에 참고한 내부 신호예요. 직접 수정할 수는 없어요.",
                            ) {
                                ReasonChipRow(sections.classifierSignals)
                            }
                        }
                        if (sections?.ruleHits?.isNotEmpty() == true) {
                            ReasonSubSection(
                                title = "적용된 규칙",
                                description = "내 규칙 탭에서 수정하거나 끌 수 있는 규칙이에요. 탭하면 해당 규칙으로 이동해요.",
                            ) {
                                RuleHitChipRow(
                                    hits = sections.ruleHits,
                                    onHitClick = { onRuleClick(it.ruleId) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (onboardingRecommendationSummary != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            onboardingRecommendationSummary.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            onboardingRecommendationSummary.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (deliveryProfileSummary != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "어떻게 전달되나요?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            deliveryProfileSummary.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "전달 모드 · ${deliveryProfileSummary.deliveryModeLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "소리 · ${deliveryProfileSummary.alertLevelLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "진동 · ${deliveryProfileSummary.vibrationLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Heads-up · ${deliveryProfileSummary.headsUpLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "잠금화면 · ${deliveryProfileSummary.lockScreenVisibilityLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        if (sourceSuppressionSummary != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "원본 알림 처리 상태",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            sourceSuppressionSummary.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "원본 상태 · ${sourceSuppressionSummary.statusLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "대체 알림 · ${sourceSuppressionSummary.replacementLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        if (notification.status == NotificationStatusUi.SILENT &&
            notification.silentMode == SilentMode.ARCHIVED
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "조용히 보관 중",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "원본 알림이 아직 알림창에 남아 있어요. 확인이 끝났다면 처리 완료로 표시해 알림창에서 치울 수 있어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val chain = MarkSilentProcessedTrayCancelChain(
                                        markSilentProcessed = repository::markSilentProcessed,
                                        sourceEntryKeyForId = repository::sourceEntryKeyForId,
                                        cancelSourceEntryIfConnected = SmartNotiNotificationListenerService
                                            .Companion::cancelSourceEntryIfConnected,
                                    )
                                    chain.run(notification.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("처리 완료로 표시")
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "이 알림 분류하기",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // Plan `2026-04-22-categories-runtime-wiring-fix.md` Task 2:
                    // the four per-action buttons are replaced by a single
                    // "분류 변경" CTA. Tap opens [CategoryAssignBottomSheet]
                    // which lets the user (a) add this notification's
                    // auto-rule to an existing Category or (b) launch the
                    // Category editor with a prefilled draft.
                    Text(
                        "이 알림이 어떤 분류에 속하는지 알려주세요. 다음부터는 분류의 전달 방식이 적용돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showAssignSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("분류 변경")
                    }
                }
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (showAssignSheet) {
        val sheetState = ChangeCategorySheetState.from(
            notification = notification,
            categories = categories,
        )
        CategoryAssignBottomSheet(
            state = sheetState,
            onDismiss = { showAssignSheet = false },
            onAssignToExisting = { categoryId ->
                showAssignSheet = false
                // Capture the categories snapshot at tap time so the
                // confirmation copy resolves the destination name even if
                // the categories Flow re-emits before the snackbar fires.
                val snapshot = categories
                scope.launch {
                    assignUseCase.assignToExisting(
                        notification = notification,
                        categoryId = categoryId,
                    )
                    val message = confirmationMessageBuilder.build(
                        outcome = DetailReclassifyOutcome.AssignedExisting(categoryId),
                        categories = snapshot,
                    )
                    if (message != null) {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            },
            onCreateNewCategory = {
                showAssignSheet = false
                // Derive the notification's currently-owning Category action
                // (if any) so the editor's default action is dynamic-opposite.
                val currentAction = resolveOwningCategoryAction(
                    notification = notification,
                    categories = categories,
                    rules = rules,
                )
                pendingPrefill = AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
                    notification = notification,
                    currentCategoryAction = currentAction,
                )
            },
        )
    }

    val prefill = pendingPrefill
    if (prefill != null) {
        // Reuse the existing CategoryEditorScreen (AlertDialog) with prefill
        // seeded. Save persists the pendingRule alongside the Category.
        val allRules = rules
        CategoryEditorScreen(
            target = CategoryEditorTarget.New,
            categories = categories,
            rules = allRules,
            capturedApps = emptyList(),
            onDismiss = { pendingPrefill = null },
            onSaved = { savedCategory ->
                pendingPrefill = null
                // Plan
                // `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md`
                // Task 2: render the Path B "새 분류 만들었어요" confirmation
                // using the just-persisted Category's name (not the
                // categories Flow snapshot — it may not have re-emitted
                // yet when this callback fires).
                val message = confirmationMessageBuilder.build(
                    outcome = DetailReclassifyOutcome.CreatedNew(
                        categoryName = savedCategory.name,
                    ),
                    categories = categories,
                )
                if (message != null) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            },
            onDelete = { /* no-op in New flow */ },
            prefill = prefill,
        )
    }
}

/**
 * Resolve the Category that currently "owns" [notification] so the
 * "새 분류 만들기" default action can be dynamic-opposite of it.
 *
 * Matches the classifier's Category selection logic at a high level: find
 * any Category whose `ruleIds` reference a Rule that matches the
 * notification's sender/app. Returns null when no Category owns the
 * notification today (classifier fell through to a heuristic default) —
 * the use case then defaults to PRIORITY.
 */
private fun resolveOwningCategoryAction(
    notification: com.smartnoti.app.domain.model.NotificationUiModel,
    categories: List<Category>,
    rules: List<RuleUiModel>,
): CategoryAction? {
    val matchedRuleIds = rules
        .filter { rule -> rule.enabled && ruleMatches(rule, notification) }
        .map { it.id }
        .toSet()
    if (matchedRuleIds.isEmpty()) return null
    val owning = categories.firstOrNull { category ->
        category.ruleIds.any { it in matchedRuleIds }
    }
    return owning?.action
}

private fun ruleMatches(
    rule: RuleUiModel,
    notification: com.smartnoti.app.domain.model.NotificationUiModel,
): Boolean {
    return when (rule.type) {
        com.smartnoti.app.domain.model.RuleTypeUi.PERSON ->
            !notification.sender.isNullOrBlank() &&
                notification.sender.equals(rule.matchValue, ignoreCase = true)
        com.smartnoti.app.domain.model.RuleTypeUi.APP ->
            notification.packageName.equals(rule.matchValue, ignoreCase = true)
        com.smartnoti.app.domain.model.RuleTypeUi.KEYWORD -> {
            val content = listOf(notification.title, notification.body).joinToString(" ")
            rule.matchValue
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .any { content.contains(it, ignoreCase = true) }
        }
        com.smartnoti.app.domain.model.RuleTypeUi.SCHEDULE,
        com.smartnoti.app.domain.model.RuleTypeUi.REPEAT_BUNDLE -> false
    }
}

@Composable
private fun ReasonSubSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}
