package com.smartnoti.app.ui.screens.categories

import androidx.compose.runtime.Composable
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.ui.components.ContextBadge
import com.smartnoti.app.ui.theme.DigestContainer
import com.smartnoti.app.ui.theme.DigestOnContainer
import com.smartnoti.app.ui.theme.IgnoreContainer
import com.smartnoti.app.ui.theme.IgnoreOnContainer
import com.smartnoti.app.ui.theme.PriorityContainer
import com.smartnoti.app.ui.theme.PriorityOnContainer
import com.smartnoti.app.ui.theme.SilentContainer
import com.smartnoti.app.ui.theme.SilentOnContainer

/**
 * Badge rendering for a Category's action.
 *
 * Reuses the existing StatusBadge colour palette (Priority/Digest/Silent/
 * Ignore) so Category rows match the notification status chips users
 * already see elsewhere. IGNORE keeps its neutral-gray tokens as called
 * out by the caller and by plan
 * `2026-04-21-ignore-tier-fourth-decision` Task 6.
 */
@Composable
fun CategoryActionBadge(action: CategoryAction) {
    val (label, bg, fg) = when (action) {
        CategoryAction.PRIORITY -> Triple("즉시 전달", PriorityContainer, PriorityOnContainer)
        CategoryAction.DIGEST -> Triple("Digest", DigestContainer, DigestOnContainer)
        CategoryAction.SILENT -> Triple("조용히 정리", SilentContainer, SilentOnContainer)
        CategoryAction.IGNORE -> Triple("무시됨", IgnoreContainer, IgnoreOnContainer)
    }
    ContextBadge(
        label = label,
        containerColor = bg,
        contentColor = fg,
    )
}

/**
 * Korean-friendly label for the action dropdown used by the Task 9 editor.
 */
fun CategoryAction.displayLabel(): String = when (this) {
    CategoryAction.PRIORITY -> "즉시 전달 (PRIORITY)"
    CategoryAction.DIGEST -> "Digest"
    CategoryAction.SILENT -> "조용히 정리 (SILENT)"
    CategoryAction.IGNORE -> "무시됨 (IGNORE)"
}
