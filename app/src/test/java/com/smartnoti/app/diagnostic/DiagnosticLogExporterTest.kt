package com.smartnoti.app.diagnostic

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md` Task 1.
 *
 * Failing-test gate (P1 release-prep) for [DiagnosticLogExporter]:
 *
 *  - When both `.log` and `.log.1` exist, the exporter builds an
 *    `ACTION_SEND_MULTIPLE` intent that carries both files as
 *    `EXTRA_STREAM` URIs.
 *  - When only `.log` exists, the exporter falls back to `ACTION_SEND` with a
 *    single `EXTRA_STREAM` URI.
 *  - URI authority must be `${applicationId}.diagnosticfileprovider` (the
 *    plan reserves a dedicated authority so it cannot collide with future
 *    user-content sharing).
 *  - `Intent.type` must be `text/plain`.
 *  - `Intent.flags` must include `FLAG_GRANT_READ_URI_PERMISSION` so the
 *    receiving app can read the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiagnosticLogExporterTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var diagnosticDir: File
    private lateinit var exporter: DiagnosticLogExporter

    @Before
    fun setUp() {
        // The exporter resolves files from the directory passed in. Using a
        // TemporaryFolder keeps the test from touching the real app filesDir.
        diagnosticDir = tempFolder.newFolder("diagnostic")
        exporter = DiagnosticLogExporter(diagnosticDir)
    }

    @Test
    fun export_creates_share_intent_with_file_provider_uri_for_two_files() {
        File(diagnosticDir, "diagnostic.log").writeText("""{"stage":"capture"}""" + "\n")
        File(diagnosticDir, "diagnostic.log.1").writeText("""{"stage":"capture"}""" + "\n")

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = exporter.buildShareIntent(context)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals("text/plain", intent.type)
        val uris = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        assertNotNull(uris)
        assertEquals(2, uris!!.size)
        for (uri in uris) {
            assertEquals("content", uri.scheme)
            assertEquals(
                "${context.packageName}.diagnosticfileprovider",
                uri.authority,
            )
        }
        assertTrue(
            "intent must grant read permission to the receiving app",
            (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0,
        )
    }

    @Test
    fun export_uses_action_send_when_only_one_file_exists() {
        File(diagnosticDir, "diagnostic.log").writeText("""{"stage":"capture"}""" + "\n")

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = exporter.buildShareIntent(context)

        assertNotNull(intent)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
        assertNotNull(uri)
        assertEquals("content", uri!!.scheme)
        assertEquals(
            "${context.packageName}.diagnosticfileprovider",
            uri.authority,
        )
    }
}
