package com.smartnoti.app.domain.model

/**
 * Plan `2026-04-27-inbox-sort-by-priority-or-app.md` Task 1.
 *
 * Modes the user picks via the inbox sort dropdown. Persisted as `name` in
 * [com.smartnoti.app.data.settings.SmartNotiSettings.inboxSortMode] so adding
 * a new mode is a backward-compatible append (existing stored values still
 * round-trip via [valueOf]).
 *
 * - [RECENT]      — current default; sort flat rows / groups by most recent
 *   posted timestamp descending.
 * - [IMPORTANCE]  — PRIORITY > DIGEST > SILENT, then most recent within the
 *   same status. NOTE: the unified inbox screen splits these statuses into
 *   sub-tabs already, so within any single sub-tab IMPORTANCE collapses to
 *   the same ordering as RECENT. The mode is shipped now so that future
 *   cross-status feeds and other surfaces can reuse the same enum.
 * - [BY_APP]      — group by `appName.lowercase()` ascending (case-insensitive
 *   natural ordering); within the same app, most recent first.
 */
enum class InboxSortMode {
    RECENT,
    IMPORTANCE,
    BY_APP,
}
