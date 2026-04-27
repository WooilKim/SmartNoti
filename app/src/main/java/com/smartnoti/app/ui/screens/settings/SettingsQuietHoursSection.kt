package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.CapturedAppSelectionItem
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle

@Composable
internal fun QuietHoursWindowPickerRow(
    spec: QuietHoursWindowPickerSpec,
    onStartHourChange: (Int) -> Unit,
    onEndHourChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietHoursHourPicker(
                label = "시작",
                selectedHour = spec.startHour,
                options = spec.hourOptions,
                onHourSelected = onStartHourChange,
                modifier = Modifier.weight(1f),
            )
            QuietHoursHourPicker(
                label = "종료",
                selectedHour = spec.endHour,
                options = spec.hourOptions,
                onHourSelected = onEndHourChange,
                modifier = Modifier.weight(1f),
            )
        }
        if (spec.sameValueWarning != null) {
            Text(
                text = spec.sameValueWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun QuietHoursHourPicker(
    label: String,
    selectedHour: Int,
    options: List<QuietHoursWindowPickerSpec.HourOption>,
    onHourSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.hour == selectedHour }?.label
        ?: "%02d:00".format(selectedHour)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(selectedLabel) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            onHourSelected(option.hour)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 6.
 *
 * Sub-row for the new "조용한 시간 대상 앱" picker. Mirrors the visual tone
 * of the surrounding QuietHours editor row — a labelled count + tap target
 * that opens the bottom-sheet picker. Empty-set warning is rendered inline
 * so the user sees the silently-no-op signal even before opening the sheet.
 */
@Composable
internal fun QuietHoursPackagesPickerRow(
    spec: QuietHoursPackagesPickerSpec,
    onOpenPicker: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPicker),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.ROW_LABEL,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.rowSummary(spec.selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = spec.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "조용한 시간 대상 앱 편집",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (spec.emptyWarning != null) {
            Text(
                text = spec.emptyWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Plan `2026-04-26-quiet-hours-shopping-packages-user-extensible.md` Task 6.
 *
 * Modal bottom sheet body for editing the `quietHoursPackages` set. The
 * sheet has two stacked sections:
 *   - Currently-selected rows with a per-row Close affordance that calls
 *     `onRemovePackage`. Empty state surfaces the same warning copy as the
 *     row above so the user gets a consistent signal.
 *   - Candidate rows (captured apps not yet in the set) with an Add
 *     affordance. The plan intentionally limits candidates to apps that
 *     have actually posted notifications during this session — wiring a
 *     full installed-app picker is Out of scope per the plan's Scope/Out.
 *
 * The sheet does not close on add/remove so the user can perform multiple
 * edits in one session; "닫기" returns to the Settings card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuietHoursPackagesPickerSheet(
    spec: QuietHoursPackagesPickerSpec,
    onDismiss: () -> Unit,
    onAddPackage: (String) -> Unit,
    onRemovePackage: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.PICKER_HEADER_TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = QuietHoursPackagesPickerSpecBuilder.PICKER_HEADER_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (spec.emptyWarning != null) {
                Text(
                    text = spec.emptyWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            QuietHoursPackagesSelectedSection(
                items = spec.items,
                onRemovePackage = onRemovePackage,
            )
            QuietHoursPackagesCandidateSection(
                candidates = spec.candidates,
                onAddPackage = onAddPackage,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    enabled = spec.selectedCount > 0,
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("모두 비우기")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("닫기")
                }
            }
        }
    }
}

@Composable
private fun QuietHoursPackagesSelectedSection(
    items: List<QuietHoursPackagesPickerSpec.Item>,
    onRemovePackage: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "현재 선택된 앱",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (items.isEmpty()) {
            Text(
                text = QuietHoursPackagesPickerSpecBuilder.NO_TARGETS_SUMMARY,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        SmartSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item.supporting ?: item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onRemovePackage(item.packageName) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "${item.displayName} 제거",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietHoursPackagesCandidateSection(
    candidates: List<CapturedAppSelectionItem>,
    onAddPackage: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = QuietHoursPackagesPickerSpecBuilder.ADD_BUTTON_LABEL,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (candidates.isEmpty()) {
            Text(
                text = QuietHoursPackagesPickerSpecBuilder.NO_CANDIDATES_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        SmartSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            candidates.forEachIndexed { index, candidate ->
                if (index > 0) {
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = candidate.appName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${candidate.notificationCount}건 · ${candidate.lastSeenLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { onAddPackage(candidate.packageName) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "${candidate.appName} 추가",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
