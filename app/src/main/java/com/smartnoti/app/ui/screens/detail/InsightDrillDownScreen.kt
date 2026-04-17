package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.domain.usecase.InsightDrillDownBuilder
import com.smartnoti.app.domain.usecase.InsightDrillDownFilter
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.NotificationCard
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SmartSurfaceCard

@Composable
fun InsightDrillDownScreen(
    contentPadding: PaddingValues,
    filterType: String,
    filterValue: String,
    onNotificationClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val drillDownBuilder = remember { InsightDrillDownBuilder() }
    val notifications by repository.observeAll().collectAsState(initial = emptyList())
    val filter = remember(filterType, filterValue) {
        when (filterType) {
            "app" -> InsightDrillDownFilter.App(appName = filterValue)
            else -> InsightDrillDownFilter.Reason(reasonTag = filterValue)
        }
    }
    val result = remember(notifications, filter) {
        drillDownBuilder.build(notifications = notifications, filter = filter)
    }

    if (result.notifications.isEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenHeader(
                    eyebrow = "Insight",
                    title = result.title,
                    subtitle = result.subtitle,
                )
            }
            item {
                EmptyState(
                    title = "아직 해당 인사이트에 맞는 알림이 없어요",
                    subtitle = "새 알림이 쌓이면 여기서 바로 이유와 내용을 함께 볼 수 있어요.",
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
                eyebrow = "Insight",
                title = result.title,
                subtitle = result.subtitle,
            )
        }
        item {
            SmartSurfaceCard {
                androidx.compose.material3.Text(
                    text = "선택한 인사이트에 연결된 정리 알림 ${result.notifications.size}건을 시간순으로 보여줘요.",
                    textAlign = TextAlign.Start,
                )
            }
        }
        items(result.notifications) { notification ->
            NotificationCard(model = notification, onClick = onNotificationClick)
        }
    }
}
