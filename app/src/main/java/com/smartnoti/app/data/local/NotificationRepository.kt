package com.smartnoti.app.data.local

import android.content.Context
import androidx.room.Room
import com.smartnoti.app.domain.model.NotificationUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NotificationRepository private constructor(
    private val dao: NotificationDao,
) {
    fun observeAll(): Flow<List<NotificationUiModel>> = dao.observeAll().map { entities ->
        entities.map { it.toUiModel() }
    }

    suspend fun save(notification: NotificationUiModel, postedAtMillis: Long) {
        dao.upsert(notification.toEntity(postedAtMillis))
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
            ).build()
            return NotificationRepository(db.notificationDao())
        }
    }
}
