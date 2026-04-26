package com.smartnoti.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY postedAtMillis DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query(
        """
        SELECT packageName, appName, MAX(postedAtMillis) AS lastPostedAtMillis, COUNT(*) AS notificationCount
        FROM notifications
        GROUP BY packageName, appName
        ORDER BY lastPostedAtMillis DESC
        """
    )
    fun observeCapturedApps(): Flow<List<CapturedAppOption>>

    @Query("SELECT COUNT(*) FROM notifications WHERE packageName = :packageName AND contentSignature = :contentSignature AND postedAtMillis >= :sinceMillis")
    suspend fun countRecentDuplicates(
        packageName: String,
        contentSignature: String,
        sinceMillis: Long,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM notifications WHERE packageName = :packageName AND contentSignature = :contentSignature AND postedAtMillis = :postedAtMillis"
    )
    suspend fun countByContentSignature(
        packageName: String,
        contentSignature: String,
        postedAtMillis: Long,
    ): Int

    @Query(
        """
        DELETE FROM notifications
        WHERE id LIKE '%:ranker_group'
          AND TRIM(title) = ''
          AND TRIM(body) = ''
        """
    )
    suspend fun deleteLegacyBlankGroupSummaryRows(): Int

    @Query("DELETE FROM notifications WHERE status = 'SILENT'")
    suspend fun deleteAllSilent(): Int

    @Query("DELETE FROM notifications WHERE status = 'SILENT' AND packageName = :packageName")
    suspend fun deleteSilentByPackage(packageName: String): Int

    @Query("DELETE FROM notifications WHERE status = 'DIGEST' AND packageName = :packageName")
    suspend fun deleteDigestByPackage(packageName: String): Int

    @Query("SELECT sourceEntryKey FROM notifications WHERE id = :id LIMIT 1")
    suspend fun sourceEntryKeyForId(id: String): String?
}
