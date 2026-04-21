package com.smartnoti.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.usecase.NotificationDetailRuleReference

/**
 * "적용된 규칙" chips for the Detail screen (plan
 * `rules-ux-v2-inbox-restructure` Phase B Task 3).
 *
 * Each chip represents a user rule that fired during classification. Chips are
 * tinted with the primary accent + a thin outline to distinguish them from the
 * grey [ReasonChipRow] (which surfaces classifier-internal signals). Tapping a
 * chip invokes [onHitClick] — callers wire this to `Routes.Rules.create(id)`
 * so the Rules tab opens with the matching row scrolled-to and flashed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleHitChipRow(
    hits: List<NotificationDetailRuleReference>,
    onHitClick: (NotificationDetailRuleReference) -> Unit,
) {
    if (hits.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        hits.forEach { hit ->
            Text(
                text = hit.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .clickable { onHitClick(hit) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
