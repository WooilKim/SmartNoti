package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import com.smartnoti.app.domain.model.postedAtMillisOrNull
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

    private fun appendUserReasonTag(existing: String): String {
        val tag = "사용자 복구"
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
            ).fallbackToDestructiveMigration().build()
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

fun List<NotificationUiModel>.toHiddenGroups(
    hidePersistentNotifications: Boolean = false,
): List<DigestGroupUiModel> {
    return filterPersistent(hidePersistentNotifications)
        .filter { it.status == NotificationStatusUi.SILENT }
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
