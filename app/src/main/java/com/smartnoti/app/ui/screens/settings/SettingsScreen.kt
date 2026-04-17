package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.settings.SettingsRepository
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
            Text("설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("현재 모드", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (settings.quietHoursEnabled) "조용한 시간 자동 적용" else "항상 즉시 분류", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quiet Hours", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${settings.quietHoursStartHour}:00 ~ ${settings.quietHoursEndHour}:00", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("조용한 시간 사용", style = MaterialTheme.typography.bodyMedium)
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
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digest 시간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(settings.digestHours.joinToString(" · ") { "$it:00" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("알림 접근 설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("시스템 설정에서 SmartNoti 알림 접근을 켜면 들어오는 알림을 홈 화면에 반영할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    Text("경로: 설정 → 알림 → 기기 및 앱 알림 → 알림 읽기", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
