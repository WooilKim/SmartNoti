package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.SilentMode
import com.smartnoti.app.domain.model.SilentMode.ARCHIVED
import com.smartnoti.app.domain.model.SilentMode.PROCESSED
import com.smartnoti.app.domain.model.postedAtMillisOrNull
import com.smartnoti.app.domain.usecase.NotificationFeedbackPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class NotificationRepository(
    private val dao: NotificationDao,
) {
    fun observeAll(): Flow<List<NotificationUiModel>> = dao.observeAll().map { entities ->
        entities.map { it.toUiModel() }
    }

    fun observeAllFiltered(hidePersistentNotifications: Boolean): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications.filterPersistent(hidePersistentNotifications)
    }

    /**
     * Returns IGNORE-status rows sorted newest-first — the feed for the
     * opt-in 무시됨 아카이브 screen (plan
     * `2026-04-21-ignore-tier-fourth-decision` Task 6). All other default
     * inbox flows (`observePriority` / `observeDigest` / `observeDigestGroups`
     * / `toHiddenGroups`) filter IGNORE out, so this is the sole surface path
     * to IGNORE rows.
     */
    fun observeIgnoredArchive(): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications
            .filter { it.status == NotificationStatusUi.IGNORE }
            .sortedByDescending { it.postedAtMillis }
    }

    fun observePriority(): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications.toPriorityNotifications(hidePersistentNotifications = false)
    }

    fun observePriorityFiltered(hidePersistentNotifications: Boolean): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications.toPriorityNotifications(hidePersistentNotifications = hidePersistentNotifications)
    }

    fun observeDigest(): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications.toDigestNotifications(hidePersistentNotifications = false)
    }

    fun observeDigestFiltered(hidePersistentNotifications: Boolean): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications.toDigestNotifications(hidePersistentNotifications = hidePersistentNotifications)
    }

    fun observeDigestGroups(): Flow<List<DigestGroupUiModel>> = observeAll().map { notifications ->
        notifications.toDigestGroups(hidePersistentNotifications = false)
    }

    fun observeDigestGroupsFiltered(hidePersistentNotifications: Boolean): Flow<List<DigestGroupUiModel>> = observeAll().map { notifications ->
        notifications.toDigestGroups(hidePersistentNotifications = hidePersistentNotifications)
    }

    fun observeNotification(notificationId: String): Flow<NotificationUiModel?> = observeAll().map { notifications ->
        notifications.firstOrNull { it.id == notificationId }
    }

    fun observeCapturedApps(): Flow<List<CapturedAppSelectionItem>> = dao.observeCapturedApps().map { apps ->
        apps.toCapturedAppSelectionItems()
    }

    fun observeCapturedAppsFiltered(hidePersistentNotifications: Boolean): Flow<List<CapturedAppSelectionItem>> {
        if (!hidePersistentNotifications) {
            return dao.observeCapturedApps().map { apps -> apps.toCapturedAppSelectionItems() }
        }

        return combine(
            dao.observeCapturedApps(),
            dao.observeAll(),
        ) { apps, notifications ->
            apps.toCapturedAppSelectionItems().toCapturedAppSelectionItems(
                notifications = notifications.map(NotificationEntity::toUiModel),
                hidePersistentNotifications = true,
            )
        }
    }

    suspend fun countRecentDuplicates(
        packageName: String,
        contentSignature: String,
        sinceMillis: Long,
    ): Int {
        return dao.countRecentDuplicates(packageName, contentSignature, sinceMillis)
    }

    /**
     * Returns true when a persisted row exists whose (packageName,
     * contentSignature, postedAtMillis) triple matches the arguments exactly.
     *
     * Used by the listener-reconnect sweep (see
     * docs/plans/2026-04-21-listener-reconnect-active-notification-sweep.md) to
     * skip active notifications that survived a previous process life or were
     * already captured by the onboarding bootstrap.
     */
    suspend fun existsByContentSignature(
        packageName: String,
        contentSignature: String,
        postedAtMillis: Long,
    ): Boolean {
        return dao.countByContentSignature(packageName, contentSignature, postedAtMillis) > 0
    }

    suspend fun save(notification: NotificationUiModel, postedAtMillis: Long, contentSignature: String) {
        dao.upsert(notification.toEntity(postedAtMillis, contentSignature))
    }

    suspend fun cleanupLegacyBlankGroupSummaryRows(): Int {
        return dao.deleteLegacyBlankGroupSummaryRows()
    }

    suspend fun deleteAllSilent(): Int {
        return dao.deleteAllSilent()
    }

    suspend fun deleteSilentByPackage(packageName: String): Int {
        return dao.deleteSilentByPackage(packageName)
    }

    /**
     * SILENT_ARCHIVED 행을 SILENT_PROCESSED 로 전이. 이미 PROCESSED 이거나, 대상이 SILENT 가 아니거나,
     * 행이 존재하지 않으면 no-op (false 반환).
     *
     * 반환값 = 실제 행이 바뀌었는지. 호출자는 true 일 때만 tray 원본 알림 cancel 을 이어서 실행해야 한다.
     */
    suspend fun markSilentProcessed(notificationId: String): Boolean {
        val entity = dao.observeAll().first().firstOrNull { it.id == notificationId }
            ?: return false
        if (entity.status != NotificationStatusUi.SILENT.name) return false
        if (entity.silentMode == SilentMode.PROCESSED.name) return false
        dao.upsert(
            entity.copy(
                silentMode = SilentMode.PROCESSED.name,
                reasonTags = appendReasonTag(entity.reasonTags, NotificationFeedbackPolicy.PROCESSED_REASON_TAG),
            )
        )
        return true
    }

    private fun appendReasonTag(existing: String, tag: String): String {
        if (existing.isBlank()) return tag
        if (existing.split("|").any { it.trim() == tag }) return existing
        return "$existing|$tag"
    }

    /**
     * Looks up the stored `StatusBarNotification.key` for [notificationId], if any.
     *
     * Used by the Detail "처리 완료로 표시" action (plan `silent-archive-drift-fix`
     * Task 3) to decide whether to ask the live listener service to cancel the
     * original tray entry after a successful [markSilentProcessed] flip.
     *
     * Returns `null` when no row matches, or when the row was saved before the
     * `sourceEntryKey` column existed (legacy rows). Either way the caller
     * should degrade to DB-only behaviour.
     */
    suspend fun sourceEntryKeyForId(notificationId: String): String? {
        return dao.sourceEntryKeyForId(notificationId)
    }

    suspend fun restoreSilentToPriorityByPackage(packageName: String): Int {
        val candidates = dao.observeAll().first()
            .filter { it.status == NotificationStatusUi.SILENT.name && it.packageName == packageName }
        candidates.forEach { entity ->
            dao.upsert(
                entity.copy(
                    status = NotificationStatusUi.PRIORITY.name,
                    reasonTags = appendUserReasonTag(entity.reasonTags),
                )
            )
        }
        return candidates.size
    }

    /**
     * Plan `docs/plans/2026-04-26-inbox-digest-group-bulk-actions.md` Task 2.
     *
     * Bulk-restore every DIGEST row for [packageName] to PRIORITY and
     * dedup-append the `사용자 분류` reason tag — mirrors
     * [restoreSilentToPriorityByPackage] but scoped to status=DIGEST and uses
     * the `사용자 분류` label for parity with
     * `ApplyCategoryActionToNotificationUseCase` (single-row Detail path).
     *
     * Returns the number of rows updated (zero if the group is empty).
     */
    suspend fun restoreDigestToPriorityByPackage(packageName: String): Int {
        val candidates = dao.observeAll().first()
            .filter { it.status == NotificationStatusUi.DIGEST.name && it.packageName == packageName }
        candidates.forEach { entity ->
            dao.upsert(
                entity.copy(
                    status = NotificationStatusUi.PRIORITY.name,
                    reasonTags = appendUserClassificationReasonTag(entity.reasonTags),
                )
            )
        }
        return candidates.size
    }

    /**
     * Plan `docs/plans/2026-04-26-inbox-digest-group-bulk-actions.md` Task 2.
     *
     * Hard-delete every DIGEST row for [packageName]. Mirrors
     * [deleteSilentByPackage]. Returns the number of rows deleted.
     */
    suspend fun deleteDigestByPackage(packageName: String): Int {
        return dao.deleteDigestByPackage(packageName)
    }

    /**
     * Plan `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md` Task 2.
     *
     * Bulk-restore every IGNORE row to PRIORITY and dedup-append the
     * `사용자 분류` reason tag — mirrors [restoreDigestToPriorityByPackage]
     * but scoped to status=IGNORE across all packages (the IgnoredArchive
     * screen renders a single plain list, not per-package groups).
     *
     * Returns the number of rows updated (zero if the archive is empty).
     */
    suspend fun restoreAllIgnoredToPriority(): Int {
        val candidates = dao.observeAll().first()
            .filter { it.status == NotificationStatusUi.IGNORE.name }
        candidates.forEach { entity ->
            dao.upsert(
                entity.copy(
                    status = NotificationStatusUi.PRIORITY.name,
                    reasonTags = appendUserClassificationReasonTag(entity.reasonTags),
                )
            )
        }
        return candidates.size
    }

    /**
     * Plan `docs/plans/2026-04-27-ignored-archive-bulk-restore-and-clear.md` Task 2.
     *
     * Hard-delete every IGNORE row. Mirrors [deleteAllSilent]. Returns the
     * number of rows deleted.
     */
    suspend fun deleteAllIgnored(): Int {
        return dao.deleteAllIgnored()
    }

    private fun appendUserReasonTag(existing: String): String {
        val tag = "사용자 복구"
        if (existing.isBlank()) return tag
        if (existing.split("|").any { it.trim() == tag }) return existing
        return "$existing|$tag"
    }

    private fun appendUserClassificationReasonTag(existing: String): String {
        val tag = "사용자 분류"
        if (existing.isBlank()) return tag
        if (existing.split("|").any { it.trim() == tag }) return existing
        return "$existing|$tag"
    }

    suspend fun updateNotification(notification: NotificationUiModel, contentSignature: String? = null) {
        val postedAtMillis = notification.postedAtMillisOrNull() ?: System.currentTimeMillis()
        val signature = contentSignature
            ?: dao.observeAll().first().firstOrNull { it.id == notification.id }?.contentSignature
            ?: listOf(notification.title, notification.body).joinToString(" ").trim()
        dao.upsert(notification.toEntity(postedAtMillis, signature))
    }

    companion object {
        @Volatile private var instance: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: buildRepository(context.applicationContext).also { repository ->
                    runBlocking {
                        repository.cleanupLegacyBlankGroupSummaryRows()
                    }
                    instance = repository
                }
            }
        }

        internal fun clearInstanceForTest() {
            instance = null
        }

        private fun buildRepository(context: Context): NotificationRepository {
            val db = Room.databaseBuilder(
                context,
                SmartNotiDatabase::class.java,
                "smartnoti.db",
            )
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration()
                .build()
            return NotificationRepository(db.notificationDao())
        }
    }
}

fun List<CapturedAppOption>.toCapturedAppSelectionItems(
    formatter: DateFormat = DateFormat.getDateTimeInstance(
        DateFormat.SHORT,
        DateFormat.SHORT,
        Locale.KOREA,
    ),
): List<CapturedAppSelectionItem> {
    return map { option ->
        CapturedAppSelectionItem(
            packageName = option.packageName,
            appName = option.appName,
            notificationCount = option.notificationCount,
            lastSeenLabel = formatter.format(Date(option.lastPostedAtMillis)),
        )
    }
}

fun List<CapturedAppSelectionItem>.toCapturedAppSelectionItems(
    notifications: List<NotificationUiModel>,
    hidePersistentNotifications: Boolean,
): List<CapturedAppSelectionItem> {
    if (!hidePersistentNotifications) return this

    val visibleCountByPackage = notifications.filterPersistent(hidePersistentNotifications = true)
        .groupingBy(NotificationUiModel::packageName)
        .eachCount()

    return mapNotNull { app ->
        val visibleCount = visibleCountByPackage[app.packageName] ?: 0
        if (visibleCount == 0) {
            null
        } else {
            app.copy(notificationCount = visibleCount.toLong())
        }
    }
}

private fun matchesSilentModeFilter(
    rowMode: SilentMode?,
    filter: SilentMode?,
): Boolean {
    return when (filter) {
        null -> true
        ARCHIVED -> rowMode == ARCHIVED
        PROCESSED -> rowMode == PROCESSED || rowMode == null
    }
}

fun List<NotificationUiModel>.filterPersistent(hidePersistentNotifications: Boolean): List<NotificationUiModel> {
    return if (hidePersistentNotifications) {
        filterNot(NotificationUiModel::isPersistent)
    } else {
        this
    }
}

fun List<NotificationUiModel>.toPriorityNotifications(
    hidePersistentNotifications: Boolean,
): List<NotificationUiModel> {
    return filterPersistent(hidePersistentNotifications)
        .filter { it.status == NotificationStatusUi.PRIORITY }
}

fun List<NotificationUiModel>.toDigestNotifications(
    hidePersistentNotifications: Boolean,
): List<NotificationUiModel> {
    return filterPersistent(hidePersistentNotifications)
        .filter { it.status == NotificationStatusUi.DIGEST }
}

fun List<NotificationUiModel>.toDigestGroups(
    hidePersistentNotifications: Boolean = false,
): List<DigestGroupUiModel> {
    return toDigestNotifications(hidePersistentNotifications)
        .groupBy { it.packageName }
        .values
        .map { grouped ->
            val latest = grouped.first()
            DigestGroupUiModel(
                id = "digest:${latest.packageName}",
                appName = latest.appName,
                count = grouped.size,
                summary = "${latest.appName} 관련 알림 ${grouped.size}건",
                items = grouped,
            )
        }
        .sortedByDescending { it.items.maxOfOrNull(NotificationUiModel::id) }
}

/**
 * Group SILENT notifications into per-app [DigestGroupUiModel] cards for the
 * Hidden 화면.
 *
 * When [silentModeFilter] is `null` (default), every SILENT row contributes —
 * preserves the pre-silent-split behaviour used by single-list callers.
 *
 * When [silentModeFilter] is set, the result is scoped to that bucket:
 * - [SilentMode.ARCHIVED] → only rows whose `silentMode == ARCHIVED`.
 * - [SilentMode.PROCESSED] → rows whose `silentMode == PROCESSED` **or**
 *   `silentMode == null`. The null case covers legacy rows saved before the
 *   `silent-archive-vs-process-split` plan landed; per plan Open question 4
 *   they are surfaced under the 처리됨 tab so the 보관 중 tab stays clean.
 */
fun List<NotificationUiModel>.toHiddenGroups(
    hidePersistentNotifications: Boolean = false,
    silentModeFilter: SilentMode? = null,
): List<DigestGroupUiModel> {
    return filterPersistent(hidePersistentNotifications)
        .filter { it.status == NotificationStatusUi.SILENT }
        .filter { matchesSilentModeFilter(it.silentMode, silentModeFilter) }
        .groupBy { it.packageName }
        .values
        .map { grouped ->
            val latest = grouped.maxByOrNull { it.postedAtMillis } ?: grouped.first()
            DigestGroupUiModel(
                id = "hidden:${latest.packageName}",
                appName = latest.appName,
                count = grouped.size,
                summary = "${latest.appName} 숨긴 알림 ${grouped.size}건",
                items = grouped.sortedByDescending { it.postedAtMillis },
            )
        }
        .sortedByDescending { group -> group.items.maxOfOrNull { it.postedAtMillis } ?: 0L }
}
