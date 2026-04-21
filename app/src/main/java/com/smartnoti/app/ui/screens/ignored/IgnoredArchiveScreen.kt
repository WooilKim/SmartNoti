package com.smartnoti.app.ui.screens.ignored

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard

/**
 * 무시됨 아카이브 screen — opt-in archive for IGNORE-status rows.
 *
 * Plan `2026-04-21-ignore-tier-fourth-decision` Task 6. This is the only
 * in-app surface that renders IGNORE rows. The route is registered in
 * [com.smartnoti.app.navigation.AppNavHost] only when the
 * `showIgnoredArchive` settings toggle is on, so navigating here is
 * impossible when the user has not opted in.
 *
 * Intentionally a plain list (no group collapsing, no reclassify actions) —
 * Task 6 scope limits this screen to audit/recovery. Detail-level IGNORE
 * feedback + undo land with Task 6a.
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

    if (notifications.isEmpty()) {
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
        return
    }

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
                    text = "최신 순으로 정렬돼 있어요. 탭하면 상세 화면에서 어떤 규칙이 걸렸는지 확인할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(notifications, key = { it.id }) { notification ->
            NotificationCard(model = notification, onClick = onNotificationClick)
        }
    }
}
