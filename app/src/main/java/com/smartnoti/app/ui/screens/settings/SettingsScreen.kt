package com.smartnoti.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
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
                    Text("업무 집중", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digest 시간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("12:00 · 18:00 · 21:00", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
