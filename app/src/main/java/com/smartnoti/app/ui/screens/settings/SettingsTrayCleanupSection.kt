package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.TrayOrphanCleanupRunner
import com.smartnoti.app.ui.components.SettingsToggleRow
import com.smartnoti.app.ui.components.SmartSurfaceCard

/**
 * Plan `docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`
 * Task 3 — pure builder that turns a [TrayOrphanCleanupRunner.PreviewResult]
 * into the single user-facing line the "트레이 정리" Settings card renders
 * under its header.
 *
 * Branch table (mirrors the four [SettingsTrayCleanupSummaryBuilderTest]
 * fixtures):
 *
 *   - listener not bound → "알림 권한 활성 후 다시 시도해 주세요"
 *   - candidateCount == 0 → "정리할 원본 알림이 없어요"
 *   - 1..3 packages       → "원본 알림 N건 정리 가능 (라벨1, 라벨2, 라벨3)"
 *   - >3 packages         → "원본 알림 N건 정리 가능 (라벨1, 라벨2, 라벨3 외 K개)"
 *
 * Labels are looked up via [appLabelLookup] (typically the listener's
 * `AppLabelResolver::resolve`) so this builder stays Android-free at
 * unit-test time.
 */
class SettingsTrayCleanupSummaryBuilder {
    fun build(
        preview: TrayOrphanCleanupRunner.PreviewResult,
        isListenerBound: Boolean,
        appLabelLookup: (String) -> String,
    ): String {
        if (!isListenerBound) return "알림 권한 활성 후 다시 시도해 주세요"
        if (preview.candidateCount == 0) return "정리할 원본 알림이 없어요"

        val totalPackages = preview.candidatePackageNames.size
        val shown = preview.candidatePackageNames.take(MAX_LABELS).map(appLabelLookup)
        val labels = shown.joinToString(separator = ", ")
        val overflow = totalPackages - shown.size

        val parens = if (overflow > 0) {
            "($labels 외 ${overflow}개)"
        } else {
            "($labels)"
        }
        return "원본 알림 ${preview.candidateCount}건 정리 가능 $parens"
    }

    private companion object {
        const val MAX_LABELS = 3
    }
}

/**
 * Plan `docs/plans/2026-04-28-fix-issue-524-tray-orphan-cleanup-button.md`
 * Task 3 — Settings card that drives the user-triggered tray-cleanup flow.
 *
 * Layout (from the plan body):
 *   - [SmartSurfaceCard] container (matches sibling settings cards).
 *   - "트레이 정리" header.
 *   - Live preview text from [SettingsTrayCleanupSummaryBuilder].
 *   - "정리 전 미리보기" toggle (default ON, screen-lifetime only).
 *   - "모두 정리하기" primary button — enabled when preview is ON and
 *     candidateCount > 0.
 *   - Confirm dialog (default per plan Open Question R1) with a
 *     "다시 묻지 않기" checkbox. Skip-confirm preference is hoisted via
 *     [skipConfirm] / [onSkipConfirmChange] so Task 4 can wire the
 *     [com.smartnoti.app.data.settings.SettingsRepository] DataStore key
 *     `tray_cleanup_skip_confirm_v1`.
 *
 * State hoisting: the preview itself is owned by the caller (Task 4 wires
 * a `LaunchedEffect` that calls `runner.preview()` on entry / after a
 * cleanup). This Composable is pure — it never calls the runner directly,
 * so it stays trivially previewable in isolation.
 */
@Composable
internal fun SettingsTrayCleanupSection(
    preview: TrayOrphanCleanupRunner.PreviewResult?,
    isListenerBound: Boolean,
    appLabelLookup: (String) -> String,
    skipConfirm: Boolean,
    onSkipConfirmChange: (Boolean) -> Unit,
    onCleanupConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    summaryBuilder: SettingsTrayCleanupSummaryBuilder = remember { SettingsTrayCleanupSummaryBuilder() },
) {
    var previewEnabled by remember { mutableStateOf(true) }
    var pendingConfirm by remember { mutableStateOf(false) }
    var dialogSkipFuture by remember { mutableStateOf(skipConfirm) }

    val resolvedPreview = preview ?: TrayOrphanCleanupRunner.PreviewResult(
        candidateCount = 0,
        candidatePackageNames = emptyList(),
    )
    val summary = summaryBuilder.build(
        preview = resolvedPreview,
        isListenerBound = isListenerBound,
        appLabelLookup = appLabelLookup,
    )
    val canCleanup = isListenerBound && previewEnabled && resolvedPreview.candidateCount > 0

    SmartSurfaceCard(modifier = modifier.fillMaxWidth()) {
        SettingsSubsection(
            title = "트레이 정리",
            subtitle = summary,
            isFirst = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsToggleRow(
                    title = "정리 전 미리보기",
                    checked = previewEnabled,
                    onCheckedChange = { previewEnabled = it },
                    subtitle = if (previewEnabled) {
                        "정리 가능한 원본 알림의 개수와 앱 이름을 위에서 미리 확인할 수 있어요."
                    } else {
                        "미리보기를 끄면 \"모두 정리하기\" 버튼이 비활성돼요. 안전 가드용 토글이에요."
                    },
                )
                Button(
                    enabled = canCleanup,
                    onClick = {
                        if (skipConfirm) {
                            onCleanupConfirmed()
                        } else {
                            dialogSkipFuture = false
                            pendingConfirm = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("모두 정리하기")
                }
            }
        }
    }

    if (pendingConfirm) {
        AlertDialog(
            onDismissRequest = { pendingConfirm = false },
            title = {
                Text("원본 알림 ${resolvedPreview.candidateCount}건을 정리할까요?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "선택한 앱들의 원본 알림이 알림 트레이에서 사라져요. 음악·통화·길안내 같은 보호 알림은 그대로 남아요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = dialogSkipFuture,
                            onCheckedChange = { dialogSkipFuture = it },
                        )
                        Text(
                            text = "다시 묻지 않기",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dialogSkipFuture != skipConfirm) {
                        onSkipConfirmChange(dialogSkipFuture)
                    }
                    pendingConfirm = false
                    onCleanupConfirmed()
                }) {
                    Text("정리")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = false }) {
                    Text("취소")
                }
            },
        )
    }
}
