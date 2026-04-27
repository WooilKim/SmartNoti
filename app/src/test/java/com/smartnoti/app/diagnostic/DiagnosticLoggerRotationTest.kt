package com.smartnoti.app.diagnostic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md` Task 1.
 *
 * Failing-test gate (P1 release-prep) for [DiagnosticLogger]'s rotation
 * contract:
 *
 *  - When `.log` would exceed `sizeCapBytes`, logger atomically rotates
 *    `.log` → `.log.1` (overwriting any previous `.log.1`) and starts a fresh
 *    `.log` for the new line.
 *  - Rotation drops every line whose `ts` is older than the configured
 *    retention window — line-by-line tail. Lines newer than the window are
 *    moved to `.log.1` verbatim.
 */
class DiagnosticLoggerRotationTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var preferences: FakeDiagnosticLoggingPreferences

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("filesDir")
        preferences = FakeDiagnosticLoggingPreferences(enabled = true, rawTitleBody = true)
    }

    @Test
    fun exceeding_size_cap_rotates_to_log1_and_starts_new_log() = runBlocking {
        // Tiny cap forces rotation after the first sizeable write.
        val sizeCap = 256L
        val logger = newLogger(sizeCap = sizeCap, clock = { 1_700_000_000_000L })

        // Write enough lines to cross the cap, then one more to force rotation.
        repeat(5) {
            logger.logCapture(
                packageName = "com.example",
                title = "padding-padding-padding-padding-padding-padding-padding",
                originalKey = "com.example|$it",
            )
        }
        // First file should be at or above the cap by now.
        assertTrue(File(filesDir, "diagnostic.log").length() > 0)

        // Pre-seed an old `.log.1` so we can assert overwrite.
        File(filesDir, "diagnostic.log.1").writeText("STALE_PREVIOUS_ROTATION\n")

        // One more write that crosses the cap → rotate.
        logger.logCapture(
            packageName = "com.example",
            title = "trigger-rotation",
            originalKey = "com.example|trigger",
        )

        val log = File(filesDir, "diagnostic.log")
        val log1 = File(filesDir, "diagnostic.log.1")
        assertTrue("`.log.1` must exist after rotation", log1.exists())
        assertFalse(
            "previous `.log.1` content must be overwritten by rotated content",
            log1.readText().contains("STALE_PREVIOUS_ROTATION"),
        )
        // `.log` must contain only the trigger line (fresh file).
        val newLogLines = log.readLines().filter { it.isNotBlank() }
        assertEquals(1, newLogLines.size)
        val obj = JSONObject(newLogLines.single())
        assertEquals("com.example|trigger", obj.getString("originalKey"))
    }

    @Test
    fun rotation_drops_lines_older_than_retention_window() = runBlocking {
        val now = 1_700_000_000_000L
        val twentyFourHoursMs = 24L * 60L * 60L * 1000L
        val retentionMs = twentyFourHoursMs

        // Build the logger with a tiny size cap so that any subsequent write
        // forces a rotation pass.
        val logger = newLogger(
            sizeCap = 64L,
            clock = { now },
            retentionMs = retentionMs,
        )

        // Pre-seed `.log` with two hand-crafted lines: one 25h old (should
        // drop on rotation) and one 1h old (should survive).
        val log = File(filesDir, "diagnostic.log")
        val oldTs = now - 25L * 60L * 60L * 1000L
        val freshTs = now - 1L * 60L * 60L * 1000L
        log.writeText(
            buildString {
                append("""{"stage":"capture","ts":$oldTs,"package":"com.old","titleHashPrefix":"aaaaaaaaaaaa","originalKey":"com.old|1"}""")
                append('\n')
                append("""{"stage":"capture","ts":$freshTs,"package":"com.fresh","titleHashPrefix":"bbbbbbbbbbbb","originalKey":"com.fresh|1"}""")
                append('\n')
            }
        )
        // Pad the file past the size cap so the next write triggers rotation
        // without us needing a public rotate() entrypoint.
        log.appendText("X".repeat(128) + "\n")

        // Trigger rotation by writing one more line.
        logger.logCapture(
            packageName = "com.trigger",
            title = "trigger",
            originalKey = "com.trigger|1",
        )

        val log1Lines = File(filesDir, "diagnostic.log.1").readLines().filter { it.isNotBlank() }
        // The 25h-old row must be dropped; the 1h-old row must survive. The
        // rotated `.log.1` may also contain the padding line we wrote (which
        // is not parseable JSON) — we only assert on JSON-shaped rows.
        val parsed = log1Lines.mapNotNull { line ->
            runCatching { JSONObject(line) }.getOrNull()
        }
        assertTrue(
            "fresh row must survive rotation: $parsed",
            parsed.any { it.optString("package") == "com.fresh" },
        )
        assertFalse(
            "25h-old row must be dropped on rotation: $parsed",
            parsed.any { it.optString("package") == "com.old" },
        )
    }

    private fun newLogger(
        sizeCap: Long,
        clock: () -> Long,
        retentionMs: Long = 24L * 60L * 60L * 1000L,
    ): DiagnosticLogger = DiagnosticLogger.create(
        filesDir = filesDir,
        preferences = preferences,
        clock = clock,
        dispatcher = Dispatchers.Unconfined,
        sizeCapBytes = sizeCap,
        retentionMillis = retentionMs,
    )
}
