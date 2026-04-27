package com.smartnoti.app.diagnostic

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Plan `docs/plans/2026-04-27-fix-issue-480-diagnostic-log-file-export.md`
 * Task 2.
 *
 * Builds the `ACTION_SEND` / `ACTION_SEND_MULTIPLE` intent the Settings
 * "로그 export" button fires. Resolves the live and rotated log files
 * inside [diagnosticDir] and wraps each in a `FileProvider` content URI under
 * the dedicated `${applicationId}.diagnosticfileprovider` authority — the
 * Task 1 contract test asserts the authority literal so the FileProvider
 * registration in `AndroidManifest.xml` and the path xml under
 * `res/xml/diagnostic_file_paths.xml` must match.
 *
 * The exporter is purely a builder — it returns the intent without firing
 * it. Callers (`SettingsViewModel`-style wiring) are responsible for
 * `context.startActivity(Intent.createChooser(intent, ...))` so a unit test
 * can assert intent shape without touching the Activity stack.
 */
class DiagnosticLogExporter(private val diagnosticDir: File) {

    fun buildShareIntent(context: Context): Intent {
        val files = listOfNotNull(
            File(diagnosticDir, "diagnostic.log").takeIf { it.exists() && it.length() > 0L },
            File(diagnosticDir, "diagnostic.log.1").takeIf { it.exists() && it.length() > 0L },
        )
        val authority = "${context.packageName}.diagnosticfileprovider"
        val uris = files.map { file ->
            FileProvider.getUriForFile(context, authority, file)
        }
        return when (uris.size) {
            0, 1 -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (uris.isNotEmpty()) {
                    putExtra(Intent.EXTRA_STREAM, uris.single())
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
