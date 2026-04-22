package com.smartnoti.app.domain.model

/**
 * Top-level "분류" container introduced by plan
 * `docs/plans/2026-04-22-categories-split-rules-actions.md` Phase P1 Task 2.
 *
 * A Category owns the **action** (`PRIORITY | DIGEST | SILENT | IGNORE`) and
 * wraps a set of matcher Rules via [ruleIds]. The classifier pipeline evolves
 * in Phase P2 so that matched Rules are lifted to their owning Categories and
 * the winning Category decides the delivery action.
 *
 * @property id Stable unique id. For migration-created Categories the id is
 *   `cat-from-rule-<ruleId>` so the migration is idempotent across crashes.
 * @property name User-visible Korean label. Defaults to "분류" but is free-form.
 * @property appPackageName Optional pin to a specific app's package name. When
 *   non-null the Category wins specificity bonuses over keyword-only peers.
 * @property ruleIds Membership list. The same `RuleUiModel.id` may legally
 *   appear in multiple Categories (the codec must not deduplicate).
 * @property action One of [CategoryAction]. Drives the notifier hot path.
 * @property order Drag-reorder index used as the specificity tie-break.
 *   Lower == higher priority (top of the 분류 tab wins ties). Task 6 wires
 *   this into `CategoryConflictResolver`; Task 2 only stores it.
 */
data class Category(
    val id: String,
    val name: String,
    val appPackageName: String?,
    val ruleIds: List<String>,
    val action: CategoryAction,
    val order: Int,
)

/**
 * The four action buckets a Category may route its matched notifications to.
 *
 * Pinned at exactly four values by `CategoryTest.category_action_enum_has_exactly_four_values`
 * — adding a fifth value requires updating the classifier hot path first.
 */
enum class CategoryAction {
    PRIORITY,
    DIGEST,
    SILENT,
    IGNORE,
}

/**
 * Canonical [CategoryAction] -> [NotificationDecision] mapping used by every
 * downstream consumer in the notifier hot path (classifier, notifier, feedback
 * policy). Plan `docs/plans/2026-04-22-categories-split-rules-actions.md`
 * Phase P2 Task 7 — the mapping lives on [CategoryAction] so notifier surfaces
 * don't need to depend on [NotificationClassifier] just to translate.
 */
fun CategoryAction.toDecision(): NotificationDecision = when (this) {
    CategoryAction.PRIORITY -> NotificationDecision.PRIORITY
    CategoryAction.DIGEST -> NotificationDecision.DIGEST
    CategoryAction.SILENT -> NotificationDecision.SILENT
    CategoryAction.IGNORE -> NotificationDecision.IGNORE
}
