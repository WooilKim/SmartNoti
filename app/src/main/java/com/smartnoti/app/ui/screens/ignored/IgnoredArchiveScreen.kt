package com.smartnoti.app.ui.screens.ignored

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
import androidx.compose.material3.AlertDialog
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
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.launch

/**
 * 무시됨 아카이브 screen — opt-in archive for IGNORE-status rows.
 *
 * Plan `2026-04-21-ignore-tier-fourth-decision` Task 6 introduced the screen.
 * Plan `2026-04-27-ignored-archive-bulk-restore-and-clear.md` Tasks 3-7 wired
 * the bulk-action surface — header `모두 PRIORITY 로 복구` / `모두 지우기`
 * (with confirmation), per-row `PRIORITY 로 복구` TextButton, and long-press
 * multi-select with a sticky [IgnoredArchiveActionBar] that collects selected
 * IGNORE rows for `모두 지우기`. Single-row inline restore is hidden while
 * selection mode is active so the chrome does not compete with the bulk
 * affordance (mirrors the priority-inbox pattern).
 *
 * Recovery target is hard-coded to PRIORITY: IGNORE rows were paths the user
 * intended to discard, so once they reverse course PRIORITY is the most
 * visible bucket to land in. Re-routing future hits of the same Category
 * still requires editing the owning Category in the rules editor — restore
 * here only flips the row, not the rule (in line with the screen's existing
 * Out-of-scope contract).
 */
@Composable
fun IgnoredArchiveScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val notifications by repository.observeIgnoredArchive()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Plan `2026-04-27-ignored-archive-bulk-restore-and-clear.md` Tasks 3+5 —
    // pure multi-select state machine. Long-press enters; subsequent body
    // taps toggle while active; explicit "취소" or post-success cleanup
    // returns to the inactive state.
    var multiSelectState by remember { mutableStateOf(IgnoredArchiveMultiSelectState()) }

    // Plan Task 5 step 2 — confirm dialog for the destructive header action.
    // Captures the row count at open time so the snackbar copy reflects what
    // the user actually saw on the button.
    var pendingClearAll by remember { mutableStateOf(false) }
    val pendingClearAllCount = if (pendingClearAll) notifications.size else 0

    // Plan Task 5 step 5 — same confirmation pattern reused for the multi-
    // select bulk delete. Captures the selection at open time so the
    // dispatch sees a stable list even if the upstream Flow re-emits.
    var pendingMultiSelectDelete by remember { mutableStateOf<Set<String>?>(null) }

    if (notifications.isEmpty()) {
        // Reset selection if the list emptied while selection was active —
        // every selected row was successfully deleted/restored or otherwise
        // removed by the upstream flow. Keeps the next entry to the screen
        // from inheriting stale selection IDs.
        if (multiSelectState.isActive) {
            multiSelectState = IgnoredArchiveMultiSelectState()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ScreenHeader(
                        eyebrow = "아카이브",
                        title = "무시됨",
                        subtitle = "IGNORE 규칙이 매치된 알림은 여기에 쌓여요. 기본 뷰에는 나타나지 않아요.",
                    )
                }
                item {
                    EmptyState(
                        title = "무시된 알림이 없어요",
                        subtitle = "IGNORE 액션 규칙을 만들면 여기에 쌓이기 시작해요.",
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentPadding)
                    .padding(16.dp),
            )
        }
        return
    }

    if (pendingClearAll) {
        AlertDialog(
            onDismissRequest = { pendingClearAll = false },
            title = { Text("무시된 알림 ${pendingClearAllCount}건을 모두 지울까요?") },
            text = { Text("되돌릴 수 없어요. SmartNoti 내 기록만 지우는 거라 시스템 알림센터에는 영향이 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    val capturedCount = pendingClearAllCount
                    pendingClearAll = false
                    scope.launch {
                        repository.deleteAllIgnored()
                        snackbarHostState.showSnackbar(
                            message = "무시된 알림 ${capturedCount}건을 모두 지웠어요",
                            duration = SnackbarDuration.Short,
                        )
                    }
                }) {
                    Text("모두 지우기")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearAll = false }) {
                    Text("취소")
                }
            },
        )
    }

    val multiSelectDeleteIds = pendingMultiSelectDelete
    if (multiSelectDeleteIds != null) {
        AlertDialog(
            onDismissRequest = { pendingMultiSelectDelete = null },
            title = { Text("선택한 알림 ${multiSelectDeleteIds.size}건을 지울까요?") },
            text = { Text("되돌릴 수 없어요. 선택한 IGNORE 알림만 SmartNoti 기록에서 사라져요.") },
            confirmButton = {
                TextButton(onClick = {
                    val captured = multiSelectDeleteIds
                    pendingMultiSelectDelete = null
                    multiSelectState = IgnoredArchiveMultiSelectState()
                    scope.launch {
                        repository.deleteIgnoredByIds(captured)
                        snackbarHostState.showSnackbar(
                            message = "선택한 알림 ${captured.size}건을 지웠어요",
                            duration = SnackbarDuration.Short,
                        )
                    }
                }) {
                    Text("지우기")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMultiSelectDelete = null }) {
                    Text("취소")
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "아카이브",
                    title = "무시됨",
                    subtitle = "SmartNoti 가 IGNORE 규칙으로 즉시 정리한 알림이에요. 원본 알림센터에서도 사라진 상태예요.",
                )
            }
            item {
                SmartSurfaceCard {
                    Text(
                        text = "보관 중 ${notifications.size}건",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "최신 순으로 정렬돼 있어요. 길게 누르면 여러 건을 한 번에 지울 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Plan Task 5 step 1+3 — header bulk affordances. Hidden
                    // while multi-select is active so the ActionBar is the
                    // sole bulk surface on screen.
                    if (!multiSelectState.isActive) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val captured = notifications.size
                                    scope.launch {
                                        repository.restoreAllIgnoredToPriority()
                                        snackbarHostState.showSnackbar(
                                            message = "무시된 알림 ${captured}건을 PRIORITY 로 복구했어요",
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("모두 PRIORITY 로 복구")
                            }
                            OutlinedButton(
                                onClick = { pendingClearAll = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("모두 지우기")
                            }
                        }
                    }
                }
            }
            // Plan Task 5 step 5 — ActionBar mounts inline between the
            // SmartSurfaceCard and the row list when selection mode is
            // active. In-flow placement keeps padding rhythm consistent
            // with the rest of the screen.
            if (multiSelectState.isActive) {
                item(key = "ignored-archive-multi-select-action-bar") {
                    IgnoredArchiveActionBar(
                        selectedCount = multiSelectState.count,
                        onDeleteClick = {
                            pendingMultiSelectDelete = multiSelectState.selectedNotificationIds
                        },
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
                    // Plan Task 5 step 6 — single-row PRIORITY restore is
                    // exposed only while selection mode is inactive. The
                    // recovery is non-destructive (status flip + dedup
                    // reason tag), so a confirmation dialog would feel
                    // heavier than the action warrants — snackbar is
                    // sufficient feedback.
                    if (!multiSelectState.isActive) {
                        TextButton(
                            onClick = {
                                val updated = notification.copy(
                                    status = NotificationStatusUi.PRIORITY,
                                    reasonTags = appendUserClassificationTag(notification.reasonTags),
                                )
                                scope.launch {
                                    repository.updateNotification(updated)
                                    snackbarHostState.showSnackbar(
                                        message = "이 알림을 PRIORITY 로 복구했어요",
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text("PRIORITY 로 복구")
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
        )
    }
}

/**
 * Mirrors the repository-side `appendUserClassificationReasonTag` so the
 * single-row restore TextButton lands the same `사용자 분류` dedup tag the
 * bulk header CTA uses. Kept private to the screen — the repository is the
 * source of truth for the bulk path; this is only used when the UI builds
 * an updated `NotificationUiModel` for `updateNotification(...)`.
 */
private fun appendUserClassificationTag(existing: List<String>): List<String> {
    val tag = "사용자 분류"
    if (existing.contains(tag)) return existing
    return existing + tag
}
