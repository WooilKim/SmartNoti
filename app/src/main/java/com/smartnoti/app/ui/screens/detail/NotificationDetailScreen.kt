package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.domain.model.RuleActionUi
import com.smartnoti.app.domain.usecase.NotificationDetailDeliveryProfileSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationFeedbackPolicy
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.ReasonChipRow
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.StatusBadge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NotificationDetailScreen(
    contentPadding: PaddingValues,
    notificationId: String,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val feedbackPolicy = remember { NotificationFeedbackPolicy() }
    val deliveryProfileSummaryBuilder = remember { NotificationDetailDeliveryProfileSummaryBuilder() }
    val scope = remember { CoroutineScope(Dispatchers.IO) }
    val liveNotification by repository.observeNotification(notificationId).collectAsState(initial = null)
    val notification = liveNotification
    val deliveryProfileSummary = remember(notification) {
        notification?.let(deliveryProfileSummaryBuilder::build)
    }

    if (notification == null) {
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenHeader(
                    title = "알림 상세",
                    subtitle = "캡처된 알림만 상세 화면에서 확인할 수 있어요.",
                )
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

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("알림 상세", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "왜 이렇게 처리됐나요?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ReasonChipRow(notification.reasonTags)
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
                    }
                }
            }
        }
    }
}
