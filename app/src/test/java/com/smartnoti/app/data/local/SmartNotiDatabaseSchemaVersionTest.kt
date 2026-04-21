package com.smartnoti.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNotiDatabaseSchemaVersionTest {

    @Test
    fun database_version_matches_silent_archive_split_schema_change() {
        // Bumped to 7 for the sourceEntryKey column added by
        // docs/plans/2026-04-20-silent-archive-drift-fix.md Task 3 so Detail's
        // "처리 완료로 표시" action can chain tray cancel after the DB flip.
        assertEquals(7, SMART_NOTI_DATABASE_VERSION)
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "isPersistent" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "deliveryChannelKey" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "alertLevel" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "vibrationMode" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "headsUpEnabled" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "lockScreenVisibility" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "sourceSuppressionState" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "replacementNotificationIssued" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "silentMode" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "sourceEntryKey" })
    }
}
