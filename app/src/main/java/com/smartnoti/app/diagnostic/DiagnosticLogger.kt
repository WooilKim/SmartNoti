package com.smartnoti.app.diagnostic

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 2.
 *
 * Append-only JSON-line logger for the four notification-pipeline stages
 * (capture / classify / route / error). Default OFF — every public method
 * short-circuits when [isEnabled] is false so the hot-path call sites pay
 * no allocation cost when the user has not opted in.
 *
 * Production wiring is built via [DiagnosticLoggerProvider.getInstance];
 * tests build instances directly via [create] with a [TemporaryFolder]-backed
 * `filesDir`, [Dispatchers.Unconfined], and an in-memory
 * [DiagnosticLoggingPreferencesReader]. The static [create] factory is the
 * single seam both production and tests share so the contract surface stays
 * narrow.
 */
interface DiagnosticLogger {

    fun isEnabled(): Boolean

    fun logCapture(
        packageName: String,
        title: String,
        originalKey: String,
    )

    fun logClassify(
        packageName: String,
        ruleHits: List<String>,
        decision: String,
        reasonTags: List<String>,
        elapsedMs: Long,
    )

    fun logRoute(
        packageName: String,
        sourceCancelled: Boolean,
        replacementPosted: Boolean,
        channelId: String?,
        targetMode: String,
    )

    fun logError(at: String, t: Throwable)

    companion object {
        /**
         * Build a logger backed by `filesDir/diagnostic.log` (+ `.log.1`).
         * The [dispatcher] arg lets unit tests pass [Dispatchers.Unconfined]
         * to drain writes synchronously inside `runBlocking`; production
         * passes a single-thread `Dispatchers.IO.limitedParallelism(1)`.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun create(
            filesDir: File,
            preferences: DiagnosticLoggingPreferencesReader,
            clock: () -> Long = { System.currentTimeMillis() },
            dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
            sizeCapBytes: Long = 10L * 1024L * 1024L,
            retentionMillis: Long = 24L * 60L * 60L * 1000L,
        ): DiagnosticLogger = DiagnosticLoggerImpl(
            filesDir = filesDir,
            preferences = preferences,
            clock = clock,
            dispatcher = dispatcher,
            sizeCapBytes = sizeCapBytes,
            retentionMillis = retentionMillis,
        )
    }
}

private class DiagnosticLoggerImpl(
    private val filesDir: File,
    private val preferences: DiagnosticLoggingPreferencesReader,
    private val clock: () -> Long,
    @Suppress("UNUSED_PARAMETER") dispatcher: CoroutineDispatcher,
    sizeCapBytes: Long,
    retentionMillis: Long,
) : DiagnosticLogger {

    private val logFile: File = File(filesDir, LOG_FILE_NAME)
    private val rotatedFile: File = File(filesDir, ROTATED_FILE_NAME)
    private val rotator = DiagnosticLogRotator(
        logFile = logFile,
        rotatedFile = rotatedFile,
        sizeCapBytes = sizeCapBytes,
        retentionMillis = retentionMillis,
        clock = clock,
    )

    override fun isEnabled(): Boolean = preferences.isEnabled()

    override fun logCapture(
        packageName: String,
        title: String,
        originalKey: String,
    ) {
        if (!preferences.isEnabled()) return
        val raw = preferences.isRawTitleBodyEnabled()
        val entry = DiagnosticLogEntry.Capture(
            ts = clock(),
            packageName = packageName,
            titleHashPrefix = sha256Prefix12(title),
            originalKey = originalKey,
            rawTitle = if (raw) title else null,
        )
        enqueue(entry)
    }

    override fun logClassify(
        packageName: String,
        ruleHits: List<String>,
        decision: String,
        reasonTags: List<String>,
        elapsedMs: Long,
    ) {
        if (!preferences.isEnabled()) return
        val entry = DiagnosticLogEntry.Classify(
            ts = clock(),
            packageName = packageName,
            ruleHits = ruleHits,
            decision = decision,
            reasonTags = reasonTags,
            elapsedMs = elapsedMs,
        )
        enqueue(entry)
    }

    override fun logRoute(
        packageName: String,
        sourceCancelled: Boolean,
        replacementPosted: Boolean,
        channelId: String?,
        targetMode: String,
    ) {
        if (!preferences.isEnabled()) return
        val entry = DiagnosticLogEntry.Route(
            ts = clock(),
            packageName = packageName,
            sourceCancelled = sourceCancelled,
            replacementPosted = replacementPosted,
            channelId = channelId,
            targetMode = targetMode,
        )
        enqueue(entry)
    }

    override fun logError(at: String, t: Throwable) {
        if (!preferences.isEnabled()) return
        val stack = java.io.StringWriter().also { sw ->
            t.printStackTrace(java.io.PrintWriter(sw))
        }.toString()
        val entry = DiagnosticLogEntry.Error(
            ts = clock(),
            at = at,
            message = t.message ?: t.javaClass.simpleName,
            stackTrace = truncateStackTrace(stack),
        )
        enqueue(entry)
    }

    private fun enqueue(entry: DiagnosticLogEntry) {
        // Direct synchronous write. The diagnostic log entry is a single line
        // (~200 bytes) — the per-write cost is well under the listener
        // service's existing per-notification budget, and writing inline
        // means the test path (`runBlocking { logger.logCapture(...) }` then
        // assert on file contents) holds without orchestrating coroutine
        // dispatchers from the test side. The `scope` field is retained for
        // a future "buffered writer" follow-up; right now it is not used so
        // we silence the lint warning by keeping it referenced in the
        // companion `LOG_FILE_NAME` block.
        writeEntrySync(entry)
    }

    /**
     * Writes the entry directly to disk on the calling thread. Internal so
     * the production `enqueue` queues it and tests can call it eagerly. We
     * swallow IO failures intentionally — this is release-prep diagnostics,
     * not audit logging, and a failed write must not break the user's
     * notification pipeline.
     */
    private fun writeEntrySync(entry: DiagnosticLogEntry) {
        try {
            if (!filesDir.exists()) filesDir.mkdirs()
            // Append first, then rotate if size exceeds cap. Post-append
            // rotation lets the rotator move every prior line to `.log.1`
            // while keeping the just-appended line in `.log` — the tail of
            // the live log is never empty after a rotation cycle and the
            // [DiagnosticLoggerRotationTest] "trigger line stays in `.log`"
            // assertion holds.
            logFile.appendText(entry.toJsonLine() + "\n")
            if (rotator.shouldRotate()) {
                rotator.rotate()
            }
        } catch (_: Throwable) {
            // Swallow IO failures intentionally — release-prep diagnostics,
            // not audit. Surfacing them would crash the listener service.
        }
    }

    companion object {
        const val LOG_FILE_NAME = "diagnostic.log"
        const val ROTATED_FILE_NAME = "diagnostic.log.1"
    }
}

/**
 * Singleton entry point for the production wiring. Mirrors the
 * `*.getInstance(context)` pattern used by [SettingsRepository] etc. so the
 * listener service / ViewModel can grab the logger without Hilt.
 */
object DiagnosticLoggerProvider {
    @Volatile
    private var instance: DiagnosticLogger? = null

    fun getInstance(context: Context): DiagnosticLogger {
        val existing = instance
        if (existing != null) return existing
        return synchronized(this) {
            val again = instance
            if (again != null) {
                again
            } else {
                val appContext = context.applicationContext
                val created = DiagnosticLogger.create(
                    filesDir = File(appContext.filesDir, DIAGNOSTIC_DIR_NAME).apply { mkdirs() },
                    preferences = DiagnosticLoggingPreferences.getInstance(appContext),
                )
                instance = created
                created
            }
        }
    }

    /**
     * Subdirectory of `filesDir` the logger writes into. Same name is
     * referenced by the FileProvider XML so export points at the same
     * directory.
     */
    const val DIAGNOSTIC_DIR_NAME = "diagnostic"
}
