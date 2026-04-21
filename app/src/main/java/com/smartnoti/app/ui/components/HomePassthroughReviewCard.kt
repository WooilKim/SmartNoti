package com.smartnoti.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartnoti.app.ui.theme.BorderSubtle
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SmartNotiTheme

/**
 * Presentation state for [HomePassthroughReviewCard].
 *
 * Kept as a pure-Kotlin value object so it can be unit-tested without Compose.
 *
 * - `isActive = false` → empty-state copy (no notifications waiting review).
 * - `isActive = true`  → active copy surfacing the count.
 *
 * Negative counts are clamped to zero and treated as the empty state.
 */
internal data class HomePassthroughReviewCardState(
    val count: Int,
    val isActive: Boolean,
    val title: String,
    val body: String,
    val actionLabel: String,
) {
    companion object {
        fun from(count: Int): HomePassthroughReviewCardState {
            val safeCount = count.coerceAtLeast(0)
            val active = safeCount > 0
            return HomePassthroughReviewCardState(
                count = safeCount,
                isActive = active,
                title = if (active) {
                    "SmartNoti 가 건드리지 않은 알림 ${safeCount}건"
                } else {
                    "검토 대기 알림 없음"
                },
                body = if (active) {
                    "이 판단이 맞는지 검토하고 필요하면 규칙으로 만들 수 있어요"
                } else {
                    "SmartNoti 가 건드리지 않은 알림이 들어오면 여기에서 재분류할 수 있어요"
                },
                actionLabel = "검토하기",
            )
        }
    }
}

/**
 * Home-screen card surfacing passthrough (PRIORITY status) notifications as a
 * review queue. Replaces the standalone Priority tab — the card is the only
 * entry point to the review screen.
 *
 * @param count Number of passthrough notifications awaiting review.
 * @param onReviewClick Called when the user taps the card to open the review screen.
 *        Safe to invoke regardless of count — callers decide whether to open an
 *        empty state or a populated list.
 */
@Composable
fun HomePassthroughReviewCard(
    count: Int,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = HomePassthroughReviewCardState.from(count)
    val containerColor = if (state.isActive) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onReviewClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isActive) {
                    PassthroughReviewBadge(count = state.count)
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun PassthroughReviewBadge(count: Int) {
    Box(
        modifier = Modifier
            .background(PriorityContainer, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "검토 대기 ${count}",
            style = MaterialTheme.typography.labelSmall,
            color = PriorityOnContainer,
        )
    }
}

@Preview(name = "HomePassthroughReviewCard — empty", showBackground = true)
@Composable
private fun HomePassthroughReviewCardEmptyPreview() {
    SmartNotiTheme {
        HomePassthroughReviewCard(count = 0, onReviewClick = {})
    }
}

@Preview(name = "HomePassthroughReviewCard — active", showBackground = true)
@Composable
private fun HomePassthroughReviewCardActivePreview() {
    SmartNotiTheme {
        HomePassthroughReviewCard(count = 3, onReviewClick = {})
    }
}
