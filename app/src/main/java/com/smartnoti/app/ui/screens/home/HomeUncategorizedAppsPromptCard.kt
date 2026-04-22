package com.smartnoti.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.usecase.UncategorizedAppsDetection
import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.components.SmartSurfaceCard
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.Navy800

/**
 * Home 상단 "새 앱 분류 유도 카드" — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 10.
 *
 * The card appears when [UncategorizedAppsDetection.Prompt] is produced by
 * [com.smartnoti.app.domain.usecase.UncategorizedAppsDetector]. It sits at the
 * very top of the Home list (above the StatPill hero card) so the user sees
 * the nudge before the operational summary.
 *
 * - "분류 만들기" → navigate into the Categories wizard / editor.
 * - "나중에" → write a 24-hour snooze onto SettingsRepository; the detector
 *   will suppress the card until the deadline elapses.
 */
@Composable
fun HomeUncategorizedAppsPromptCard(
    prompt: UncategorizedAppsDetection.Prompt,
    onCreateCategory: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = Navy800,
        borderColor = BorderSubtle,
    ) {
        ContextBadge(
            label = "분류 제안",
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            contentColor = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "새 앱 ${prompt.uncoveredCount}개가 알림을 보내고 있어요",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = buildBodyText(prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onCreateCategory,
                modifier = Modifier.weight(1f),
            ) {
                Text("분류 만들기")
            }
            TextButton(
                onClick = onSnooze,
                modifier = Modifier.weight(1f),
            ) {
                Text("나중에")
            }
        }
    }
}

private fun buildBodyText(prompt: UncategorizedAppsDetection.Prompt): String {
    val labels = prompt.sampleAppLabels
    return when {
        labels.isEmpty() -> "분류하시겠어요?"
        prompt.uncoveredCount > labels.size ->
            "${labels.joinToString(", ")} 외 ${prompt.uncoveredCount - labels.size}개 앱을 분류하시겠어요?"
        else -> "${labels.joinToString(", ")} 앱을 분류하시겠어요?"
    }
}
