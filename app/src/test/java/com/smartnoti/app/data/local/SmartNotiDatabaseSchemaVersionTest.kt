package com.smartnoti.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNotiDatabaseSchemaVersionTest {

    @Test
    fun database_version_matches_notification_entity_schema_change() {
        assertEquals(3, SMART_NOTI_DATABASE_VERSION)
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "isPersistent" })
    }
}
