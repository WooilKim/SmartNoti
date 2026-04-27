package com.smartnoti.app.diagnostic

import java.security.MessageDigest

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 2.
 *
 * Sealed JSON-line model the [DiagnosticLogger] writes to
 * `filesDir/diagnostic.log`. Each subtype owns the exact set of fields the
 * Task 1 contract assertions expect (`stage` + per-stage required fields).
 *
 * The encoder is a hand-rolled JSON writer ([JsonLineWriter]) rather than
 * `org.json.JSONObject` because Android's `org.json` is mocked-out in plain
 * unit tests (the mock throws `Method put in org.json.JSONObject not mocked`)
 * and the contract tests assert against on-disk content without using a
 * Robolectric runner. The on-disk format is still vanilla JSON so the test
 * side parses each line back with `org.json.JSONObject` under Robolectric
 * (`DiagnosticLogExporterTest`) or uses the same fields directly under JUnit.
 */
internal sealed class DiagnosticLogEntry {

    abstract fun toJsonLine(): String

    data class Capture(
        val ts: Long,
        val packageName: String,
        val titleHashPrefix: String,
        val originalKey: String,
        // When raw mode is ON, the logger embeds the raw title alongside the
        // hash prefix so the user reproduction shows what they actually saw
        // on screen. Hash prefix stays present so consumers can dedupe.
        val rawTitle: String? = null,
    ) : DiagnosticLogEntry() {
        override fun toJsonLine(): String = JsonLineWriter()
            .field("stage", "capture")
            .field("ts", ts)
            .field("package", packageName)
            .field("titleHashPrefix", titleHashPrefix)
            .field("originalKey", originalKey)
            .let { writer -> if (rawTitle != null) writer.field("title", rawTitle) else writer }
            .build()
    }

    data class Classify(
        val ts: Long,
        val packageName: String,
        val ruleHits: List<String>,
        val decision: String,
        val reasonTags: List<String>,
        val elapsedMs: Long,
    ) : DiagnosticLogEntry() {
        override fun toJsonLine(): String = JsonLineWriter()
            .field("stage", "classify")
            .field("ts", ts)
            .field("package", packageName)
            .arrayField("ruleHits", ruleHits)
            .field("decision", decision)
            .arrayField("reasonTags", reasonTags)
            .field("elapsed_ms", elapsedMs)
            .build()
    }

    data class Route(
        val ts: Long,
        val packageName: String,
        val sourceCancelled: Boolean,
        val replacementPosted: Boolean,
        val channelId: String?,
        val targetMode: String,
    ) : DiagnosticLogEntry() {
        override fun toJsonLine(): String = JsonLineWriter()
            .field("stage", "route")
            .field("ts", ts)
            .field("package", packageName)
            .field("sourceCancelled", sourceCancelled)
            .field("replacementPosted", replacementPosted)
            .field("targetMode", targetMode)
            .nullableStringField("channelId", channelId)
            .build()
    }

    data class Error(
        val ts: Long,
        val at: String,
        val message: String,
        val stackTrace: String,
    ) : DiagnosticLogEntry() {
        override fun toJsonLine(): String = JsonLineWriter()
            .field("stage", "error")
            .field("ts", ts)
            .field("at", at)
            .field("message", message)
            .field("stackTrace", stackTrace)
            .build()
    }
}

/**
 * Tiny hand-rolled JSON object writer. Avoids `org.json.JSONObject` (which
 * Android's host-JVM unit test classpath stubs out). Produces a single-line
 * JSON object — the diagnostic log is one JSON object per `\n`-terminated
 * line. Field order is insertion order so the unit test's fixed-field
 * expectations stay deterministic.
 */
internal class JsonLineWriter {
    private val sb = StringBuilder("{")
    private var first = true

    fun field(name: String, value: String): JsonLineWriter {
        appendComma()
        appendKey(name)
        appendQuoted(value)
        return this
    }

    fun field(name: String, value: Long): JsonLineWriter {
        appendComma()
        appendKey(name)
        sb.append(value)
        return this
    }

    fun field(name: String, value: Boolean): JsonLineWriter {
        appendComma()
        appendKey(name)
        sb.append(if (value) "true" else "false")
        return this
    }

    fun arrayField(name: String, values: List<String>): JsonLineWriter {
        appendComma()
        appendKey(name)
        sb.append('[')
        var firstItem = true
        for (v in values) {
            if (!firstItem) sb.append(',') else firstItem = false
            appendQuoted(v)
        }
        sb.append(']')
        return this
    }

    fun nullableStringField(name: String, value: String?): JsonLineWriter {
        appendComma()
        appendKey(name)
        if (value == null) sb.append("null") else appendQuoted(value)
        return this
    }

    fun build(): String {
        sb.append('}')
        return sb.toString()
    }

    private fun appendComma() {
        if (first) first = false else sb.append(',')
    }

    private fun appendKey(name: String) {
        appendQuoted(name)
        sb.append(':')
    }

    private fun appendQuoted(value: String) {
        sb.append('"')
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c.code < 0x20) {
                    sb.append("\\u")
                    sb.append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
    }
}

/**
 * SHA-256 prefix helper. The first 6 bytes of the digest, lower-hex encoded
 * (12 chars) — matches the privacy contract asserted in Task 1's
 * `title_is_hashed_when_raw_mode_off` test.
 */
internal fun sha256Prefix12(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(12)
    for (i in 0 until 6) {
        val b = digest[i].toInt() and 0xFF
        sb.append(HEX[b ushr 4])
        sb.append(HEX[b and 0x0F])
    }
    return sb.toString()
}

private val HEX = "0123456789abcdef".toCharArray()

/**
 * Truncate a stack trace to at most [limit] characters so that a single
 * runaway throwable cannot blow the diagnostic log past its 10MB cap in one
 * write. `4096` matches the Task 1 contract assertion.
 */
internal fun truncateStackTrace(stack: String, limit: Int = 4096): String =
    if (stack.length <= limit) stack else stack.substring(0, limit)
