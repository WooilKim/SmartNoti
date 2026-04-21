package com.smartnoti.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

internal const val SMART_NOTI_DATABASE_VERSION = 6

@Database(
    entities = [NotificationEntity::class],
    version = SMART_NOTI_DATABASE_VERSION,
    exportSchema = false,
)
abstract class SmartNotiDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}
