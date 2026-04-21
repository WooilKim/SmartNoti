package com.smartnoti.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNotiDatabaseSchemaVersionTest {

    @Test
    fun database_version_matches_silent_archive_split_schema_change() {
        // Bumped to 8 for the ruleHitIds column added by
        // docs/plans/2026-04-21-rules-ux-v2-inbox-restructure.md Phase B Task 1
        // so the Detail UI can split classifier signals from rule hits in
        // Phase B Task 3. Previous bumps: 7 for sourceEntryKey (plan
        // `silent-archive-drift-fix` Task 3).
        assertEquals(8, SMART_NOTI_DATABASE_VERSION)
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
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "ruleHitIds" })
    }
}
