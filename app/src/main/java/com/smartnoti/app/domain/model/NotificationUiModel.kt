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
)

enum class NotificationStatusUi {
    PRIORITY,
    DIGEST,
    SILENT,
}
