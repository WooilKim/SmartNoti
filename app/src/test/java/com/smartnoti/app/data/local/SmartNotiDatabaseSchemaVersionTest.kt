package com.smartnoti.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartNotiDatabaseSchemaVersionTest {

    @Test
    fun database_version_matches_delivery_metadata_schema_change() {
        assertEquals(4, SMART_NOTI_DATABASE_VERSION)
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "isPersistent" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "deliveryChannelKey" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "alertLevel" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "vibrationMode" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "headsUpEnabled" })
        assertTrue(NotificationEntity::class.java.declaredFields.any { it.name == "lockScreenVisibility" })
    }
}
