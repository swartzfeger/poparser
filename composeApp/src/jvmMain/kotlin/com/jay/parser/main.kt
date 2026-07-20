package com.jay.parser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jay.parser.masterdata.MasterDataImportResult
import com.jay.parser.masterdata.MasterDataStore
import com.jay.parser.ui.MainScreen
import java.awt.FileDialog
import java.io.File
import javax.swing.SwingUtilities

private const val APP_NAME = "PO Parser"
private const val APP_VERSION = "1.5.2"
private const val APP_VENDOR = "Jay Swartzfeger"
private const val APP_COPYRIGHT = "© 2026 Precision Laboratories"

private fun isMacOs(): Boolean =
    System.getProperty("os.name").lowercase().contains("mac")

fun main() = application {
    val defaultWidth = if (isMacOs()) 1400.dp else 1400.dp
    val defaultHeight = if (isMacOs()) 900.dp else 900.dp

    val windowState = rememberWindowState(
        width = defaultWidth,
        height = defaultHeight,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = APP_NAME,
        state = windowState
    ) {
        var showAbout by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var showMasterList by remember { mutableStateOf(false) }
        var noShipVia by remember { mutableStateOf(false) }
        var noShipTo by remember { mutableStateOf(false) }
        var masterListMessage by remember { mutableStateOf("") }
        var masterListImportResult by remember { mutableStateOf<MasterDataImportResult?>(null) }
        var masterListIsImporting by remember { mutableStateOf(false) }

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        noShipVia = noShipVia,
                        noShipTo = noShipTo
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "About…",
                            modifier = Modifier.clickable { showAbout = true },
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "⚙ Settings",
                            modifier = Modifier.clickable { showSettings = true },
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "⬆ Update Master List",
                            modifier = Modifier.clickable {
                                masterListMessage = ""
                                masterListImportResult = null
                                showMasterList = true
                            },
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (showAbout) {
                        AlertDialog(
                            onDismissRequest = { showAbout = false },
                            title = { Text("About $APP_NAME") },
                            text = {
                                Text(
                                    buildString {
                                        appendLine(APP_NAME)
                                        appendLine("Version $APP_VERSION")
                                        appendLine()
                                        appendLine("Desktop tool for parsing purchase orders")
                                        appendLine("and extracting structured order data.")
                                        appendLine()
                                        appendLine("Built with Kotlin + Compose Multiplatform")
                                        appendLine()
                                        appendLine(APP_VENDOR)
                                        append(APP_COPYRIGHT)
                                    }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { showAbout = false }) {
                                    Text("OK")
                                }
                            }
                        )
                    }

                    if (showSettings) {
                        AlertDialog(
                            onDismissRequest = { showSettings = false },
                            title = { Text("Settings") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(
                                        text = "Export options",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Switch(
                                            checked = noShipVia,
                                            onCheckedChange = { noShipVia = it }
                                        )
                                        Column {
                                            Text("No Ship Via")
                                            Text(
                                                text = "Leave Ship Via blank in the CSV export.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Switch(
                                            checked = noShipTo,
                                            onCheckedChange = { noShipTo = it }
                                        )
                                        Column {
                                            Text("No Ship To")
                                            Text(
                                                text = "Leave Ship To name and address fields blank in the CSV export.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSettings = false }) {
                                    Text("OK")
                                }
                            }
                        )
                    }

                    if (showMasterList) {
                        val metadata = MasterDataStore.metadata()

                        AlertDialog(
                            onDismissRequest = {
                                if (!masterListIsImporting) showMasterList = false
                            },
                            title = { Text("Update Master List") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = if (metadata == null) {
                                            "Using bundled master data."
                                        } else {
                                            "Using imported master data from ${metadata.sourceFilename}."
                                        },
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    if (metadata != null) {
                                        Text(
                                            text = buildString {
                                                append("Imported: ${metadata.importedAt}\n")
                                                append("Customers: ${metadata.customerCount}\n")
                                                append("Items: ${metadata.descriptionCount} descriptions, ${metadata.pricedItemCount} priced\n")
                                                append("GL accounts: ${metadata.glAccountCount}\n")
                                                append("Qty discount rules: ${metadata.qtyDiscountRuleCount}")
                                            },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (masterListMessage.isNotBlank()) {
                                        Text(
                                            text = masterListMessage,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    masterListImportResult?.let { result ->
                                        Text(
                                            text = buildString {
                                                appendLine("Imported ${result.metadata.sourceFilename}.")
                                                appendLine("${result.metadata.customerCount} customers")
                                                appendLine("${result.metadata.descriptionCount} item descriptions")
                                                appendLine("${result.metadata.pricedItemCount} priced items")
                                                appendLine("${result.metadata.glAccountCount} GL accounts")
                                                append("${result.metadata.qtyDiscountRuleCount} quantity discount rules")
                                            },
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (result.warnings.isNotEmpty()) {
                                            Text(
                                                text = "Warnings:\n" + result.warnings.take(5).joinToString("\n"),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Data folder: ${MasterDataStore.dataDirectoryPath()}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    enabled = !masterListIsImporting,
                                    onClick = {
                                        val selectedFile = pickMasterListFile()
                                        if (selectedFile != null) {
                                            masterListIsImporting = true
                                            masterListMessage = "Importing ${selectedFile.name}..."
                                            masterListImportResult = null

                                            Thread {
                                                try {
                                                    val result = MasterDataStore.importMasterList(selectedFile)
                                                    SwingUtilities.invokeLater {
                                                        masterListImportResult = result
                                                        masterListMessage = "Master list imported successfully."
                                                        masterListIsImporting = false
                                                    }
                                                } catch (e: Exception) {
                                                    SwingUtilities.invokeLater {
                                                        masterListMessage = "Import failed: ${e.message ?: "Unknown error"}"
                                                        masterListIsImporting = false
                                                    }
                                                }
                                            }.start()
                                        }
                                    }
                                ) {
                                    Text(if (masterListIsImporting) "Importing..." else "Choose XLSX")
                                }
                            },
                            dismissButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(
                                        enabled = !masterListIsImporting,
                                        onClick = {
                                            try {
                                                MasterDataStore.restoreBundledDefaults()
                                                masterListImportResult = null
                                                masterListMessage = "Restored bundled master data."
                                            } catch (e: Exception) {
                                                masterListMessage = "Restore failed: ${e.message ?: "Unknown error"}"
                                            }
                                        }
                                    ) {
                                        Text("Restore Defaults")
                                    }

                                    TextButton(
                                        enabled = !masterListIsImporting,
                                        onClick = { showMasterList = false }
                                    ) {
                                        Text("Close")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun pickMasterListFile(): File? {
    val dialog = FileDialog(null as java.awt.Frame?, "Choose Master List XLSX", FileDialog.LOAD)
    dialog.file = "*.xlsx"
    dialog.isVisible = true

    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null

    return File(directory, file)
}
