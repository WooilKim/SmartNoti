package com.smartnoti.app.diagnostic

import org.json.JSONObject
import java.io.File

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 2.
 *
 * Owns the size-cap + 24h tail-drop rotation logic separately from the
 * append path so [DiagnosticLoggerRotationTest] can exercise it directly.
 *
 * Rotation contract:
 *  - Triggered when the existing `.log` file's size exceeds [sizeCapBytes].
 *  - Reads `.log` line-by-line, parses each as JSON, drops every line whose
 *    `ts` field is older than `now - retentionMillis`, and writes the
 *    survivors to `.log.1` (overwriting any previous `.log.1`).
 *  - Truncates `.log` so the next append starts a fresh file.
 *  - Lines that fail JSON parse (eg. user pre-seeded padding rows) are kept
 *    verbatim — the rotator only filters JSON-shaped rows it can prove are
 *    too old. This matches the Task 1 rotation test which seeds 'X'-padding.
 */
internal class DiagnosticLogRotator(
    private val logFile: File,
    private val rotatedFile: File,
    private val sizeCapBytes: Long,
    private val retentionMillis: Long,
    private val clock: () -> Long,
) {

    fun shouldRotate(): Boolean = logFile.exists() && logFile.length() > sizeCapBytes

    fun rotate() {
        if (!logFile.exists()) return
        val cutoff = clock() - retentionMillis
        val survivors = mutableListOf<String>()
        logFile.useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val parsed = runCatching { JSONObject(line) }.getOrNull()
                if (parsed == null) {
                    // Non-JSON row — keep verbatim. Padding / corrupted rows
                    // should still survive into the rotated file so a human
                    // reproduction does not silently lose context.
                    survivors += line
                    continue
                }
                val ts = parsed.optLong("ts", Long.MIN_VALUE)
                if (ts == Long.MIN_VALUE) {
                    // No `ts` field — keep verbatim (defensive).
                    survivors += line
                    continue
                }
                if (ts >= cutoff) survivors += line
            }
        }
        // Atomic-ish replace: write rotated content first, then truncate the
        // live log. If the JVM dies between the two writes the worst case is
        // a duplicated tail in `.log.1` plus a still-full `.log` — both are
        // strictly safer than losing the survivors.
        rotatedFile.writeText(buildString {
            for (line in survivors) {
                append(line)
                append('\n')
            }
        })
        logFile.writeText("")
    }
}
