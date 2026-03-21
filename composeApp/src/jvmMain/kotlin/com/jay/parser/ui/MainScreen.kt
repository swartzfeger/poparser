package com.jay.parser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import com.jay.parser.export.SageCsvExporter
import com.jay.parser.models.ExportOrder
import com.jay.parser.pdf.PdfFieldParser
import com.jay.parser.pdf.PdfTextExtractor
import com.jay.parser.parser.OrderEnricher
import java.awt.Component
import java.awt.Container
import java.awt.FileDialog
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.time.LocalDate
import java.util.Locale
import javax.swing.SwingUtilities

private val AppColors = darkColorScheme(
    background = Color(0xFF0E1116),
    surface = Color(0xFF171B22),
    primary = Color(0xFF6EA8FF),
    secondary = Color(0xFF8B9BB4),
    onBackground = Color(0xFFE8EDF5),
    onSurface = Color(0xFFE8EDF5)
)

data class UiState(
    val status: String = "Idle",
    val message: String = "Choose purchase order PDFs to begin."
)

@Composable
fun FrameWindowScope.MainScreen() {
    val queuedFiles = remember { mutableStateListOf<File>() }
    var parsedOrders by remember { mutableStateOf<List<ExportOrder>>(emptyList()) }
    var uiState by remember { mutableStateOf(UiState()) }
    var isDragOver by remember { mutableStateOf(false) }

    val extractor = remember { PdfTextExtractor() }
    val parser = remember { PdfFieldParser() }
    val enricher = remember { OrderEnricher() }
    val exporter = remember { SageCsvExporter() }

    fun addFiles(files: List<File>) {
        val existing = queuedFiles.map { it.absolutePath }.toSet()
        val newFiles = files
            .filter { it.extension.equals("pdf", ignoreCase = true) }
            .filterNot { it.absolutePath in existing }

        if (newFiles.isNotEmpty()) {
            queuedFiles.addAll(newFiles)
            parsedOrders = emptyList()
            uiState = UiState(
                status = "Ready",
                message = "${queuedFiles.size} PDF file(s) queued."
            )
        } else if (files.isNotEmpty()) {
            uiState = UiState(
                status = "Ready",
                message = "No new PDF files were added."
            )
        }
    }

    DisposableEffect(window) {
        val listener = object : DropTargetAdapter() {
            private fun supportsFiles(flavors: Array<DataFlavor>): Boolean {
                return flavors.any { it == DataFlavor.javaFileListFlavor }
            }

            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (supportsFiles(dtde.currentDataFlavors)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    isDragOver = true
                } else {
                    dtde.rejectDrag()
                    isDragOver = false
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent) {
                if (supportsFiles(dtde.currentDataFlavors)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                    isDragOver = true
                } else {
                    dtde.rejectDrag()
                    isDragOver = false
                }
            }

            override fun dragExit(dte: java.awt.dnd.DropTargetEvent) {
                isDragOver = false
            }

            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    if (!supportsFiles(dtde.currentDataFlavors)) {
                        dtde.rejectDrop()
                        isDragOver = false
                        return
                    }

                    dtde.acceptDrop(DnDConstants.ACTION_COPY)

                    @Suppress("UNCHECKED_CAST")
                    val droppedFiles = dtde.transferable
                        .getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                        ?: emptyList()

                    addFiles(droppedFiles)
                    dtde.dropComplete(true)
                } catch (e: Exception) {
                    uiState = UiState(
                        status = "Error",
                        message = "Drop failed: ${e.message ?: "Unknown error"}"
                    )
                    dtde.dropComplete(false)
                } finally {
                    isDragOver = false
                }
            }
        }

        val installedTargets = mutableListOf<Pair<Component, DropTarget?>>()

        fun installDropTarget(component: Component) {
            installedTargets += component to component.dropTarget
            component.dropTarget = DropTarget(component, DnDConstants.ACTION_COPY, listener, true)
        }

        fun installRecursively(component: Component) {
            installDropTarget(component)
            if (component is Container) {
                component.components.forEach { child ->
                    installRecursively(child)
                }
            }
        }

        SwingUtilities.invokeLater {
            installRecursively(window)
            installRecursively(window.rootPane)
            installRecursively(window.rootPane.contentPane)
        }

        onDispose {
            installedTargets.forEach { (component, previous) ->
                component.dropTarget = previous
            }
        }
    }

    MaterialTheme(colorScheme = AppColors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeaderSection()

                    FilePickerBanner(isDragOver = isDragOver)

                    ActionRow(
                        onChooseFiles = {
                            val selected = pickPdfFiles()
                            addFiles(selected)
                        },
                        onClear = {
                            queuedFiles.clear()
                            parsedOrders = emptyList()
                            uiState = UiState()
                        },
                        onParse = {
                            if (queuedFiles.isEmpty()) {
                                uiState = UiState(
                                    status = "Idle",
                                    message = "Add at least one PDF before parsing."
                                )
                            } else {
                                try {
                                    uiState = UiState(
                                        status = "Processing",
                                        message = "Parsing ${queuedFiles.size} PDF file(s)..."
                                    )

                                    val startNanos = System.nanoTime()

                                    val orders = queuedFiles.map { file ->
                                        val parsed = parser.parse(extractor.extractLines(file))
                                        enricher.enrich(file.name, parsed)
                                    }

                                    val elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0
                                    val elapsedText = String.format(Locale.US, "%.1f", elapsedSeconds)

                                    parsedOrders = orders

                                    val totalLines = orders.sumOf { it.lines.size }
                                    uiState = UiState(
                                        status = "Parsed",
                                        message = "Parsed ${orders.size} order(s), $totalLines line(s) total, $elapsedText seconds processing time"
                                    )
                                } catch (e: Exception) {
                                    uiState = UiState(
                                        status = "Error",
                                        message = "Parse failed: ${e.message ?: "Unknown error"}"
                                    )
                                }
                            }
                        },
                        onExport = {
                            if (parsedOrders.isEmpty()) {
                                uiState = UiState(
                                    status = "Idle",
                                    message = "Parse at least one PDF before exporting."
                                )
                            } else {
                                try {
                                    val offending = parsedOrders.firstOrNull { order ->
                                        val shipTo = order.shipToCustomer.orEmpty().uppercase()
                                        val addr1 = order.addressLine1.orEmpty().uppercase()
                                        val addr2 = order.addressLine2.orEmpty().uppercase()
                                        val city = order.city.orEmpty().uppercase()
                                        val zip = order.zip.orEmpty().uppercase()

                                        shipTo.contains("PRECISION") ||
                                                addr1.contains("AIRPORT") ||
                                                addr2.contains("AIRPORT") ||
                                                city.contains("COTTONWOOD") ||
                                                zip.contains("86326")
                                    }

                                    if (offending != null) {
                                        uiState = UiState(
                                            status = "Error",
                                            message = "Export blocked: shipTo='${offending.shipToCustomer}', addr1='${offending.addressLine1}', addr2='${offending.addressLine2}', city='${offending.city}', zip='${offending.zip}'"
                                        )
                                    } else {
                                        val outputFile = pickSaveCsvFile()
                                        if (outputFile != null) {
                                            exporter.export(
                                                orders = parsedOrders,
                                                outputFile = outputFile,
                                                orderDate = LocalDate.now()
                                            )

                                            uiState = UiState(
                                                status = "Exported",
                                                message = "CSV written to: ${outputFile.absolutePath}"
                                            )
                                        } else {
                                            uiState = UiState(
                                                status = "Ready",
                                                message = "Export canceled."
                                            )
                                        }
                                    }
                                } catch (e: Exception) {
                                    uiState = UiState(
                                        status = "Error",
                                        message = "Export failed: ${e.message ?: "Unknown error"}"
                                    )
                                }
                            }
                        }
                    )

                    StatusPanel(uiState = uiState)

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            QueuePanel(
                                files = queuedFiles,
                                onRemove = { index ->
                                    queuedFiles.removeAt(index)
                                    parsedOrders = emptyList()
                                    uiState = UiState(
                                        status = if (queuedFiles.isEmpty()) "Idle" else "Ready",
                                        message = if (queuedFiles.isEmpty()) {
                                            "Choose purchase order PDFs to begin."
                                        } else {
                                            "${queuedFiles.size} PDF file(s) queued."
                                        }
                                    )
                                }
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            ResultsPanel(parsedOrders)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "PO PDF Parser",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Import purchase order PDFs, extract customer and item data, and export Sage-ready CSV.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun FilePickerBanner(isDragOver: Boolean) {
    val backgroundColor = if (isDragOver) Color(0xFF1B2A3C) else Color(0xFF121720)
    val borderColor = if (isDragOver) Color(0xFF6EA8FF) else Color(0xFF3A4558)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            border = BorderStroke(2.dp, borderColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isDragOver) {
                            "Drop PDF files here"
                        } else {
                            "Use Choose PDFs or drag files here"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isDragOver) {
                            "Release to add files to the queue"
                        } else {
                            "Drag and drop is enabled"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Single-page and multi-page purchase orders supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    onChooseFiles: () -> Unit,
    onClear: () -> Unit,
    onParse: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(onClick = onChooseFiles) {
            Text("Choose PDFs")
        }

        OutlinedButton(onClick = onClear) {
            Text("Clear Queue")
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(onClick = onParse) {
            Text("Parse PDFs")
        }

        Button(onClick = onExport) {
            Text("Export CSV")
        }
    }
}

@Composable
private fun StatusPanel(uiState: UiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B22)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Status: ${uiState.status}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = uiState.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun QueuePanel(
    files: List<File>,
    onRemove: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B22)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Queued Files (${files.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider(color = Color(0xFF2B3442))

            if (files.isEmpty()) {
                Text(
                    text = "No files queued.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(files) { index, file ->
                        FileRow(
                            index = index,
                            file = file,
                            onRemove = { onRemove(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsPanel(orders: List<ExportOrder>) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF171B22)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Parsed Orders (${orders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider(color = Color(0xFF2B3442))

            if (orders.isEmpty()) {
                Text(
                    text = "No parsed results yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(orders) { index, order ->
                        ResultOrderCard(index = index, order = order)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultOrderCard(
    index: Int,
    order: ExportOrder
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11161D)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${index + 1}. ${order.sourceFilename}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "Customer: ${order.customer?.name ?: order.customerNameRaw.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Order #: ${order.orderNumber}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Ship To Name: ${order.shipToCustomer.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Addr 1: ${order.addressLine1.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = "Addr 2: ${order.addressLine2.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = "City/State/Zip: ${listOfNotNull(order.city, order.state, order.zip).joinToString(" ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Text(
                text = "Lines: ${order.lines.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun FileRow(
    index: Int,
    file: File,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11161D)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${file.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(0.dp))
                Text(
                    text = file.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}

private fun FrameWindowScope.pickPdfFiles(): List<File> {
    val dialog = FileDialog(window, "Choose Purchase Order PDFs", FileDialog.LOAD).apply {
        isMultipleMode = true
        file = "*.pdf"
        isVisible = true
    }

    return dialog.files?.toList().orEmpty()
}

private fun FrameWindowScope.pickSaveCsvFile(): File? {
    val dialog = FileDialog(window, "Save Sage CSV", FileDialog.SAVE).apply {
        file = "sage-export.csv"
        isVisible = true
    }

    val directory = dialog.directory ?: return null
    val filename = dialog.file ?: return null

    val finalName = if (filename.endsWith(".csv", ignoreCase = true)) filename else "$filename.csv"
    return File(directory, finalName)
}