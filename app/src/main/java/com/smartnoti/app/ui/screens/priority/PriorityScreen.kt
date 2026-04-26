package com.smartnoti.app.ui.screens.priority

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.usecase.BulkPassthroughReviewReclassifyDispatcher
import com.smartnoti.app.domain.usecase.NotificationFeedbackPolicy
import com.smartnoti.app.domain.usecase.PassthroughReviewReclassifyDispatcher
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import kotlinx.coroutines.launch

/**
 * Passthrough Review screen.
 *
 * Formerly the "중요 알림" tab. The UX has reframed to surface the PRIORITY
 * bucket as "SmartNoti 가 건드리지 않은 알림" — notifications the classifier let
 * through unchanged. The screen is the action sink for the Home passthrough
 * review card (see `HomePassthroughReviewCard`) and each row carries inline
 * reclassify actions so the user can Digest / Silence / turn the notification
 * into a rule without opening the detail screen.
 *
 * Plan: `docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md` Phase A Task 3.
 *
 * Plan `2026-04-26-priority-inbox-bulk-reclassify.md` Tasks 5+6 added the
 * multi-select gesture (long-press a card to enter selection mode, body taps
 * toggle while active), a sticky `PriorityMultiSelectActionBar`, and a
 * snackbar host for the bulk "→ Digest" / "→ 조용히" confirmations. Single-row
 * inline `PassthroughReclassifyActions` is hidden while selection mode is
 * active so the chrome does not compete with the bulk affordance.
 */
@Composable
fun PriorityScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onCreateRuleClick: (NotificationUiModel) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val dispatcher = remember(repository, rulesRepository) {
        PassthroughReviewReclassifyDispatcher(
            feedbackPolicy = NotificationFeedbackPolicy(),
            updateNotification = { repository.updateNotification(it) },
            upsertRule = { rulesRepository.upsertRule(it) },
        )
    }
    // Plan `2026-04-26-priority-inbox-bulk-reclassify.md` Task 6 step 1 —
    // bulk wrapper composes over the same single-row dispatcher so
    // NOOP/IGNORED/UPDATED accounting stays in one place.
    val bulkDispatcher = remember(dispatcher) {
        BulkPassthroughReviewReclassifyDispatcher(dispatcher)
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = com.smartnoti.app.data.settings.SmartNotiSettings())
    val notificationsFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observePriorityFiltered(settings.hidePersistentNotifications)
    }
    val notifications by notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    // Plan `2026-04-26-priority-inbox-bulk-reclassify.md` Tasks 5+6 — pure
    // multi-select state machine. Long-press enters; subsequent body taps
    // toggle while active; explicit "취소" clears; bulk action success
    // also clears so the ActionBar disappears cleanly.
    var multiSelectState by remember { mutableStateOf(PriorityScreenMultiSelectState()) }

    if (notifications.isEmpty()) {
        // Reset selection if the list emptied while selection was active —
        // every selected row was successfully reclassified or otherwise
        // removed by the upstream flow. Keeps the next entry to the screen
        // from inheriting stale selection IDs.
        if (multiSelectState.isActive) {
            multiSelectState = PriorityScreenMultiSelectState()
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "검토",
                    title = "검토 대기 알림",
                    subtitle = "SmartNoti 가 건드리지 않은 알림이 들어오면 여기에서 재분류할 수 있어요.",
                )
            }
            item {
                EmptyState(
                    title = "검토할 알림이 없어요",
                    subtitle = "들어오는 알림은 SmartNoti 가 자동으로 분류하고, 그대로 통과한 알림만 여기에 모여요",
                )
            }
        }
        return
    }

    // Plan Task 6 step 2 — selected NotificationUiModel lookup. We
    // re-derive on each recomposition so the bulk dispatch always sees the
    // current models even if the upstream flow re-emitted between
    // long-press and ActionBar tap.
    val selectedModels = remember(notifications, multiSelectState.selectedNotificationIds) {
        notifications.filter { it.id in multiSelectState.selectedNotificationIds }
    }

    fun runBulk(action: RuleActionUi, label: String) {
        // Capture the selection at tap time — the launch coroutine sees a
        // stable list even if the upstream Flow re-emits before persistence
        // completes. Same pattern as RulesScreen's bulk-assign flow.
        val capturedModels = selectedModels.toList()
        if (capturedModels.isEmpty()) return
        multiSelectState = PriorityScreenMultiSelectState()
        scope.launch {
            val result = bulkDispatcher.bulk(capturedModels, action)
            // Plan Task 6 step 3 — snackbar only when at least one row
            // actually persisted. NOOP-only path keeps the selection so
            // the user can retry, but here we already cleared selection
            // for the success path; an all-NOOP outcome is rare (every
            // selected row was already in the target status) so the
            // "no feedback" trade-off is acceptable per the plan's
            // Product intent.
            if (result.persistedCount > 0) {
                snackbarHostState.showSnackbar(
                    message = "알림 ${result.persistedCount}건을 ${label}로 옮겼어요",
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenHeader(
                    eyebrow = "검토",
                    title = "SmartNoti 가 건드리지 않은 알림",
                    subtitle = "이 판단이 맞는지 확인하고, 필요하면 바로 Digest / 조용히 / 규칙으로 보낼 수 있어요.",
                )
            }
            item {
                SmartSurfaceCard {
                    Text(
                        text = "검토 대기 ${notifications.size}건",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "SmartNoti 가 자동 처리하지 않고 원본 그대로 전달한 알림이에요. 틀렸다고 느낀 건만 골라 재분류해주세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Plan Task 5 step 3 — ActionBar mounts inline between the
            // SmartSurfaceCard and the row list when selection mode is
            // active. In-flow placement keeps padding rhythm consistent
            // with the rest of the screen and avoids a Floating overlay.
            if (multiSelectState.isActive) {
                item(key = "bulk-multi-select-action-bar") {
                    PriorityMultiSelectActionBar(
                        selectedCount = multiSelectState.selectedNotificationIds.size,
                        onSendToDigest = { runBulk(RuleActionUi.DIGEST, "Digest") },
                        onSilence = { runBulk(RuleActionUi.SILENT, "조용히") },
                        onCancelClick = { multiSelectState = multiSelectState.cancel() },
                    )
                }
            }
            items(notifications, key = { it.id }) { notification ->
                val isSelected = notification.id in multiSelectState.selectedNotificationIds
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NotificationCard(
                        model = notification,
                        onClick = { id ->
                            if (multiSelectState.isActive) {
                                multiSelectState = multiSelectState.toggle(id)
                            } else {
                                onNotificationClick(id)
                            }
                        },
                        onLongClick = {
                            if (!multiSelectState.isActive) {
                                multiSelectState = multiSelectState.enterSelection(notification.id)
                            }
                        },
                        isSelected = isSelected,
                    )
                    // Plan Task 5 step 3 — single-row inline actions are
                    // hidden while selection mode is active so the bulk
                    // ActionBar is the only reclassify affordance on
                    // screen. Restoring on cancel/exit is automatic.
                    if (!multiSelectState.isActive) {
                        PassthroughReclassifyActions(
                            onSendToDigest = {
                                scope.launch {
                                    dispatcher.reclassify(
                                        notification = notification,
                                        action = RuleActionUi.DIGEST,
                                        createRule = false,
                                    )
                                }
                            },
                            onSilence = {
                                scope.launch {
                                    dispatcher.reclassify(
                                        notification = notification,
                                        action = RuleActionUi.SILENT,
                                        createRule = false,
                                    )
                                }
                            },
                            onCreateRule = { onCreateRuleClick(notification) },
                        )
                    }
                }
            }
        }
        // Plan Task 6 step 5 — snackbar overlay for bulk-reclassify
        // confirmation copy. Hosted inside the same Box that wraps the
        // LazyColumn so the toast lifts above the parent Scaffold's
        // bottom inset (BottomNav). Mirrors the RulesScreen pattern.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(16.dp),
        )
    }
}

@Composable
private fun PassthroughReclassifyActions(
    onSendToDigest: () -> Unit,
    onSilence: () -> Unit,
    onCreateRule: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "이 판단을 바꿀까요?",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSendToDigest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("→ Digest")
                }
                OutlinedButton(
                    onClick = onSilence,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("→ 조용히")
                }
            }
            TextButton(
                onClick = onCreateRule,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("→ 규칙 만들기")
            }
        }
    }
}
