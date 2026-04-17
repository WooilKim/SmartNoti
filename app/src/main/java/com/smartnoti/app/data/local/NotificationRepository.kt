package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import com.smartnoti.app.domain.model.DigestGroupUiModel
import com.smartnoti.app.domain.model.NotificationStatusUi
import com.smartnoti.app.domain.model.NotificationUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class NotificationRepository private constructor(
    private val dao: NotificationDao,
) {
    fun observeAll(): Flow<List<NotificationUiModel>> = dao.observeAll().map { entities ->
        entities.map { it.toUiModel() }
    }

    fun observePriority(): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications.filter { it.status == NotificationStatusUi.PRIORITY }
    }

    fun observeDigest(): Flow<List<NotificationUiModel>> = observeAll().map { notifications ->
        notifications.filter { it.status == NotificationStatusUi.DIGEST }
    }

    fun observeDigestGroups(): Flow<List<DigestGroupUiModel>> = observeDigest().map { notifications ->
        notifications.toDigestGroups()
    }

    fun observeNotification(notificationId: String): Flow<NotificationUiModel?> = observeAll().map { notifications ->
        notifications.firstOrNull { it.id == notificationId }
    }

    suspend fun countRecentDuplicates(
        packageName: String,
        contentSignature: String,
        sinceMillis: Long,
    ): Int {
        return dao.countRecentDuplicates(packageName, contentSignature, sinceMillis)
    }

    suspend fun save(notification: NotificationUiModel, postedAtMillis: Long, contentSignature: String) {
        dao.upsert(notification.toEntity(postedAtMillis, contentSignature))
    }

    suspend fun updateNotification(notification: NotificationUiModel, contentSignature: String? = null) {
        val postedAtMillis = notification.id.substringAfterLast(':').toLongOrNull() ?: System.currentTimeMillis()
        val signature = contentSignature
            ?: dao.observeAll().first().firstOrNull { it.id == notification.id }?.contentSignature
            ?: listOf(notification.title, notification.body).joinToString(" ").trim()
        dao.upsert(notification.toEntity(postedAtMillis, signature))
    }

    companion object {
        @Volatile private var instance: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: buildRepository(context.applicationContext).also { instance = it }
            }
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

fun List<NotificationUiModel>.toDigestGroups(): List<DigestGroupUiModel> {
    return filter { it.status == NotificationStatusUi.DIGEST }
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
