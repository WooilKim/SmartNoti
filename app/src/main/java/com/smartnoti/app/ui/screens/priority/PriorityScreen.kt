package com.smartnoti.app.ui.screens.priority

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleActionUi
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
    val scope = rememberCoroutineScope()
    val settings by settingsRepository.observeSettings().collectAsStateWithLifecycle(initialValue = com.smartnoti.app.data.settings.SmartNotiSettings())
    val notificationsFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observePriorityFiltered(settings.hidePersistentNotifications)
    }
    val notifications by notificationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    if (notifications.isEmpty()) {
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
        items(notifications, key = { it.id }) { notification ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NotificationCard(model = notification, onClick = onNotificationClick)
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
