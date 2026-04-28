package com.smartnoti.app.ui.screens.inbox

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartnoti.app.data.local.HighVolumeAppCandidate
import com.smartnoti.app.data.local.HighVolumeAppDetector
import com.smartnoti.app.data.local.NotificationRepository
import com.smartnoti.app.data.local.TrayOrphanCleanupRunner
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.data.local.toHiddenGroups
import com.smartnoti.app.domain.model.InboxSortMode
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.notification.AndroidAppIconSource
import com.smartnoti.app.notification.AppIconResolver
import com.smartnoti.app.ui.screens.digest.DigestScreen
import com.smartnoti.app.ui.screens.digest.DigestScreenMode
import com.smartnoti.app.ui.screens.hidden.HiddenNotificationsScreen
import com.smartnoti.app.ui.screens.hidden.HiddenScreenMode
import com.smartnoti.app.ui.theme.BorderSubtle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 정리함 (Inbox) screen — plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P3 Task 11.
 *
 * One screen, three sub-tabs:
 * - **Digest** groups (reuses [DigestScreen]'s content)
 * - **보관 중** ARCHIVED SILENT (reuses [HiddenNotificationsScreen] ARCHIVED
 *   tab content)
 * - **처리됨** PROCESSED SILENT (reuses HiddenNotificationsScreen PROCESSED)
 *
 * The screen avoids re-implementing list/state logic by delegating each
 * sub-tab to the existing screen composable. Header and sub-tab segment live
 * here; the nested screens supply their own bodies and bulk actions.
 *
 * Deep links into the legacy Digest / Hidden routes still work — AppNavHost
 * keeps them registered so tray group-summary contentIntents and onboarding
 * flows don't break mid-migration.
 */
@Composable
fun InboxScreen(
    contentPadding: PaddingValues,
    onNotificationClick: (String) -> Unit,
    onBack: () -> Unit,
    onOpenSuppressedAppsSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { NotificationRepository.getInstance(context) }
    val settingsRepository = remember(context) { SettingsRepository.getInstance(context) }
    val settings by settingsRepository.observeSettings()
        .collectAsStateWithLifecycle(initialValue = SmartNotiSettings())
    // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 3.
    // Resolve the persisted sort mode once per recomposition. Falls back to
    // RECENT if the on-disk value drifts to an unknown enum name (defense
    // against a future enum rename — the migration in `applyPendingMigrations`
    // and the `setInboxSortMode` setter both write valid `name` only, so this
    // branch is unreachable today).
    val sortMode = remember(settings.inboxSortMode) {
        runCatching { InboxSortMode.valueOf(settings.inboxSortMode) }
            .getOrDefault(InboxSortMode.RECENT)
    }
    val filteredFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeAllFiltered(settings.hidePersistentNotifications)
    }
    val filteredNotifications by filteredFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val digestGroupsFlow = remember(repository, settings.hidePersistentNotifications) {
        repository.observeDigestGroupsFiltered(settings.hidePersistentNotifications)
    }
    val digestGroups by digestGroupsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedCount = remember(filteredNotifications) {
        filteredNotifications
            .toHiddenGroups(
                hidePersistentNotifications = false,
                silentModeFilter = SilentMode.ARCHIVED,
            )
            .sumOf { it.count }
    }
    val processedCount = remember(filteredNotifications) {
        filteredNotifications
            .toHiddenGroups(
                hidePersistentNotifications = false,
                silentModeFilter = SilentMode.PROCESSED,
            )
            .sumOf { it.count }
    }
    val digestCount = remember(digestGroups) { digestGroups.sumOf { it.count } }

    var selectedTab by rememberSaveable { mutableStateOf(InboxTab.Digest) }

    // Plan `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
    // Task 7. High-volume-app suggestion state. Card lives ABOVE the
    // DigestScreen body (Column nesting outside the LazyColumn) so it does not
    // re-arrange items on dismiss — the LazyColumn's scroll position is
    // preserved naturally because the Column does not own a LazyListState.
    //
    // The detector + cleanup runner are constructed once per composition and
    // memoized against the singletons they wrap. AppIconResolver is built on
    // demand (PackageManager pulled from LocalContext) and lasts the lifetime
    // of the InboxScreen — bounded LRU prevents bitmap pile-up.
    val detector = remember(repository) { repository.highVolumeAppDetector() }
    val cleanupRunner = remember(context) { TrayOrphanCleanupRunner.create(context) }
    val iconResolver = remember(context) {
        AppIconResolver(AndroidAppIconSource(context.applicationContext.packageManager))
    }
    var suggestion by remember { mutableStateOf<HighVolumeAppCandidate?>(null) }
    var suggestionIcon by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Re-detect on settings change (suppress / dismiss / snooze sets all gate
    // detector input) and on tab change (only render on Digest sub-tab — the
    // detector still runs to keep the surface consistent across tab toggles).
    LaunchedEffect(
        detector,
        settings.suppressedSourceApps,
        settings.suppressedSourceAppsExcluded,
        settings.suggestedSuppressionDismissed,
        settings.suggestedSuppressionSnoozeUntil,
    ) {
        val nowMillis = System.currentTimeMillis()
        // Filter out snoozed entries whose expiry is still in the future. The
        // detector returns ranked candidates; we pick the first one not under
        // active snooze.
        val candidates = withContext(Dispatchers.IO) {
            detector.detect(
                avgPerDayThreshold = 10,
                windowDays = 7,
                currentSuppressedSourceApps = settings.suppressedSourceApps,
                currentSuggestedSuppressionDismissed = settings.suggestedSuppressionDismissed,
                currentSuppressedSourceAppsExcluded = settings.suppressedSourceAppsExcluded,
            )
        }
        val activeSnoozes = settings.suggestedSuppressionSnoozeUntil
        val pick = candidates.firstOrNull { c ->
            (activeSnoozes[c.packageName] ?: 0L) <= nowMillis
        }
        suggestion = pick
        suggestionIcon = pick?.let { withContext(Dispatchers.IO) { iconResolver.resolve(it.packageName) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // F2 of `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`:
            // header chrome (eyebrow + 1-line title + 2-line subtitle + dedicated
            // sort row + tab row) used to consume ~25% of first screen, pushing
            // the first group card below the fold. The trim:
            // - Drop the subtitle entirely on Inbox specifically — eyebrow +
            //   title already establish context for a returning user; the
            //   docstring-style subtitle only paid an explanation tax.
            // - Inline the `InboxSortDropdown` into the title row's trailing
            //   slot (right-aligned) so the dedicated 48dp sort row collapses.
            // The `InboxHeaderChromeSpec` object is the single source of truth
            // for this composition; `InboxHeaderChromeContractTest` pins it.
            // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 3 wiring
            // for the dropdown setter is preserved — only its location moved.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = InboxHeaderChromeSpec.eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = InboxHeaderChromeSpec.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                InboxSortDropdown(
                    currentMode = sortMode,
                    onSelect = { mode ->
                        scope.launch { settingsRepository.setInboxSortMode(mode) }
                    },
                )
            }
            InboxTabRow(
                selected = selectedTab,
                digestCount = digestCount,
                archivedCount = archivedCount,
                processedCount = processedCount,
                onSelected = { selectedTab = it },
            )
            // Plan `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
            // Task 7. Inline suggestion card sits BELOW the tab row + ABOVE the
            // DigestScreen body so the user sees it on top of the Digest sub-tab
            // (the only sub-tab that owns explicit-action affordances). Other
            // tabs do not show the card — the user's intent on archived /
            // processed tabs is to triage, not to bundle.
            val activeSuggestion = suggestion
            if (selectedTab == InboxTab.Digest && activeSuggestion != null) {
                InboxSuggestionCard(
                    candidate = activeSuggestion,
                    sourceIcon = suggestionIcon,
                    onAccept = {
                        scope.launch {
                            InboxSuggestionAcceptHandler.accept(
                                candidate = activeSuggestion,
                                callbacks = InboxScreenSuggestionCallbacks(
                                    settingsRepository = settingsRepository,
                                    cleanupRunner = cleanupRunner,
                                    currentSuppressedSourceApps = settings.suppressedSourceApps,
                                    onClear = {
                                        suggestion = null
                                        suggestionIcon = null
                                    },
                                ),
                            )
                        }
                    },
                    onSnooze = {
                        scope.launch {
                            settingsRepository.setSuggestedSuppressionSnoozeUntil(
                                packageName = activeSuggestion.packageName,
                                untilMillis = System.currentTimeMillis() +
                                    InboxSuggestionCardSpec.SNOOZE_DURATION_MILLIS,
                            )
                            suggestion = null
                            suggestionIcon = null
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            settingsRepository.setSuggestedSuppressionDismissed(
                                packageName = activeSuggestion.packageName,
                                dismissed = true,
                            )
                            suggestion = null
                            suggestionIcon = null
                        }
                    },
                )
            }
        }
        val innerPadding = PaddingValues(0.dp)
        when (selectedTab) {
            InboxTab.Digest -> DigestScreen(
                contentPadding = innerPadding,
                onNotificationClick = onNotificationClick,
                // Plan `2026-04-27-digest-empty-state-suppress-opt-in-cta`
                // Task 2: forward the empty-state CTA navigation up to the
                // NavHost so the inbox-unified Digest sub-tab and the legacy
                // `Routes.Digest` deep-link both reach the same Settings
                // destination via identical wiring.
                onOpenSuppressedAppsSettings = onOpenSuppressedAppsSettings,
                // Plan `2026-04-27-inbox-unified-double-header-collapse` Task
                // 3: outer InboxScreen already renders the `정리함 / 알림 정리함`
                // ScreenHeader + sort dropdown + tab row, so embed the
                // DigestScreen body without its own ScreenHeader and summary
                // card to remove the duplicate chrome.
                mode = DigestScreenMode.Embedded,
            )
            // Plan `2026-04-22-inbox-denest-and-home-recent-truncate` Task 2:
            // outer Inbox tab is the single source of truth. Embed the Hidden
            // screen so it skips its own ScreenHeader + ARCHIVED/PROCESSED
            // segment row and renders only the body matching the outer pick.
            //
            // Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 3:
            // forward the resolved [sortMode] into the embed so the inner
            // group-list helper applies the same mode for the user.
            InboxTab.Archived,
            InboxTab.Processed -> HiddenNotificationsScreen(
                contentPadding = innerPadding,
                onNotificationClick = onNotificationClick,
                onBack = onBack,
                mode = InboxToHiddenScreenModeMapper.mapToMode(selectedTab),
                sortMode = sortMode,
            )
        }
    }
}

/** Inbox top-level sub-tabs. Persisted via `rememberSaveable`. */
enum class InboxTab { Digest, Archived, Processed }

@Composable
private fun InboxTabRow(
    selected: InboxTab,
    digestCount: Int,
    archivedCount: Int,
    processedCount: Int,
    onSelected: (InboxTab) -> Unit,
) {
    // Plan `docs/plans/2026-04-28-meta-inbox-organized-feel-overhaul.md`
    // finding **F4** — corner radius / border tokens flow from
    // [InboxCardLanguage] so the tab row stays in lockstep with the card
    // surfaces below it. Tab row picks the smaller [TAB_ROW_CORNER_RADIUS_DP]
    // (12dp) intentionally — segmented controls have their own visual
    // convention and matching the 16dp card radius would read as another
    // stacked card rather than a control.
    Surface(
        shape = RoundedCornerShape(InboxCardLanguage.TAB_ROW_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(InboxCardLanguage.CARD_BORDER_WIDTH_DP.dp, BorderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            InboxTabSegment(
                label = "Digest",
                count = digestCount,
                isSelected = selected == InboxTab.Digest,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(InboxTab.Digest) },
            )
            InboxTabSegment(
                label = "보관 중",
                count = archivedCount,
                isSelected = selected == InboxTab.Archived,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(InboxTab.Archived) },
            )
            InboxTabSegment(
                label = "처리됨",
                count = processedCount,
                isSelected = selected == InboxTab.Processed,
                modifier = Modifier.weight(1f),
                onClick = { onSelected(InboxTab.Processed) },
            )
        }
    }
}

@Composable
private fun InboxTabSegment(
    label: String,
    count: Int,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val labelColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape = shape),
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "$label · ${count}건",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = labelColor,
            )
        }
    }
}

/**
 * Plan `docs/plans/2026-04-28-fix-issue-525-high-volume-app-suggest-suppression.md`
 * Task 7. Production binding of [InboxSuggestionAcceptCallbacks] consumed by
 * [InboxSuggestionAcceptHandler] when the user taps `[예, 묶을게요]`.
 *
 *  - [addToSuppressedSourceApps] does an additive set write — reads the most
 *    recent settings snapshot once at construction (passed through
 *    [currentSuppressedSourceApps]) so we do NOT lose entries already
 *    present. The settings flow then re-emits and the next composition's
 *    LaunchedEffect re-detects.
 *  - [cleanupTrayOrphans] forwards to the legacy `cleanup()` runner today
 *    (the scoped overload is still pending #524 follow-up plan Task 4).
 *    NotBound surfaces verbatim; Cancelled returns the unscoped count which
 *    is good enough for v1 — the next iteration adds the scoped overload.
 *  - The two remaining callbacks ([markSuggestionPermanentlyDismissed],
 *    [snoozeSuggestionUntil]) are wired but the accept handler does not call
 *    them; InboxScreen invokes the matching SettingsRepository setter
 *    directly from the `onSnooze` / `onDismiss` button callbacks.
 */
private class InboxScreenSuggestionCallbacks(
    private val settingsRepository: SettingsRepository,
    private val cleanupRunner: TrayOrphanCleanupRunner,
    private val currentSuppressedSourceApps: Set<String>,
    private val onClear: () -> Unit,
) : InboxSuggestionAcceptCallbacks {
    override suspend fun addToSuppressedSourceApps(packageName: String) {
        if (packageName in currentSuppressedSourceApps) return
        settingsRepository.setSuppressedSourceApps(currentSuppressedSourceApps + packageName)
    }

    override suspend fun cleanupTrayOrphans(targetPackages: Set<String>): InboxSuggestionCleanupOutcome {
        // v1: the scoped overload of `TrayOrphanCleanupRunner.cleanup(Set)` is
        // a follow-up plan task. Until then we trigger the unscoped cleanup —
        // it walks the tray once and cancels every orphan, which is still
        // strictly more useful than doing nothing. Worst case it cancels
        // orphans for OTHER packages that the user already accepted in a
        // previous tap; that is the desirable end state anyway.
        val result = withContext(Dispatchers.IO) { cleanupRunner.cleanup() }
        return if (result.notBound) {
            InboxSuggestionCleanupOutcome.NotBound
        } else {
            InboxSuggestionCleanupOutcome.Cancelled(cancelledCount = result.cancelledCount)
        }
    }

    override suspend fun dismissCard() {
        onClear()
    }

    override suspend fun markSuggestionPermanentlyDismissed(packageName: String) {
        settingsRepository.setSuggestedSuppressionDismissed(packageName, dismissed = true)
    }

    override suspend fun snoozeSuggestionUntil(packageName: String, untilMillis: Long) {
        settingsRepository.setSuggestedSuppressionSnoozeUntil(packageName, untilMillis)
    }
}
