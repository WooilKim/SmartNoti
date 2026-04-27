package com.smartnoti.app.diagnostic

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    dispatcher: CoroutineDispatcher,
    sizeCapBytes: Long,
    retentionMillis: Long,
) : DiagnosticLogger {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
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
        scope.launch {
            runCatching {
                if (!filesDir.exists()) filesDir.mkdirs()
                if (rotator.shouldRotate()) {
                    rotator.rotate()
                }
                logFile.appendText(entry.toJsonLine() + "\n")
            }
            // We swallow IO failures intentionally: this is release-prep
            // diagnostics, not audit logging. A failed write must not break
            // the user's notification pipeline.
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
