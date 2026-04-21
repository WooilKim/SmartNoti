package com.smartnoti.app.domain.model

data class NotificationUiModel(
    val id: String,
    val appName: String,
    val packageName: String,
    val sender: String?,
    val title: String,
    val body: String,
    val receivedAtLabel: String,
    val status: NotificationStatusUi,
    val reasonTags: List<String>,
    val score: Int? = null,
    val isBundled: Boolean = false,
    val isPersistent: Boolean = false,
    val deliveryChannelKey: String = DeliveryProfile.CHANNEL_SILENT,
    val alertLevel: AlertLevel = AlertLevel.NONE,
    val vibrationMode: VibrationMode = VibrationMode.OFF,
    val headsUpEnabled: Boolean = false,
    val lockScreenVisibility: LockScreenVisibilityMode = LockScreenVisibilityMode.SECRET,
    val sourceSuppressionState: SourceNotificationSuppressionState = SourceNotificationSuppressionState.NOT_CONFIGURED,
    val replacementNotificationIssued: Boolean = false,
    val postedAtMillis: Long = 0L,
    val silentMode: SilentMode? = null,
    /**
     * StatusBarNotification key of the tray entry that produced this row, when captured.
     *
     * Kept as a persistence-facing hint — the UI does not surface this directly.
     * Detail's "처리 완료로 표시" action reads it back from the repository to
     * chain the tray cancel alongside the DB transition (plan
     * `silent-archive-drift-fix` Task 3).
     */
    val sourceEntryKey: String? = null,
    /**
     * Ordered list of user rule ids that fired during classification for this
     * notification. Empty when the decision came from a classifier-internal
     * signal (VIP sender, priority keyword, quiet-hours shopping, repeat
     * burst) rather than a user rule (plan `rules-ux-v2-inbox-restructure`
     * Phase B Task 2).
     *
     * Persisted via [com.smartnoti.app.data.local.NotificationEntity.ruleHitIds]
     * as a comma-separated string. Surfaced in Phase B Task 3's Detail UI as
     * the "적용된 규칙" sub-section, distinct from the free-form [reasonTags]
     * classifier-signal chips.
     */
    val matchedRuleIds: List<String> = emptyList(),
)

enum class NotificationStatusUi {
    PRIORITY,
    DIGEST,
    SILENT,

    /**
     * Persisted storage counterpart of [com.smartnoti.app.domain.model.NotificationDecision.IGNORE]
     * added by plan `2026-04-21-ignore-tier-fourth-decision` Task 2. Rows with
     * this status are kept in the DB for audit / weekly insights but filtered
     * out of the default Home / Priority / Digest / Hidden views (Task 6).
     */
    IGNORE,
}
