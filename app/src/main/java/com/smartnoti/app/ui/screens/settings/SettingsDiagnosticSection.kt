package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.ui.components.SettingsCardHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 3.
 *
 * Settings → "진단" section. Two switches + one button:
 *
 *  - "로그 기록" — top-level opt-in switch. Default OFF. When OFF the
 *    `DiagnosticLogger` short-circuits every call so the notification
 *    pipeline pays no allocation cost.
 *  - "본문 raw 기록 — 개인정보 포함 가능" — sub-toggle, only enabled when
 *    "로그 기록" is ON. When OFF (default) the logger writes only a
 *    12-hex SHA-256 prefix of the title; when ON the raw title text is
 *    embedded alongside the hash.
 *  - "로그 export" — fires the `ACTION_SEND` chooser with the rotated +
 *    live log files via `${applicationId}.diagnosticfileprovider`.
 *
 * State-driven (no Singleton lookups inside the section) so
 * [SettingsDiagnosticSectionTest] can drive it under a Robolectric +
 * `createComposeRule` harness.
 */
@Composable
internal fun SettingsDiagnosticSection(
    state: SettingsDiagnosticSectionState,
    onLoggingEnabledChange: (Boolean) -> Unit,
    onRawTitleBodyEnabledChange: (Boolean) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartSurfaceCard(modifier = modifier.fillMaxWidth()) {
        SettingsCardHeader(
            eyebrow = "진단",
            title = "진단 로그",
            subtitle = "알림 처리 단계별 결정 내용을 파일로 기록해 개발자에게 공유할 수 있어요. 기록은 사용자가 켤 때만 동작합니다. 본문은 기본적으로 hash 처리됩니다.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DiagnosticToggleRow(
                tag = SettingsDiagnosticSectionTags.LOGGING_SWITCH,
                title = "로그 기록",
                subtitle = if (state.loggingEnabled) {
                    "켜진 동안만 진단 로그가 파일에 추가돼요. 끄면 기존 파일은 유지되지만 새 항목은 기록되지 않아요."
                } else {
                    "기본 OFF. 켜면 알림이 어떻게 분류·전달됐는지 줄 단위 JSON으로 기록돼요."
                },
                checked = state.loggingEnabled,
                enabled = true,
                onCheckedChange = onLoggingEnabledChange,
            )
            DiagnosticToggleRow(
                tag = SettingsDiagnosticSectionTags.RAW_SWITCH,
                title = "본문 raw 기록",
                subtitle = "개인정보 포함 가능. OFF면 제목은 SHA-256 12자 hash로만 기록돼요.",
                checked = state.rawTitleBodyEnabled,
                enabled = state.loggingEnabled,
                onCheckedChange = onRawTitleBodyEnabledChange,
            )
            OutlinedButton(
                onClick = onExportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsDiagnosticSectionTags.EXPORT_BUTTON),
            ) {
                Text("로그 export")
            }
        }
    }
}

internal data class SettingsDiagnosticSectionState(
    val loggingEnabled: Boolean,
    val rawTitleBodyEnabled: Boolean,
)

internal object SettingsDiagnosticSectionTags {
    const val LOGGING_SWITCH = "settings_diagnostic_logging_switch"
    const val RAW_SWITCH = "settings_diagnostic_raw_switch"
    const val EXPORT_BUTTON = "settings_diagnostic_export_button"
}

@Composable
private fun DiagnosticToggleRow(
    tag: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}
