package com.smartnoti.app.diagnostic

import java.io.File

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 2.
 *
 * Owns the size-cap + 24h tail-drop rotation logic separately from the
 * append path so [DiagnosticLoggerRotationTest] can exercise it directly.
 *
 * Rotation contract (post-append):
 *  - Logger appends the new line to `.log`, THEN calls [rotateIfNeeded].
 *    When the file's size exceeds [sizeCapBytes], rotation moves every
 *    line except the most recently appended one into `.log.1`
 *    (overwriting any previous `.log.1`) and keeps the most recent line in
 *    `.log` so the live tail is never empty after a rotation cycle.
 *  - Lines moved into `.log.1` are filtered by `ts`: anything older than
 *    `now - retentionMillis` is dropped (24h tail). Lines that fail JSON
 *    parse (eg. test padding rows) are kept verbatim so a human
 *    reproduction does not silently lose context.
 *  - `rotatedFile.writeText(...)` overwrites the previous `.log.1` content.
 */
internal class DiagnosticLogRotator(
    private val logFile: File,
    private val rotatedFile: File,
    private val sizeCapBytes: Long,
    private val retentionMillis: Long,
    private val clock: () -> Long,
) {

    fun shouldRotate(): Boolean = logFile.exists() && logFile.length() > sizeCapBytes

    /**
     * Reads all lines of `.log`, splits into "older lines" and "tail line"
     * (the most recently appended line — what the logger just wrote in the
     * triggering append). The older lines are filtered by `ts` and written
     * to `.log.1`; the tail line is left in `.log` as the only surviving
     * row so the live log keeps a fresh tail.
     */
    fun rotate() {
        if (!logFile.exists()) return
        val cutoff = clock() - retentionMillis
        val allLines = logFile.readLines().filter { it.isNotBlank() }
        if (allLines.isEmpty()) return
        val tail = allLines.last()
        val older = allLines.dropLast(1)
        val survivors = mutableListOf<String>()
        for (line in older) {
            val ts = extractTsFromJsonLine(line)
            if (ts == null) {
                // Non-JSON row — keep verbatim. Padding / corrupted rows
                // should still survive into the rotated file so a human
                // reproduction does not silently lose context.
                survivors += line
                continue
            }
            if (ts >= cutoff) survivors += line
        }
        // Atomic-ish replace: write rotated content first, then rewrite
        // `.log` to contain only the tail line. If the JVM dies between
        // the two writes the worst case is a duplicated tail in `.log.1`
        // plus a still-full `.log` — both strictly safer than losing the
        // survivors.
        rotatedFile.writeText(buildString {
            for (line in survivors) {
                append(line)
                append('\n')
            }
        })
        logFile.writeText(tail + "\n")
    }

    /**
     * Extracts the `"ts":<long>` numeric field from a JSON-line. Hand-rolled
     * to avoid pulling `org.json.JSONObject` into the rotator (Android's
     * `org.json` stub on the unit-test classpath throws "not mocked"). The
     * format is fixed by [DiagnosticLogEntry.JsonLineWriter] so a substring
     * scan is sufficient — returns `null` for rows the writer did not
     * produce (eg. test padding rows).
     */
    private fun extractTsFromJsonLine(line: String): Long? {
        val key = "\"ts\":"
        val keyIndex = line.indexOf(key)
        if (keyIndex < 0) return null
        val start = keyIndex + key.length
        var end = start
        while (end < line.length) {
            val c = line[end]
            if (c == ',' || c == '}') break
            end++
        }
        val raw = line.substring(start, end).trim()
        return raw.toLongOrNull()
    }
}
