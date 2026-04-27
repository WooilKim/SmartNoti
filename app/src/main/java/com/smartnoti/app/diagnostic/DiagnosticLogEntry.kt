package com.smartnoti.app.diagnostic

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 2.
 *
 * Sealed JSON-line model the [DiagnosticLogger] writes to
 * `filesDir/diagnostic.log`. Each subtype owns the exact set of fields the
 * Task 1 contract assertions expect (`stage` + per-stage required fields).
 *
 * `org.json.JSONObject` is the encoder rather than `kotlinx.serialization` so
 * the module does not introduce a new build dependency — the test classpath
 * already pulls `org.json` for [DiagnosticLoggerContractTest], and the main
 * classpath has it via the Android SDK.
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
        override fun toJsonLine(): String {
            val obj = JSONObject()
                .put("stage", "capture")
                .put("ts", ts)
                .put("package", packageName)
                .put("titleHashPrefix", titleHashPrefix)
                .put("originalKey", originalKey)
            if (rawTitle != null) obj.put("title", rawTitle)
            return obj.toString()
        }
    }

    data class Classify(
        val ts: Long,
        val packageName: String,
        val ruleHits: List<String>,
        val decision: String,
        val reasonTags: List<String>,
        val elapsedMs: Long,
    ) : DiagnosticLogEntry() {
        override fun toJsonLine(): String {
            val ruleHitsArray = JSONArray().apply { ruleHits.forEach { put(it) } }
            val reasonTagsArray = JSONArray().apply { reasonTags.forEach { put(it) } }
            return JSONObject()
                .put("stage", "classify")
                .put("ts", ts)
                .put("package", packageName)
                .put("ruleHits", ruleHitsArray)
                .put("decision", decision)
                .put("reasonTags", reasonTagsArray)
                .put("elapsed_ms", elapsedMs)
                .toString()
        }
    }

    data class Route(
        val ts: Long,
        val packageName: String,
        val sourceCancelled: Boolean,
        val replacementPosted: Boolean,
        val channelId: String?,
        val targetMode: String,
    ) : DiagnosticLogEntry() {
        override fun toJsonLine(): String {
            val obj = JSONObject()
                .put("stage", "route")
                .put("ts", ts)
                .put("package", packageName)
                .put("sourceCancelled", sourceCancelled)
                .put("replacementPosted", replacementPosted)
                .put("targetMode", targetMode)
            obj.put("channelId", channelId ?: JSONObject.NULL)
            return obj.toString()
        }
    }

    data class Error(
        val ts: Long,
        val at: String,
        val message: String,
        val stackTrace: String,
    ) : DiagnosticLogEntry() {
        override fun toJsonLine(): String =
            JSONObject()
                .put("stage", "error")
                .put("ts", ts)
                .put("at", at)
                .put("message", message)
                .put("stackTrace", stackTrace)
                .toString()
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
