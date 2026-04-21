package com.smartnoti.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Confirmation dialog for the Detail screen's "무시" (IGNORE) feedback button.
 *
 * IGNORE is the fourth — and most destructive — decision tier added by plan
 * `2026-04-21-ignore-tier-fourth-decision`. A matching IGNORE rule will be
 * auto-upserted for the sender/app on confirmation, so the user needs to
 * understand that:
 *
 * 1. the notification (and its siblings going forward) will be treated as
 *    delete-level — cancelled from the tray and filtered out of the default
 *    SmartNoti inbox views;
 * 2. the row itself is still persisted and reachable through Settings >
 *    "무시된 알림 보기" if they want to audit or restore.
 *
 * The confirm flow returns through [onConfirm]; dismiss/cancel both collapse
 * the dialog via [onDismiss]. The Detail screen wires an undo snackbar right
 * after [onConfirm] so the 3-second window is orthogonal to this dialog.
 */
@Composable
fun IgnoreConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "이 알림을 무시할까요?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Text(
                text = "이 알림을 무시하면 앱에서도 삭제됩니다. 되돌리려면 설정 > 무시된 알림 보기.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "무시")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "취소")
            }
        },
    )
}
