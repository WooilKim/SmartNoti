package com.smartnoti.app.diagnostic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md` Task 1.
 *
 * Failing-test gate (P1 release-prep) for [DiagnosticLogger]'s 4-stage append-only
 * JSON-line contract:
 *
 *  - `disabled` ⇒ no file write at all (no-op when the user has not opted in).
 *  - `capture` stage ⇒ exactly one JSON line with `stage` + `ts` + `package` +
 *    `titleHashPrefix` + `originalKey`.
 *  - default privacy mode ⇒ raw title text never appears on disk; only a
 *    12-hex SHA-256 prefix.
 *  - raw mode ON ⇒ raw title text DOES appear on disk (corollary).
 *  - `classify` / `route` / `error` stages each have their own required-field
 *    contract.
 *
 * Tests construct the logger directly with a [TemporaryFolder]-backed
 * `filesDir`, an in-memory [FakeDiagnosticLoggingPreferences], and a
 * single-thread `Dispatchers.Unconfined` so writes are flushed synchronously
 * inside `runBlocking`. The on-disk file is then read line-by-line and
 * parsed via `org.json.JSONObject` (already on the Robolectric classpath).
 */
class DiagnosticLoggerContractTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var preferences: FakeDiagnosticLoggingPreferences

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("filesDir")
        preferences = FakeDiagnosticLoggingPreferences()
    }

    @After
    fun tearDown() {
        // TemporaryFolder cleans itself.
    }

    @Test
    fun disabled_logger_does_not_write() = runBlocking {
        preferences.enabled = false

        val logger = newLogger()

        logger.logCapture(
            packageName = "com.example",
            title = "hello",
            originalKey = "com.example|1",
        )

        val logFile = File(filesDir, "diagnostic.log")
        assertFalse(
            "logger must not create diagnostic.log when preferences.enabled = false",
            logFile.exists(),
        )
    }

    @Test
    fun capture_writes_one_json_line_with_required_fields() = runBlocking {
        preferences.enabled = true

        val logger = newLogger(clock = { 1_700_000_000_000L })

        logger.logCapture(
            packageName = "com.example",
            title = "hello",
            originalKey = "com.example|1",
        )

        val lines = readLines()
        assertEquals(1, lines.size)
        val obj = JSONObject(lines.single())
        assertEquals("capture", obj.getString("stage"))
        assertEquals(1_700_000_000_000L, obj.getLong("ts"))
        assertEquals("com.example", obj.getString("package"))
        assertEquals(12, obj.getString("titleHashPrefix").length)
        assertEquals("com.example|1", obj.getString("originalKey"))
    }

    @Test
    fun title_is_hashed_when_raw_mode_off() = runBlocking {
        preferences.enabled = true
        preferences.rawTitleBody = false

        val logger = newLogger()

        logger.logCapture(
            packageName = "com.example",
            title = "(광고) 오늘만 특가",
            originalKey = "com.example|1",
        )

        val rawText = File(filesDir, "diagnostic.log").readText()
        assertFalse(
            "raw title body must not appear on disk when rawTitleBody = false",
            rawText.contains("오늘만 특가"),
        )
        // titleHashPrefix is a 12-char lowercase hex prefix of SHA-256.
        val obj = JSONObject(rawText.trim())
        val hashPrefix = obj.getString("titleHashPrefix")
        assertEquals(12, hashPrefix.length)
        assertTrue(hashPrefix.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun raw_mode_on_writes_raw_title() = runBlocking {
        preferences.enabled = true
        preferences.rawTitleBody = true

        val logger = newLogger()

        logger.logCapture(
            packageName = "com.example",
            title = "(광고) 오늘만 특가",
            originalKey = "com.example|1",
        )

        val rawText = File(filesDir, "diagnostic.log").readText()
        assertTrue(
            "raw title body must appear on disk when rawTitleBody = true",
            rawText.contains("(광고) 오늘만 특가"),
        )
    }

    @Test
    fun classify_route_error_stages_each_have_distinct_required_fields() = runBlocking {
        preferences.enabled = true

        val logger = newLogger(clock = { 42L })

        logger.logClassify(
            packageName = "com.example",
            ruleHits = listOf("rule-1", "rule-2"),
            decision = "DIGEST",
            reasonTags = listOf("(광고)", "promo"),
            elapsedMs = 7L,
        )
        logger.logRoute(
            packageName = "com.example",
            sourceCancelled = true,
            replacementPosted = false,
            channelId = "smartnoti_digest",
            targetMode = "DIGEST",
        )
        val throwable = IllegalStateException("boom")
        logger.logError(
            at = "FakeClass.fakeMethod",
            t = throwable,
        )

        val lines = readLines()
        assertEquals(3, lines.size)

        val classify = JSONObject(lines[0])
        assertEquals("classify", classify.getString("stage"))
        assertEquals(42L, classify.getLong("ts"))
        assertEquals("com.example", classify.getString("package"))
        val ruleHits = classify.getJSONArray("ruleHits")
        assertEquals(2, ruleHits.length())
        assertEquals("rule-1", ruleHits.getString(0))
        assertEquals("DIGEST", classify.getString("decision"))
        val reasonTags = classify.getJSONArray("reasonTags")
        assertEquals(2, reasonTags.length())
        assertEquals(7L, classify.getLong("elapsed_ms"))

        val route = JSONObject(lines[1])
        assertEquals("route", route.getString("stage"))
        assertEquals(true, route.getBoolean("sourceCancelled"))
        assertEquals(false, route.getBoolean("replacementPosted"))
        assertEquals("smartnoti_digest", route.getString("channelId"))
        assertEquals("DIGEST", route.getString("targetMode"))

        val error = JSONObject(lines[2])
        assertEquals("error", error.getString("stage"))
        assertEquals("FakeClass.fakeMethod", error.getString("at"))
        assertEquals("boom", error.getString("message"))
        val stack = error.getString("stackTrace")
        assertNotNull(stack)
        assertTrue(
            "stackTrace must be capped at 4KB",
            stack.length <= 4096,
        )
    }

    private fun newLogger(
        clock: () -> Long = { System.currentTimeMillis() },
        sizeCapBytes: Long = 10L * 1024L * 1024L,
        retentionMillis: Long = 24L * 60L * 60L * 1000L,
    ): DiagnosticLogger = DiagnosticLogger.create(
        filesDir = filesDir,
        preferences = preferences,
        clock = clock,
        dispatcher = Dispatchers.Unconfined,
        sizeCapBytes = sizeCapBytes,
        retentionMillis = retentionMillis,
    )

    private fun readLines(): List<String> {
        val file = File(filesDir, "diagnostic.log")
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }
}
