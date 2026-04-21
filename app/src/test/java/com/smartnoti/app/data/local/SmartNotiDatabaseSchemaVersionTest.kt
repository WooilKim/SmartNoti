package com.smartnoti.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNotiDatabaseSchemaVersionTest {

    @Test
    fun database_version_matches_silent_archive_split_schema_change() {
        // Bumped to 9 for the IGNORE enum value added by
        // docs/plans/2026-04-21-ignore-tier-fourth-decision.md Task 2 — the
        // `status` column is free-form string so there is no table schema
        // diff, but the version bump + explicit no-op MIGRATION_8_9 records
        // the enum-set expansion in history. Previous bumps: 8 for
        // ruleHitIds (rules-ux-v2-inbox-restructure Phase B Task 1),
        // 7 for sourceEntryKey (silent-archive-drift-fix Task 3).
        assertEquals(9, SMART_NOTI_DATABASE_VERSION)
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
