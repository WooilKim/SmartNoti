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
)
