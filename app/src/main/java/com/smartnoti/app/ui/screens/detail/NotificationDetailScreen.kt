package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
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
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.usecase.NotificationDetailDeliveryProfileSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailOnboardingRecommendationSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailReasonSectionBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailRuleReference
import com.smartnoti.app.domain.usecase.NotificationDetailSourceSuppressionSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationFeedbackPolicy
import com.smartnoti.app.domain.usecase.shouldShowDetailCard
import com.smartnoti.app.notification.MarkSilentProcessedTrayCancelChain
import com.smartnoti.app.notification.SmartNotiNotificationListenerService
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.IgnoreConfirmationDialog
import com.smartnoti.app.ui.components.ReasonChipRow
import com.smartnoti.app.ui.components.RuleHitChipRow
import com.smartnoti.app.ui.components.StatusBadge
import kotlinx.coroutines.Dispatchers
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
    val feedbackPolicy = remember { NotificationFeedbackPolicy() }
    val deliveryProfileSummaryBuilder = remember { NotificationDetailDeliveryProfileSummaryBuilder() }
    val onboardingRecommendationSummaryBuilder = remember { NotificationDetailOnboardingRecommendationSummaryBuilder() }
    val sourceSuppressionSummaryBuilder = remember { NotificationDetailSourceSuppressionSummaryBuilder() }
    val reasonSectionBuilder = remember { NotificationDetailReasonSectionBuilder() }
    val scope = rememberCoroutineScope()
    val liveNotification by repository.observeNotification(notificationId).collectAsState(initial = null)
    val notification = liveNotification
    val rules by rulesRepository.observeRules().collectAsState(initial = emptyList<RuleUiModel>())
    // Plan 2026-04-21-ignore-tier-fourth-decision Task 6a: destructive "무시"
    // flow needs a confirm dialog + 3-sec undo snackbar. State is scoped to
    // this screen so dismissing/navigating away drops the undo window (the
    // flag-off rehydration is intentional — see plan Risks).
    var showIgnoreDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
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
                        // Phase B Task 3 split: classifier-internal factoids
                        // (grey, non-interactive) are separate from user-rule
                        // hits (blue, clickable → Rules deep-link). Either
                        // sub-section is hidden when its list is empty so a
                        // passthrough alert doesn't show an empty "적용된 규칙"
                        // header.
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
                                    // Chain DB flip → tray cancel. See
                                    // docs/plans/2026-04-20-silent-archive-drift-fix.md Task 3.
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
                        "이 알림 학습시키기",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "한 번 누르면 상태를 바꾸고 같은 유형의 규칙도 함께 저장해요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val updated = feedbackPolicy.applyAction(notification, RuleActionUi.ALWAYS_PRIORITY)
                                repository.updateNotification(updated)
                                rulesRepository.upsertRule(feedbackPolicy.toRule(notification, RuleActionUi.ALWAYS_PRIORITY))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("중요로 고정")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val updated = feedbackPolicy.applyAction(notification, RuleActionUi.DIGEST)
                                    repository.updateNotification(updated)
                                    rulesRepository.upsertRule(feedbackPolicy.toRule(notification, RuleActionUi.DIGEST))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Digest로 보내기")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val updated = feedbackPolicy.applyAction(notification, RuleActionUi.SILENT)
                                    repository.updateNotification(updated)
                                    rulesRepository.upsertRule(feedbackPolicy.toRule(notification, RuleActionUi.SILENT))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("조용히 처리")
                        }
                        // Plan 2026-04-21-ignore-tier-fourth-decision Task 6a —
                        // "무시" sits to the right of "조용히 처리" so the feedback
                        // row reads least-destructive → most-destructive. Tap
                        // opens a confirmation dialog; the dialog's confirm path
                        // handles the DB flip, rule upsert, back-nav, and undo
                        // snackbar.
                        OutlinedButton(
                            onClick = { showIgnoreDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("무시")
                        }
                    }
                }
            }
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(16.dp),
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (showIgnoreDialog) {
        IgnoreConfirmationDialog(
            onConfirm = {
                showIgnoreDialog = false
                scope.launch {
                    applyIgnoreWithUndo(
                        feedbackPolicy = feedbackPolicy,
                        repository = repository,
                        rulesRepository = rulesRepository,
                        notification = notification,
                        existingRules = rules,
                        snackbarHostState = snackbarHostState,
                    )
                    onBack()
                }
            },
            onDismiss = { showIgnoreDialog = false },
        )
    }
}

/**
 * Applies IGNORE to [notification], upserts a matching IGNORE rule, and shows a
 * 3-second undo snackbar. Undo restores the prior notification state and either
 * deletes the newly-created rule or restores the previous rule action (for the
 * case where a rule for this sender/app already existed before).
 *
 * Kept separate from the Composable so the mutation path is readable and so the
 * "rollback on undo" snapshot is explicit. The undo window is in-memory only;
 * process restart finalizes the IGNORE (see plan Risks).
 */
private suspend fun applyIgnoreWithUndo(
    feedbackPolicy: NotificationFeedbackPolicy,
    repository: NotificationRepository,
    rulesRepository: RulesRepository,
    notification: NotificationUiModel,
    existingRules: List<RuleUiModel>,
    snackbarHostState: SnackbarHostState,
) {
    val priorNotification = notification
    val ignoreRule = feedbackPolicy.toRule(notification, RuleActionUi.IGNORE)
    // Match on the same (id || (type, matchValue)) rule identity RulesRepository
    // uses for upsert collisions — otherwise we'd miss an existing rule that
    // was stored under a different id.
    val priorRule = existingRules.firstOrNull { existing ->
        existing.id == ignoreRule.id ||
            (existing.type == ignoreRule.type && existing.matchValue == ignoreRule.matchValue)
    }

    val updated = feedbackPolicy.applyAction(notification, RuleActionUi.IGNORE)
    repository.updateNotification(updated)
    rulesRepository.upsertRule(ignoreRule)

    val result = snackbarHostState.showSnackbar(
        message = "무시됨. 되돌리려면 탭",
        actionLabel = "되돌리기",
        withDismissAction = false,
    )
    if (result == SnackbarResult.ActionPerformed) {
        repository.updateNotification(priorNotification)
        if (priorRule != null) {
            rulesRepository.upsertRule(priorRule)
        } else {
            rulesRepository.deleteRule(ignoreRule.id)
        }
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
