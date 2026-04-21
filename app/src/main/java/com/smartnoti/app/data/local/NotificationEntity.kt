package com.smartnoti.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val appName: String,
    val packageName: String,
    val sender: String?,
    val title: String,
    val body: String,
    val postedAtMillis: Long,
    val status: String,
    val reasonTags: String,
    val score: Int?,
    val isBundled: Boolean,
    val isPersistent: Boolean,
    val contentSignature: String,
    val deliveryChannelKey: String = "smartnoti_silent",
    val alertLevel: String = "NONE",
    val vibrationMode: String = "OFF",
    val headsUpEnabled: Boolean = false,
    val lockScreenVisibility: String = "SECRET",
    val sourceSuppressionState: String = "NOT_CONFIGURED",
    val replacementNotificationIssued: Boolean = false,
    val silentMode: String? = null,
    /**
     * StatusBarNotification key of the tray entry that produced this row, when available.
     *
     * Captured so the Detail "처리 완료로 표시" action (plan
     * `silent-archive-drift-fix` Task 3) can pipe it into the live listener
     * service to cancel the original tray notification alongside the DB flip.
     * `null` on legacy rows saved before the column existed — consumers must
     * treat that as "no tray cancel available" and fall back to DB-only
     * behaviour.
     */
    val sourceEntryKey: String? = null,
    /**
     * Comma-separated list of user rule ids that fired during classification,
     * when any. `null` on legacy rows saved before schema v8 (plan
     * `rules-ux-v2-inbox-restructure` Phase B Task 1).
     *
     * Stored separately from [reasonTags] so the Detail UI can split
     * classifier signals ("SmartNoti 가 본 신호") from rule hits
     * ("적용된 규칙") in Phase B Task 3. Consumers MUST treat `null` as
     * "no rule hit recorded" — functionally equivalent to an empty list.
     */
    val ruleHitIds: String? = null,
)
