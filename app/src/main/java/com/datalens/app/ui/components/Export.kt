package com.datalens.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.datalens.app.domain.model.ReportData
import com.datalens.app.domain.usecase.ReportFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Local export (Storage Access Framework) and sharing (standard Android share
 * sheet) of usage reports. Reports never leave the device unless the user
 * explicitly exports or shares them.
 */
class ReportActions internal constructor(
    private val appContext: Context,
    private val showMessage: (String) -> Unit,
) {
    internal var pending: ReportData? = null
        private set

    internal var csvLauncher: ActivityResultLauncher<String>? = null
    internal var jsonLauncher: ActivityResultLauncher<String>? = null

    internal fun attach(
        csv: ActivityResultLauncher<String>,
        json: ActivityResultLauncher<String>,
    ) {
        csvLauncher = csv
        jsonLauncher = json
    }

    /** Saves a report to a location the user picks (Storage Access Framework). */
    fun saveToDisk(report: ReportData) {
        pending = report
        val launcher = if (report.mimeType == ReportFormat.CSV.mimeType) csvLauncher else jsonLauncher
        if (launcher == null) {
            pending = null
            showMessage("Export is not ready yet — please try again.")
            return
        }
        launcher.launch(report.suggestedFileName)
    }

    /** Writes the report to a cache file and opens the Android share sheet. */
    fun share(report: ReportData) {
        try {
            val dir = File(appContext.cacheDir, "reports").apply { mkdirs() }
            val file = File(dir, report.suggestedFileName)
            file.writeBytes(report.bytes)
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = report.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, report.suggestedFileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(Intent.createChooser(intent, "Share usage report"))
        } catch (_: Exception) {
            showMessage("Could not share the report.")
        }
    }

    internal fun handleSaveResult(uri: Uri?, context: Context) {
        val report = pending
        pending = null
        if (uri == null || report == null) return
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(report.bytes)
                output.flush()
            }
            showMessage("Report saved: ${report.suggestedFileName}")
        } catch (_: Exception) {
            showMessage("Could not save the report.")
        }
    }
}

/** Creates a [ReportActions] wired to SAF launchers and a snackbar. */
@Composable
fun rememberReportExporter(
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
): ReportActions {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)

    val actions = remember {
        ReportActions(
            appContext = context.applicationContext,
            showMessage = { message ->
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            },
        )
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportFormat.CSV.mimeType),
    ) { uri -> actions.handleSaveResult(uri, currentContext) }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ReportFormat.JSON.mimeType),
    ) { uri -> actions.handleSaveResult(uri, currentContext) }

    actions.attach(csvLauncher, jsonLauncher)
    return actions
}
