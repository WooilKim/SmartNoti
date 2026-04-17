package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.ui.components.ScreenHeader
import com.smartnoti.app.ui.components.SectionLabel
import com.smartnoti.app.ui.components.SmartSurfaceCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val repository = remember(context) { SettingsRepository.getInstance(context) }
    val settings by repository.observeSettings().collectAsState(
        initial = com.smartnoti.app.data.settings.SmartNotiSettings()
    )
    val scope = remember { CoroutineScope(Dispatchers.IO) }

    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenHeader(
                eyebrow = "Settings",
                title = "설정",
                subtitle = "알림 분류 동작을 운영 도구처럼 명확하게 조정할 수 있어요.",
            )
        }
        item {
            SectionLabel(
                title = "현재 모드",
                subtitle = "SmartNoti가 지금 어떤 기준으로 알림을 다루는지 보여줘요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (settings.quietHoursEnabled) "조용한 시간 자동 적용" else "항상 즉시 분류",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (settings.quietHoursEnabled) {
                        "지정한 시간대에는 덜 급한 알림을 정리함 중심으로 다뤄요."
                    } else {
                        "모든 시간대에 동일한 기준으로 바로 분류해요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionLabel(
                title = "Quiet Hours",
                subtitle = "자동 완화 시간대를 확인하고 즉시 켜거나 끌 수 있어요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${settings.quietHoursStartHour}:00 ~ ${settings.quietHoursEndHour}:00",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "조용한 시간 사용",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = settings.quietHoursEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                repository.setQuietHoursEnabled(enabled)
                            }
                        }
                    )
                }
            }
        }
        item {
            SectionLabel(
                title = "Digest 시간",
                subtitle = "덜 중요한 알림을 묶어 보여줄 정리 시점을 확인해요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    settings.digestHours.joinToString(" · ") { "$it:00" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "반복되거나 덜 급한 알림은 이 시점에 맞춰 Digest로 다시 확인할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionLabel(
                title = "알림 접근 설정",
                subtitle = "실시간 반영을 위해 필요한 시스템 권한 위치를 안내해요.",
            )
        }
        item {
            SmartSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "시스템 설정에서 SmartNoti 알림 접근을 켜면 들어오는 알림을 홈 화면에 반영할 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "경로: 설정 → 알림 → 기기 및 앱 알림 → 알림 읽기",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
