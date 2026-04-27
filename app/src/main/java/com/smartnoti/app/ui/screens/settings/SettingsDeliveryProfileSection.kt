package com.smartnoti.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

internal data class DeliveryProfileCallbacks(
    val onAlertLevelChange: (String) -> Unit,
    val onVibrationModeChange: (String) -> Unit,
    val onHeadsUpChange: (Boolean) -> Unit,
    val onLockScreenVisibilityChange: (String) -> Unit,
)

@Composable
internal fun DeliveryProfileSettingsCard(
    settings: SmartNotiSettings,
    priorityCallbacks: DeliveryProfileCallbacks,
    digestCallbacks: DeliveryProfileCallbacks,
    silentCallbacks: DeliveryProfileCallbacks,
    summaryBuilder: SettingsDisclosureSummaryBuilder,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val priorityTokens = remember(
        settings.priorityAlertLevel,
        settings.priorityVibrationMode,
        settings.priorityHeadsUpEnabled,
        settings.priorityLockScreenVisibility,
    ) {
        summaryBuilder.buildDeliveryProfileSummaryTokens(
            alertLevel = settings.priorityAlertLevel,
            vibrationMode = settings.priorityVibrationMode,
            headsUpEnabled = settings.priorityHeadsUpEnabled,
            lockScreenVisibility = settings.priorityLockScreenVisibility,
        )
    }
    val digestTokens = remember(
        settings.digestAlertLevel,
        settings.digestVibrationMode,
        settings.digestHeadsUpEnabled,
        settings.digestLockScreenVisibility,
    ) {
        summaryBuilder.buildDeliveryProfileSummaryTokens(
            alertLevel = settings.digestAlertLevel,
            vibrationMode = settings.digestVibrationMode,
            headsUpEnabled = settings.digestHeadsUpEnabled,
            lockScreenVisibility = settings.digestLockScreenVisibility,
        )
    }
    val silentTokens = remember(
        settings.silentAlertLevel,
        settings.silentVibrationMode,
        settings.silentHeadsUpEnabled,
        settings.silentLockScreenVisibility,
    ) {
        summaryBuilder.buildDeliveryProfileSummaryTokens(
            alertLevel = settings.silentAlertLevel,
            vibrationMode = settings.silentVibrationMode,
            headsUpEnabled = settings.silentHeadsUpEnabled,
            lockScreenVisibility = settings.silentLockScreenVisibility,
        )
    }

    SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        ExpandableSettingsSectionHeader(
            title = "대체 알림 전달 방식",
            subtitle = if (expanded) {
                "설정한 값은 SmartNoti가 원본 알림을 대신 보여줄 때만 적용돼요."
            } else {
                "Priority·Digest·Silent 현재 상태를 먼저 확인하고, 필요할 때만 세부 제약을 펼쳐 조정해요."
            },
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeliveryProfileSummaryRow(
                title = "Priority",
                modeLabel = "즉시 대응",
                accentColor = PriorityOnContainer,
                tokens = priorityTokens,
            )
            DeliveryProfileSummaryRow(
                title = "Digest",
                modeLabel = "묶음 확인",
                accentColor = DigestOnContainer,
                tokens = digestTokens,
            )
            DeliveryProfileSummaryRow(
                title = "Silent",
                modeLabel = "비침습 모드",
                accentColor = SilentOnContainer,
                tokens = silentTokens,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DeliveryProfileEditorSection(
                    title = "Priority",
                    subtitle = "중요 알림은 필요할 때 강하게 알리되, 조용한 시간이나 반복 상황에서는 자동으로 낮아질 수 있어요.",
                    accentColor = PriorityOnContainer,
                    summaryTokens = priorityTokens,
                    selectedAlertLevel = settings.priorityAlertLevel,
                    allowedAlertLevels = listOf(AlertLevel.LOUD, AlertLevel.SOFT, AlertLevel.QUIET),
                    onAlertLevelChange = priorityCallbacks.onAlertLevelChange,
                    selectedVibrationMode = settings.priorityVibrationMode,
                    allowedVibrationModes = listOf(VibrationMode.STRONG, VibrationMode.LIGHT, VibrationMode.OFF),
                    onVibrationModeChange = priorityCallbacks.onVibrationModeChange,
                    headsUpEnabled = settings.priorityHeadsUpEnabled,
                    headsUpEnabledAllowed = true,
                    headsUpDescription = "Heads-up 표시",
                    onHeadsUpChange = priorityCallbacks.onHeadsUpChange,
                    selectedLockScreenVisibility = settings.priorityLockScreenVisibility,
                    allowedLockScreenModes = listOf(
                        LockScreenVisibilityMode.PUBLIC,
                        LockScreenVisibilityMode.PRIVATE,
                        LockScreenVisibilityMode.SECRET,
                    ),
                    onLockScreenVisibilityChange = priorityCallbacks.onLockScreenVisibilityChange,
                )
                DeliveryProfileEditorSection(
                    title = "Digest",
                    subtitle = "Digest는 조용히 다시 확인하는 용도라서 loud·강한 진동·heads-up은 허용하지 않아요.",
                    accentColor = DigestOnContainer,
                    summaryTokens = digestTokens,
                    selectedAlertLevel = settings.digestAlertLevel,
                    allowedAlertLevels = listOf(AlertLevel.SOFT, AlertLevel.QUIET, AlertLevel.NONE),
                    onAlertLevelChange = digestCallbacks.onAlertLevelChange,
                    selectedVibrationMode = settings.digestVibrationMode,
                    allowedVibrationModes = listOf(VibrationMode.LIGHT, VibrationMode.OFF),
                    onVibrationModeChange = digestCallbacks.onVibrationModeChange,
                    headsUpEnabled = false,
                    headsUpEnabledAllowed = false,
                    headsUpDescription = "Digest는 heads-up을 사용하지 않음",
                    onHeadsUpChange = digestCallbacks.onHeadsUpChange,
                    selectedLockScreenVisibility = settings.digestLockScreenVisibility,
                    allowedLockScreenModes = listOf(
                        LockScreenVisibilityMode.PRIVATE,
                        LockScreenVisibilityMode.SECRET,
                    ),
                    onLockScreenVisibilityChange = digestCallbacks.onLockScreenVisibilityChange,
                )
                DeliveryProfileEditorSection(
                    title = "Silent",
                    subtitle = "Silent는 항상 비침습적으로 유지돼요. 소리·진동·heads-up은 사용할 수 없어요.",
                    accentColor = SilentOnContainer,
                    summaryTokens = silentTokens,
                    selectedAlertLevel = settings.silentAlertLevel,
                    allowedAlertLevels = listOf(AlertLevel.NONE, AlertLevel.QUIET),
                    onAlertLevelChange = silentCallbacks.onAlertLevelChange,
                    selectedVibrationMode = settings.silentVibrationMode,
                    allowedVibrationModes = listOf(VibrationMode.OFF),
                    onVibrationModeChange = silentCallbacks.onVibrationModeChange,
                    headsUpEnabled = false,
                    headsUpEnabledAllowed = false,
                    headsUpDescription = "Silent는 heads-up을 사용하지 않음",
                    onHeadsUpChange = silentCallbacks.onHeadsUpChange,
                    selectedLockScreenVisibility = settings.silentLockScreenVisibility,
                    allowedLockScreenModes = listOf(
                        LockScreenVisibilityMode.PRIVATE,
                        LockScreenVisibilityMode.SECRET,
                    ),
                    onLockScreenVisibilityChange = silentCallbacks.onLockScreenVisibilityChange,
                )
            }
        }
    }
}

@Composable
private fun DeliveryProfileSummaryRow(
    title: String,
    modeLabel: String,
    accentColor: Color,
    tokens: DeliveryProfileSummaryTokens,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = accentColor, shape = RoundedCornerShape(999.dp)),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = tokens.toSummaryLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}

@Composable
private fun DeliveryProfileEditorSection(
    title: String,
    subtitle: String,
    accentColor: Color,
    summaryTokens: DeliveryProfileSummaryTokens,
    selectedAlertLevel: String,
    allowedAlertLevels: List<AlertLevel>,
    onAlertLevelChange: (String) -> Unit,
    selectedVibrationMode: String,
    allowedVibrationModes: List<VibrationMode>,
    onVibrationModeChange: (String) -> Unit,
    headsUpEnabled: Boolean,
    headsUpEnabledAllowed: Boolean,
    headsUpDescription: String,
    onHeadsUpChange: (Boolean) -> Unit,
    selectedLockScreenVisibility: String,
    allowedLockScreenModes: List<LockScreenVisibilityMode>,
    onLockScreenVisibilityChange: (String) -> Unit,
) {
    SmartSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = accentColor, shape = RoundedCornerShape(999.dp)),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "현재 ${summaryTokens.toSummaryLine()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DeliveryProfileControlGroup(title = "신호") {
            DeliveryProfileOptionRow(
                label = "알림",
                selected = selectedAlertLevel,
                options = allowedAlertLevels.map { it.name to it.toKoreanLabel() },
                onSelected = onAlertLevelChange,
            )
            DeliveryProfileOptionRow(
                label = "진동",
                selected = selectedVibrationMode,
                options = allowedVibrationModes.map { it.name to it.toKoreanLabel() },
                onSelected = onVibrationModeChange,
            )
        }
        HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
        DeliveryProfileControlGroup(title = "표시와 보호") {
            DeliveryProfileHeadsUpRow(
                headsUpEnabled = headsUpEnabled,
                headsUpEnabledAllowed = headsUpEnabledAllowed,
                headsUpDescription = headsUpDescription,
                onHeadsUpChange = onHeadsUpChange,
            )
            DeliveryProfileOptionRow(
                label = "잠금 화면",
                selected = selectedLockScreenVisibility,
                options = allowedLockScreenModes.map { it.name to it.toKoreanLabel() },
                onSelected = onLockScreenVisibilityChange,
            )
        }
    }
}

@Composable
private fun DeliveryProfileControlGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeliveryProfileOptionRow(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.first == selected,
                    onClick = { onSelected(option.first) },
                    label = { Text(option.second) },
                )
            }
        }
    }
}

@Composable
private fun DeliveryProfileHeadsUpRow(
    headsUpEnabled: Boolean,
    headsUpEnabledAllowed: Boolean,
    headsUpDescription: String,
    onHeadsUpChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Heads-up",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = headsUpDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!headsUpEnabledAllowed) {
                Text(
                    text = "안전한 전달을 위해 고정됨",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = headsUpEnabled,
            enabled = headsUpEnabledAllowed,
            onCheckedChange = onHeadsUpChange,
        )
    }
}

private fun DeliveryProfileSummaryTokens.toSummaryLine(): String =
    listOf(alertLabel, vibrationLabel, headsUpLabel, lockScreenLabel).joinToString(" · ")
