package com.smartnoti.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [NotificationEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SmartNotiDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}
