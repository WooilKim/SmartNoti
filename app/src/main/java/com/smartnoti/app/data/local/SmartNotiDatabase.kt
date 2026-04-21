package com.smartnoti.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val SMART_NOTI_DATABASE_VERSION = 9

@Database(
    entities = [NotificationEntity::class],
    version = SMART_NOTI_DATABASE_VERSION,
    exportSchema = false,
)
abstract class SmartNotiDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}

/**
 * No-op migration from schema v8 to v9 introduced by plan
 * `2026-04-21-ignore-tier-fourth-decision` Task 2.
 *
 * The only change is that the free-form `status` column now accepts the new
 * enum value `"IGNORE"` alongside `PRIORITY / DIGEST / SILENT`. No table
 * schema diff — no `ALTER TABLE`, no column add, no data rewrite. The
 * migration is declared explicitly so a future schema-sweep tool reading the
 * version history does not get confused by the jump and so upgraders on
 * non-destructive builds retain their rows.
 */
internal val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Intentionally empty — enum value set expansion, no schema change.
    }
}
