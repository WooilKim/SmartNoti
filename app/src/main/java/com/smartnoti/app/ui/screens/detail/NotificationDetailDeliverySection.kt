package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.usecase.NotificationDetailDeliveryProfileSummary
import com.smartnoti.app.domain.usecase.NotificationDetailSourceSuppressionSummary
import com.smartnoti.app.notification.MarkSilentProcessedTrayCancelChain
import com.smartnoti.app.notification.SmartNotiNotificationListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "어떻게 전달되나요?" 카드. summary 가 null 이면 nothing emit. Refactor
 * `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md` Task 2 에서
 * root composable 에서 cut-and-paste 됨, 동작 변경 없음.
 */
@Composable
internal fun NotificationDetailDeliveryProfileCard(
    summary: NotificationDetailDeliveryProfileSummary?,
) {
    if (summary == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "어떻게 전달되나요?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                summary.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "전달 모드 · ${summary.deliveryModeLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "소리 · ${summary.alertLevelLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "진동 · ${summary.vibrationLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Heads-up · ${summary.headsUpLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "잠금화면 · ${summary.lockScreenVisibilityLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * "원본 알림 처리 상태" 카드. summary 가 null 이면 nothing emit. Refactor
 * `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md` Task 2 에서
 * root composable 에서 cut-and-paste 됨, 동작 변경 없음.
 */
@Composable
internal fun NotificationDetailSourceSuppressionCard(
    summary: NotificationDetailSourceSuppressionSummary?,
) {
    if (summary == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "원본 알림 처리 상태",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                summary.overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "원본 상태 · ${summary.statusLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "대체 알림 · ${summary.replacementLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * "조용히 보관 중 / 처리 완료로 표시" 카드. visibility gate
 * (status == SILENT && silentMode == ARCHIVED) 도 함수 안에서 처리됨.
 * Refactor `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md`
 * Task 2 에서 root composable 에서 cut-and-paste 됨, 동작 변경 없음 —
 * `MarkSilentProcessedTrayCancelChain` 호출 체인은 lambda 안에 그대로 보존.
 */
@Composable
internal fun NotificationDetailArchivedCompletionCard(
    notification: NotificationUiModel,
    repository: NotificationRepository,
    scope: CoroutineScope,
) {
    if (notification.status != NotificationStatusUi.SILENT ||
        notification.silentMode != SilentMode.ARCHIVED
    ) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
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
