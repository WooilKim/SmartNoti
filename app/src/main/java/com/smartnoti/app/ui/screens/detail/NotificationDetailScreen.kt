package com.smartnoti.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.RuleTypeUi
import com.smartnoti.app.domain.model.RuleUiModel
import com.smartnoti.app.domain.usecase.AcceptSenderSuggestionUseCase
import com.smartnoti.app.domain.usecase.ApplyCategoryActionToNotificationUseCase
import com.smartnoti.app.domain.usecase.AssignNotificationToCategoryUseCase
import com.smartnoti.app.domain.usecase.CategoryActionToNotificationStatusMapper
import com.smartnoti.app.domain.usecase.CategoryEditorPrefill
import com.smartnoti.app.domain.usecase.NotificationDetailDeliveryProfileSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailOnboardingRecommendationSummaryBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailReasonSectionBuilder
import com.smartnoti.app.domain.usecase.NotificationDetailSourceSuppressionSummaryBuilder
import com.smartnoti.app.domain.usecase.QuietHoursExplainerBuilder
import com.smartnoti.app.domain.usecase.shouldShowDetailCard
import com.smartnoti.app.ui.components.EmptyState
import com.smartnoti.app.ui.components.StatusBadge
import kotlinx.coroutines.launch
import androidx.compose.material3.SnackbarDuration

@Composable
fun NotificationDetailScreen(
    contentPadding: PaddingValues,
    notificationId: String,
    onBack: () -> Unit,
    onRuleClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val rulesRepository = remember(context) { RulesRepository.getInstance(context) }
    val categoriesRepository = remember(context) { CategoriesRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val deliveryProfileSummaryBuilder = remember { NotificationDetailDeliveryProfileSummaryBuilder() }
    val onboardingRecommendationSummaryBuilder = remember { NotificationDetailOnboardingRecommendationSummaryBuilder() }
    val sourceSuppressionSummaryBuilder = remember { NotificationDetailSourceSuppressionSummaryBuilder() }
    val reasonSectionBuilder = remember { NotificationDetailReasonSectionBuilder() }
    val quietHoursExplainerBuilder = remember { QuietHoursExplainerBuilder() }
    val scope = rememberCoroutineScope()
    // Plan `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md` Task 2:
    // single SnackbarHostState shared by the two reclassify call sites so
    // both Path A (assign-to-existing) and Path B (created-new) can render a
    // brief confirmation toast after the assign sheet / editor dismisses.
    val snackbarHostState = remember { SnackbarHostState() }
    val confirmationMessageBuilder = remember { DetailReclassifyConfirmationMessageBuilder() }
    val liveNotification by repository.observeNotification(notificationId).collectAsState(initial = null)
    val notification = liveNotification
    val rules by rulesRepository.observeRules().collectAsState(initial = emptyList<RuleUiModel>())
    val categories by categoriesRepository.observeCategories().collectAsState(initial = emptyList<Category>())
    // Plan `docs/plans/2026-04-26-quiet-hours-explainer-copy.md` Task 3:
    // observe settings so the explainer can interpolate the user's current
    // quiet-hours window. First emission may briefly fall back to defaults
    // (23~7) — same race other Detail builders accept.
    val settings by settingsRepository.observeSettings().collectAsState(initial = SmartNotiSettings())
    // Plan `2026-04-22-categories-runtime-wiring-fix.md` Task 2: the four
    // action buttons are replaced with a single "분류 변경" CTA that opens
    // a ModalBottomSheet. Local state governs whether the sheet or the
    // editor dialog is up; dismissing either returns to the Detail card.
    var showAssignSheet by remember { mutableStateOf(false) }
    var pendingPrefill by remember { mutableStateOf<CategoryEditorPrefill?>(null) }
    val assignUseCase = remember(rulesRepository, categoriesRepository) {
        AssignNotificationToCategoryUseCase(
            ports = object : AssignNotificationToCategoryUseCase.Ports {
                override suspend fun upsertRule(rule: RuleUiModel) {
                    rulesRepository.upsertRule(rule)
                }
                override suspend fun appendRuleIdToCategory(
                    categoryId: String,
                    ruleId: String,
                ) {
                    categoriesRepository.appendRuleIdToCategory(categoryId, ruleId)
                }
            },
        )
    }
    // Plan `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
    // Task 7. Use case wired against the same two repositories as
    // [assignUseCase] — separate Ports interface so the SenderRule path can
    // be unit-tested in isolation without dragging in the AssignNotification
    // contract. Both writes (rule upsert + category attach) happen inside
    // [AcceptSenderSuggestionUseCase.accept] sequentially.
    val acceptSenderSuggestionUseCase = remember(rulesRepository, categoriesRepository) {
        AcceptSenderSuggestionUseCase(
            ports = object : AcceptSenderSuggestionUseCase.Ports {
                override suspend fun upsertRule(rule: RuleUiModel) {
                    rulesRepository.upsertRule(rule)
                }
                override suspend fun appendRuleIdToCategory(
                    categoryId: String,
                    ruleId: String,
                ) {
                    categoriesRepository.appendRuleIdToCategory(categoryId, ruleId)
                }
            },
        )
    }
    // Plan `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
    // Task 7. Per-Detail-entry dismissable state — when the user taps
    // [SenderRuleSuggestionCardSpec.LABEL_DISMISS] the card is hidden for the
    // remainder of this Detail mount. v1 keeps it in-memory only; per-title
    // sticky-dismiss is an explicit v2 carve-out (Plan §Risks).
    var senderSuggestionDismissedThisEntry by remember { mutableStateOf(false) }
    // Plan `docs/plans/2026-04-26-detail-reclassify-this-row-now.md` Tasks 4–5:
    // after the Path A (existing Category) or Path B (newly-created Category)
    // write completes, also rewrite the currently-displayed row's
    // `status` + `reasonTags` so list views reflect the user's choice
    // immediately. The legacy 4-button layout did this synchronously; the
    // 2026-04-22 redesign dropped it and snackbar copy alone made the
    // behavior feel ambiguous.
    val applyToCurrentRowUseCase = remember(repository) {
        ApplyCategoryActionToNotificationUseCase(
            ports = object : ApplyCategoryActionToNotificationUseCase.Ports {
                override suspend fun updateNotification(notification: com.smartnoti.app.domain.model.NotificationUiModel) {
                    repository.updateNotification(notification)
                }
            },
            mapper = CategoryActionToNotificationStatusMapper(),
        )
    }
    val reasonSections = remember(notification, rules) {
        notification?.let { reasonSectionBuilder.build(it, rules) }
    }
    // Plan `docs/plans/2026-04-26-quiet-hours-explainer-copy.md` Task 3:
    // synthesize the plain-language quiet-hours explainer from the live
    // notification + current settings window. Null when the quiet-hours
    // branch was not the decisive classifier signal.
    val quietHoursExplainer = remember(
        notification?.reasonTags,
        notification?.status,
        settings.quietHoursStartHour,
        settings.quietHoursEndHour,
    ) {
        notification?.let {
            quietHoursExplainerBuilder.build(
                reasonTags = it.reasonTags,
                status = it.status,
                startHour = settings.quietHoursStartHour,
                endHour = settings.quietHoursEndHour,
            )
        }
    }
    val deliveryProfileSummary = remember(notification) {
        notification?.let(deliveryProfileSummaryBuilder::build)
    }
    val onboardingRecommendationSummary = remember(notification) {
        notification?.let(onboardingRecommendationSummaryBuilder::build)
    }
    val sourceSuppressionSummary = remember(notification) {
        notification?.takeIf {
            it.sourceSuppressionState.shouldShowDetailCard(it.replacementNotificationIssued)
        }?.let {
            sourceSuppressionSummaryBuilder.build(
                suppressionState = it.sourceSuppressionState,
                replacementNotificationIssued = it.replacementNotificationIssued,
            )
        }
    }
    // Plan `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
    // Task 7. Pre-compute the existing-SENDER-rule check outside the spec so
    // [SenderRuleSuggestionCardSpec.shouldShow] stays pure. Re-evaluates
    // whenever the rules Flow re-emits (e.g. after the user taps accept and
    // the rule lands in storage). Title comparison is the same `contains` +
    // `ignoreCase` contract the classifier uses, so the suggestion suppresses
    // exactly when classification would already route via SENDER.
    val hasExistingSenderRuleForTitle = remember(rules, notification?.title) {
        val title = notification?.title.orEmpty()
        if (title.isBlank()) false else rules.any { rule ->
            rule.type == RuleTypeUi.SENDER &&
                rule.matchValue.isNotBlank() &&
                title.contains(rule.matchValue, ignoreCase = true)
        }
    }
    val showSenderSuggestion = remember(
        notification?.title,
        hasExistingSenderRuleForTitle,
        settings.senderSuggestionEnabled,
        senderSuggestionDismissedThisEntry,
    ) {
        !senderSuggestionDismissedThisEntry &&
            SenderRuleSuggestionCardSpec.shouldShow(
                title = notification?.title.orEmpty(),
                hasExistingSenderRule = hasExistingSenderRuleForTitle,
                settingToggleOn = settings.senderSuggestionEnabled,
            )
    }

    if (notification == null) {
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DetailTopBar(title = "알림 상세", onBack = onBack)
            }
            item {
                EmptyState(
                    title = "알림을 찾을 수 없어요",
                    subtitle = "이미 삭제됐거나 아직 저장되지 않은 실제 알림일 수 있어요",
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailTopBar(title = "알림 상세", onBack = onBack)
        }
        // SnackbarHost overlay below — Plan
        // `docs/plans/2026-04-24-detail-reclassify-confirm-toast.md` Task 2.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        notification.appName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        notification.sender ?: notification.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        notification.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusBadge(notification.status)
                }
            }
        }
        item {
            NotificationDetailReasonCard(
                sections = reasonSections,
                quietHoursExplainer = quietHoursExplainer,
                onRuleClick = onRuleClick,
            )
        }
        item {
            NotificationDetailOnboardingRecommendationCard(
                summary = onboardingRecommendationSummary,
            )
        }
        item {
            NotificationDetailDeliveryProfileCard(summary = deliveryProfileSummary)
        }
        item {
            NotificationDetailSourceSuppressionCard(summary = sourceSuppressionSummary)
        }
        item {
            NotificationDetailArchivedCompletionCard(
                notification = notification,
                repository = repository,
                scope = scope,
            )
        }
        // Plan
        // `docs/plans/2026-04-28-fix-issue-526-sender-aware-classification-rules.md`
        // Task 7. SENDER one-tap suggestion sits directly above the
        // "분류 변경" CTA so the user can either (a) elevate this exact
        // sender with one tap (Accept) or (b) fall through to the general
        // reclassify sheet (existing CTA below). Visibility is gated by
        // [showSenderSuggestion] — `item { }` is only emitted when all five
        // [SenderRuleSuggestionCardSpec.shouldShow] guards pass and the
        // user has not already tapped dismiss this Detail mount.
        if (showSenderSuggestion) {
            item(key = "sender-suggestion-${notification.id}") {
                SenderRuleSuggestionCard(
                    title = notification.title,
                    onAccept = {
                        // Capture the categories snapshot at tap time so a
                        // concurrent edit cannot race the destination
                        // resolution (mirrors the Path A snapshot pattern in
                        // [NotificationDetailReclassifyHost.onAssignToExisting]).
                        val snapshot = categories
                        scope.launch {
                            val outcome = acceptSenderSuggestionUseCase.accept(
                                title = notification.title,
                                categories = snapshot,
                            )
                            val message = when (outcome) {
                                is AcceptSenderSuggestionUseCase.Outcome.Attached ->
                                    "같은 발신자의 다음 알림부터 중요로 분류돼요"
                                AcceptSenderSuggestionUseCase.Outcome.NoPriorityCategory ->
                                    "중요 분류가 없어 발신자 규칙을 만들 수 없어요. " +
                                        "분류 변경에서 새 분류를 만들어 주세요."
                                AcceptSenderSuggestionUseCase.Outcome.BlankTitle -> null
                            }
                            // Hide the card immediately — the next
                            // recomposition would suppress it anyway via
                            // `hasExistingSenderRuleForTitle`, but flipping
                            // the dismiss flag avoids a one-frame flash if
                            // the rules Flow has not re-emitted yet.
                            senderSuggestionDismissedThisEntry = true
                            if (message != null) {
                                snackbarHostState.showSnackbar(
                                    message = message,
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                    onDismiss = {
                        senderSuggestionDismissedThisEntry = true
                    },
                )
            }
        }
        item {
            NotificationDetailReclassifyCta(onOpenAssignSheet = { showAssignSheet = true })
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Respect the parent Scaffold's bottom inset (BottomNav)
                // so the snackbar lifts above the navigation bar instead
                // of hiding behind it.
                .padding(contentPadding)
                .padding(16.dp),
        )
    }

    NotificationDetailReclassifyHost(
        notification = notification,
        categories = categories,
        rules = rules,
        showAssignSheet = showAssignSheet,
        onAssignSheetVisibilityChange = { showAssignSheet = it },
        pendingPrefill = pendingPrefill,
        onPendingPrefillChange = { pendingPrefill = it },
        assignUseCase = assignUseCase,
        applyToCurrentRowUseCase = applyToCurrentRowUseCase,
        confirmationMessageBuilder = confirmationMessageBuilder,
        snackbarHostState = snackbarHostState,
        scope = scope,
    )
}

