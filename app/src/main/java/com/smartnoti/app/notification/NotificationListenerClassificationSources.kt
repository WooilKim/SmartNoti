package com.smartnoti.app.notification

import com.smartnoti.app.data.categories.CategoriesRepository
import com.smartnoti.app.data.rules.RulesRepository
import com.smartnoti.app.data.settings.SettingsRepository
import com.smartnoti.app.data.settings.SmartNotiSettings
import com.smartnoti.app.domain.model.Category
import com.smartnoti.app.domain.model.RuleUiModel
import kotlinx.coroutines.flow.first

/**
 * Thin seam extracted from [SmartNotiNotificationListenerService.processNotification].
 *
 * Plan `docs/plans/2026-04-22-categories-runtime-wiring-fix.md` Drift 1: the
 * listener's inline classification read only pulls Rules, not Categories, so
 * post-P1 classifier always falls back to SILENT. This helper encapsulates
 * the "read repo state needed by [com.smartnoti.app.domain.usecase.NotificationCaptureProcessor.process]"
 * pattern so the contract can be unit-tested without spinning up the
 * [android.service.notification.NotificationListenerService].
 *
 * Task 1 (RED) ships this helper with the bug preserved — [read] returns
 * `categories = emptyList()` even when [CategoriesRepository] has entries —
 * so the accompanying test fails. Task 2 fixes [read] to actually invoke
 * [CategoriesRepository.currentCategories] and wires the listener call site
 * to use this helper instead of its inline read.
 */
class NotificationListenerClassificationSources(
    private val rulesRepository: RulesRepository,
    private val categoriesRepository: CategoriesRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Snapshot of the repository state the classifier needs. Matches the
     * tuple currently assembled inline inside the listener.
     */
    data class Snapshot(
        val rules: List<RuleUiModel>,
        val categories: List<Category>,
        val settings: SmartNotiSettings,
    )

    suspend fun read(): Snapshot {
        val rules = rulesRepository.currentRules()
        // BUG (plan Task 2 fix): the listener's inline read site does not
        // consult CategoriesRepository, so the classifier receives an empty
        // Category list and every user rule falls back to SILENT. Mirror the
        // production bug here — the accompanying RED test pins the contract
        // that this list MUST be [categoriesRepository.currentCategories()].
        val categories: List<Category> = emptyList()
        val settings = settingsRepository.observeSettings().first()
        return Snapshot(rules = rules, categories = categories, settings = settings)
    }
}
