package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.CategoryAction
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.ApplyCategoryActionToNotificationUseCase
import com.smartnoti.app.domain.usecase.AssignNotificationToCategoryUseCase
import com.smartnoti.app.domain.usecase.CategoryEditorPrefill
import com.smartnoti.app.ui.notification.CategoryAssignBottomSheet
import com.smartnoti.app.ui.notification.ChangeCategorySheetState
import com.smartnoti.app.ui.screens.categories.CategoryEditorScreen
import com.smartnoti.app.ui.screens.categories.CategoryEditorTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Detail 화면 상단의 단순한 top bar — 뒤로 가기 + 제목. Refactor
 * `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md` Task 3 에서
 * root 파일에서 cut-and-paste 됨, visibility `private` → `internal` 로 승격.
 */
@Composable
internal fun DetailTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "뒤로 가기",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * "이 알림 분류하기" CTA 카드. `분류 변경` 버튼 탭 시 호출자가 hoisting 한
 * showAssignSheet state 를 true 로 토글한다. Refactor
 * `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md` Task 3 에서
 * root composable 에서 cut-and-paste 됨, 동작 변경 없음.
 */
@Composable
internal fun NotificationDetailReclassifyCta(onOpenAssignSheet: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "이 알림 분류하기",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // Plan `2026-04-22-categories-runtime-wiring-fix.md` Task 2:
            // the four per-action buttons are replaced by a single
            // "분류 변경" CTA. Tap opens [CategoryAssignBottomSheet]
            // which lets the user (a) add this notification's
            // auto-rule to an existing Category or (b) launch the
            // Category editor with a prefilled draft.
            Text(
                "이 알림이 어떤 분류에 속하는지 알려주세요. 다음부터는 분류의 전달 방식이 적용돼요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenAssignSheet,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("분류 변경")
            }
        }
    }
}

/**
 * Reclassify 다이얼로그/시트 호스트. `CategoryAssignBottomSheet` (assign-to-existing
 * + create-new 분기) + `CategoryEditorScreen` (Path B prefill) 두 호출 블록을
 * 묶어 root 가 한 줄로 호출할 수 있게 함. 모든 외부 dependency 는 explicit
 * 파라미터로 받아 sub-fn 자체는 stateless. Refactor
 * `docs/plans/2026-04-27-refactor-notification-detail-screen-split.md` Task 3 에서
 * root composable 에서 cut-and-paste 됨, lambdas 내부 동작
 * (`assignUseCase.assignToExisting` / `applyToCurrentRowUseCase.apply` /
 * `confirmationMessageBuilder.build` / `snackbarHostState.showSnackbar`) 그대로 보존.
 */
@Composable
internal fun NotificationDetailReclassifyHost(
    notification: NotificationUiModel,
    categories: List<Category>,
    rules: List<RuleUiModel>,
    showAssignSheet: Boolean,
    onAssignSheetVisibilityChange: (Boolean) -> Unit,
    pendingPrefill: CategoryEditorPrefill?,
    onPendingPrefillChange: (CategoryEditorPrefill?) -> Unit,
    assignUseCase: AssignNotificationToCategoryUseCase,
    applyToCurrentRowUseCase: ApplyCategoryActionToNotificationUseCase,
    confirmationMessageBuilder: DetailReclassifyConfirmationMessageBuilder,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
) {
    if (showAssignSheet) {
        val sheetState = ChangeCategorySheetState.from(
            notification = notification,
            categories = categories,
        )
        CategoryAssignBottomSheet(
            state = sheetState,
            onDismiss = { onAssignSheetVisibilityChange(false) },
            onAssignToExisting = { categoryId ->
                onAssignSheetVisibilityChange(false)
                // Capture the categories snapshot at tap time so the
                // confirmation copy resolves the destination name even if
                // the categories Flow re-emits before the snackbar fires.
                val snapshot = categories
                scope.launch {
                    assignUseCase.assignToExisting(
                        notification = notification,
                        categoryId = categoryId,
                    )
                    // Plan
                    // `docs/plans/2026-04-26-detail-reclassify-this-row-now.md`
                    // Task 4: also rewrite the current row's status to the
                    // destination Category's action so the list view
                    // reflects the user's tap immediately. `snapshot` is
                    // captured at tap time; if the Category has since been
                    // deleted (race) we fall back to snackbar-only.
                    val destinationAction = snapshot.firstOrNull { it.id == categoryId }?.action
                    if (destinationAction != null) {
                        applyToCurrentRowUseCase.apply(notification, destinationAction)
                    }
                    val message = confirmationMessageBuilder.build(
                        outcome = DetailReclassifyOutcome.AssignedExisting(categoryId),
                        categories = snapshot,
                    )
                    if (message != null) {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            },
            onCreateNewCategory = {
                onAssignSheetVisibilityChange(false)
                // Derive the notification's currently-owning Category action
                // (if any) so the editor's default action is dynamic-opposite.
                val currentAction = resolveOwningCategoryAction(
                    notification = notification,
                    categories = categories,
                    rules = rules,
                )
                onPendingPrefillChange(
                    AssignNotificationToCategoryUseCase.buildPrefillForNewCategory(
                        notification = notification,
                        currentCategoryAction = currentAction,
                    ),
                )
            },
        )
    }

    val prefill = pendingPrefill
    if (prefill != null) {
        // Reuse the existing CategoryEditorScreen (AlertDialog) with prefill
        // seeded. Save persists the pendingRule alongside the Category.
        CategoryEditorScreen(
            target = CategoryEditorTarget.New,
            categories = categories,
            rules = rules,
            capturedApps = emptyList(),
            onDismiss = { onPendingPrefillChange(null) },
            onSaved = { savedCategory ->
                onPendingPrefillChange(null)
                // Plan
                // `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md`
                // Task 2: render the Path B "새 분류 만들었어요" confirmation
                // using the just-persisted Category's name (not the
                // categories Flow snapshot — it may not have re-emitted
                // yet when this callback fires).
                val message = confirmationMessageBuilder.build(
                    outcome = DetailReclassifyOutcome.CreatedNew(
                        categoryName = savedCategory.name,
                    ),
                    categories = categories,
                )
                scope.launch {
                    // Plan
                    // `docs/plans/2026-04-26-detail-reclassify-this-row-now.md`
                    // Task 5: also rewrite the current row's status to the
                    // newly-created Category's action so list views
                    // reflect the user's choice immediately. `savedCategory`
                    // is the just-persisted entity from the editor save
                    // path so its action is authoritative even before
                    // the categories Flow re-emits.
                    applyToCurrentRowUseCase.apply(notification, savedCategory.action)
                    if (message != null) {
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                }
            },
            onDelete = { /* no-op in New flow */ },
            prefill = prefill,
        )
    }
}

/**
 * Resolve the Category that currently "owns" [notification] so the
 * "새 분류 만들기" default action can be dynamic-opposite of it.
 *
 * Matches the classifier's Category selection logic at a high level: find
 * any Category whose `ruleIds` reference a Rule that matches the
 * notification's sender/app. Returns null when no Category owns the
 * notification today (classifier fell through to a heuristic default) —
 * the use case then defaults to PRIORITY.
 */
private fun resolveOwningCategoryAction(
    notification: NotificationUiModel,
    categories: List<Category>,
    rules: List<RuleUiModel>,
): CategoryAction? {
    val matchedRuleIds = rules
        .filter { rule -> rule.enabled && ruleMatches(rule, notification) }
        .map { it.id }
        .toSet()
    if (matchedRuleIds.isEmpty()) return null
    val owning = categories.firstOrNull { category ->
        category.ruleIds.any { it in matchedRuleIds }
    }
    return owning?.action
}

private fun ruleMatches(
    rule: RuleUiModel,
    notification: NotificationUiModel,
): Boolean {
    return when (rule.type) {
        RuleTypeUi.PERSON ->
            !notification.sender.isNullOrBlank() &&
                notification.sender.equals(rule.matchValue, ignoreCase = true)
        RuleTypeUi.APP ->
            notification.packageName.equals(rule.matchValue, ignoreCase = true)
        RuleTypeUi.KEYWORD -> {
            val content = listOf(notification.title, notification.body).joinToString(" ")
            rule.matchValue
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .any { content.contains(it, ignoreCase = true) }
        }
        // Plan `2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
        // Task 2. Mirror the classifier's SENDER branch so the "분류 변경"
        // sheet's dynamic-opposite default action recognises a SENDER rule
        // as the owning matcher (not just the legacy four).
        RuleTypeUi.SENDER ->
            rule.matchValue.isNotBlank() &&
                notification.title.contains(rule.matchValue, ignoreCase = true)
        RuleTypeUi.SCHEDULE,
        RuleTypeUi.REPEAT_BUNDLE -> false
    }
}
