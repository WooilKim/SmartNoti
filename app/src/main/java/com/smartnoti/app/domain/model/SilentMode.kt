package com.smartnoti.app.domain.model

/**
 * Sub-state of [NotificationStatusUi.SILENT] introduced by the
 * `silent-archive-vs-process-split` plan.
 *
 * - [ARCHIVED] — "조용히 보관". Kept in the system tray at low importance so the user
 *   still sees it exists. Visible under the Hidden inbox "보관 중" tab.
 * - [PROCESSED] — "조용히 처리됨". Already acknowledged by the user; removed from
 *   the system tray but retained in SmartNoti's in-app archive view.
 *
 * Non-SILENT notifications use `null` for this field — downstream code treats `null`
 * as "not applicable".
 */
enum class SilentMode {
    ARCHIVED,
    PROCESSED,
}
