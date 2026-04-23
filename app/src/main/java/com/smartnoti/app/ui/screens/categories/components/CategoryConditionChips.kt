package com.smartnoti.app.ui.screens.categories.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.IgnoreContainer
import com.smartnoti.app.ui.theme.IgnoreOnContainer
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

/**
 * Renders the inline "조건: X 또는 Y → action" chip row introduced by plan
 * `docs/plans/2026-04-24-categories-condition-chips.md` Task 2.
 *
 * Visual hierarchy follows `.claude/rules/ui-improvement.md`:
 * - Subtle pill chips for each condition token (surfaceVariant) so they
 *   stay quieter than the action chip.
 * - Action chip uses the same colour family as `CategoryActionBadge` but at
 *   an even lower-saturation tint to avoid duplicating the badge's weight
 *   when the two render together on the same row.
 * - Connector glyphs (`또는`, `→`) render as inline text labels with
 *   `onSurfaceVariant`, not chips, so the eye reads condition → action as
 *   one sentence rather than four shouting pills.
 *
 * Card mode (small `maxInline`) and editor mode (`Int.MAX_VALUE`) share the
 * same composable — the formatter handles truncation, the composable just
 * renders whatever it's handed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryConditionChips(
    text: CategoryConditionChipText,
    action: CategoryAction,
    modifier: Modifier = Modifier,
) {
    val plain = text.toPlainString()
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = plain },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ConnectorText(text.prefix)
        text.tokens.forEachIndexed { index, token ->
            if (index > 0) ConnectorText("또는")
            ConditionChip(token)
        }
        if (text.overflowLabel != null) {
            ConnectorText(text.overflowLabel)
        }
        ConnectorText("→")
        ActionChip(label = text.actionLabel, action = action)
    }
}

@Composable
private fun ConditionChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ActionChip(label: String, action: CategoryAction) {
    val (container, content) = actionTint(action)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ConnectorText(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}

private fun actionTint(action: CategoryAction): Pair<Color, Color> = when (action) {
    CategoryAction.PRIORITY -> PriorityContainer to PriorityOnContainer
    CategoryAction.DIGEST -> DigestContainer to DigestOnContainer
    CategoryAction.SILENT -> SilentContainer to SilentOnContainer
    CategoryAction.IGNORE -> IgnoreContainer to IgnoreOnContainer
}

/**
 * Convenience wrapper used by call sites that already have rules + action +
 * mode but not a pre-built [CategoryConditionChipText]. Keeps the screens
 * thin and routes everything through the same formatter.
 */
@Composable
fun CategoryConditionChips(
    rules: List<com.smartnoti.app.domain.model.RuleUiModel>,
    action: CategoryAction,
    maxInline: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val text = CategoryConditionChipFormatter.format(
        rules = rules,
        action = action,
        maxInline = maxInline,
    )
    CategoryConditionChips(
        text = text,
        action = action,
        modifier = modifier.padding(contentPadding),
    )
}
