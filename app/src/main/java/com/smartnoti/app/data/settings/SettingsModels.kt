package com.smartnoti.app.data.settings

import com.smartnoti.app.domain.model.AlertLevel
import com.smartnoti.app.domain.model.LockScreenVisibilityMode
import com.smartnoti.app.domain.model.VibrationMode

data class SmartNotiSettings(
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartHour: Int = 23,
    val quietHoursEndHour: Int = 7,
    val digestHours: List<Int> = listOf(12, 18, 21),
    val priorityAlertLevel: String = AlertLevel.LOUD.name,
    val priorityVibrationMode: String = VibrationMode.STRONG.name,
    val priorityHeadsUpEnabled: Boolean = true,
    val priorityLockScreenVisibility: String = LockScreenVisibilityMode.PRIVATE.name,
    val digestAlertLevel: String = AlertLevel.SOFT.name,
    val digestVibrationMode: String = VibrationMode.LIGHT.name,
    val digestHeadsUpEnabled: Boolean = false,
    val digestLockScreenVisibility: String = LockScreenVisibilityMode.PRIVATE.name,
    val silentAlertLevel: String = AlertLevel.NONE.name,
    val silentVibrationMode: String = VibrationMode.OFF.name,
    val silentHeadsUpEnabled: Boolean = false,
    val silentLockScreenVisibility: String = LockScreenVisibilityMode.SECRET.name,
    val suppressSourceForDigestAndSilent: Boolean = false,
    val suppressedSourceApps: Set<String> = emptySet(),
    val suppressionExcludedApps: Set<String> = emptySet(),
    val hidePersistentNotifications: Boolean = true,
    val hidePersistentSourceNotifications: Boolean = false,
    val protectCriticalPersistentNotifications: Boolean = true,
)
