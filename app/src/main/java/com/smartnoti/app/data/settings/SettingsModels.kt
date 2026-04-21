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
    val hidePersistentNotifications: Boolean = true,
    val hidePersistentSourceNotifications: Boolean = false,
    val protectCriticalPersistentNotifications: Boolean = true,
    // Plan `2026-04-21-ignore-tier-fourth-decision` Task 6: default OFF. When
    // true, SmartNoti exposes the 무시됨 아카이브 route (conditional nav entry)
    // so power users can audit / recover rows the IGNORE rule swept out of
    // the default views. Toggling this is the only in-app surface that reveals
    // IGNORE rows — the repository still persists them regardless.
    val showIgnoredArchive: Boolean = false,
)
