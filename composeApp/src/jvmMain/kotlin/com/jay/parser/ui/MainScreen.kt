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
import com.jay.parser.parser.OrderEnricher
import com.jay.parser.parser.OrderFileParser
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.FileDialog
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.time.LocalDate
import javax.swing.SwingUtilities
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

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
    val message: String = "Choose purchase order files to begin."
)

@Composable
fun FrameWindowScope.MainScreen() {
    val queuedFiles = remember { mutableStateListOf<File>() }
    var parsedOrders by remember { mutableStateOf<List<ExportOrder>>(emptyList()) }
    var uiState by remember { mutableStateOf(UiState()) }
    var isDragOver by remember { mutableStateOf(false) }

    val fileParser = remember { OrderFileParser() }
    val enricher = remember { OrderEnricher() }
    val exporter = remember { SageCsvExporter() }
    val clipboard = LocalClipboardManager.current

    // ==================== MACOS RAINBOW CURSOR HELPERS ====================
    fun setBusyCursor() {
        SwingUtilities.invokeLater {
            println("ð [CURSOR] Setting busy cursor (rainbow beachball)")
            val waitCursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
            window.cursor = waitCursor
            window.rootPane?.cursor = waitCursor
            window.contentPane?.cursor = waitCursor
        }
    }

    fun resetCursor() {
        SwingUtilities.invokeLater {
            println("ð [CURSOR] Resetting to default cursor")
            val defaultCursor = Cursor.getDefaultCursor()
            window.cursor = defaultCursor
            window.rootPane?.cursor = defaultCursor
            window.contentPane?.cursor = defaultCursor
        }
    }
    // =====================================================================

    fun addFiles(files: List<File>) {
        val existing = queuedFiles.map { it.absolutePath }.toSet()
        val newFiles = files
            .filter {
                it.extension.equals("pdf", ignoreCase = true) ||
                        it.extension.equals("xlsx", ignoreCase = true)
            }
            .filterNot { it.absolutePath in existing }

        if (newFiles.isNotEmpty()) {
            queuedFiles.addAll(newFiles)
            parsedOrders = emptyList()
            uiState = UiState(
                status = "Ready",
                message = "${queuedFiles.size} file(s) queued."
            )
        } else if (files.isNotEmpty()) {
            uiState = UiState(
                status = "Ready",
                message = "No new supported files were added."
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
                            val selected = pickOrderFiles()
                            addFiles(selected)
                        },
                        onClear = {
                            queuedFiles.clear()
                            parsedOrders = emptyList()
                            uiState = UiState()
                        },
                        onParse = {
                            if (queuedFiles.isEmpty()) return@ActionRow

                            // IMMEDIATELY set the cursor on the UI thread
                            setBusyCursor()

                            // Run the heavy OCR/parsing on a background thread so the UI stays responsive
                            Thread {
                                val startTime = System.currentTimeMillis()

                                try {
                                    // All UI updates must go through invokeLater
                                    SwingUtilities.invokeLater {
                                        uiState = UiState(
                                            status = "Processing",
                                            message = "Starting parse of ${queuedFiles.size} file(s)..."
                                        )
                                    }

                                    val results = mutableListOf<ExportOrder>()

                                    queuedFiles.forEachIndexed { index, file ->
                                        val fileNum = index + 1

                                        SwingUtilities.invokeLater {
                                            uiState = UiState(
                                                status = "Processing",
                                                message = "Parsing file $fileNum/${queuedFiles.size}: ${file.name}..."
                                            )
                                        }

                                        val parsedOrdersList = fileParser.parse(file)

                                        if (parsedOrdersList.isEmpty()) {
                                            println("No orders found in file: ${file.name}")
                                        }

                                        parsedOrdersList.forEach { parsed ->
                                            try {
                                                val enriched = enricher.enrich(file.name, parsed)
                                                results.add(enriched)
                                            } catch (e: Exception) {
                                                println("Error enriching order from ${file.name}: ${e.message}")
                                                e.printStackTrace()
                                            }
                                        }
                                    }

                                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0

                                    SwingUtilities.invokeLater {
                                        parsedOrders = results
                                        uiState = UiState(
                                            status = "Complete",
                                            message = "Successfully parsed ${results.size} orders from ${queuedFiles.size} files in ${"%.2f".format(elapsedSeconds)}s."
                                        )
                                        resetCursor()
                                    }
                                } catch (e: Exception) {
                                    SwingUtilities.invokeLater {
                                        uiState = UiState(
                                            status = "Error",
                                            message = "Parsing failed: ${e.message ?: "Unknown error"}"
                                        )
                                        resetCursor()
                                    }
                                    e.printStackTrace()
                                }
                            }.start()
                        },
                        onExport = {
                            if (parsedOrders.isEmpty()) {
                                uiState = UiState(
                                    status = "Idle",
                                    message = "Parse at least one file before exporting."
                                )
                            } else {
                                try {
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
                                            "Choose purchase order files to begin."
                                        } else {
                                            "${queuedFiles.size} file(s) queued."
                                        }
                                    )
                                }
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            ResultsPanel(
                                orders = parsedOrders,
                                onCopyOrder = { order ->
                                    clipboard.setText(AnnotatedString(buildOrderDebugText(order)))
                                    uiState = UiState(
                                        status = "Copied",
                                        message = "Parsed order copied to clipboard."
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// âââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ
// Everything below this line is 100% your original code (no changes)
// âââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââââ

@Composable
private fun HeaderSection() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "PO Parser",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Import purchase order PDFs or XLSX files, extract customer and item data, and export Sage-ready CSV.",
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
                            "Drop PDF or XLSX files here"
                        } else {
                            "Use Choose Files or drag files here"
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
                        text = "PDF and XLSX purchase orders supported",
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
            Text("Choose Files")
        }

        OutlinedButton(onClick = onClear) {
            Text("Clear Queue")
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(onClick = onParse) {
            Text("Parse Files")
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
private fun ResultsPanel(
    orders: List<ExportOrder>,
    onCopyOrder: (ExportOrder) -> Unit
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
                        ResultOrderCard(
                            index = index,
                            order = order,
                            onCopy = { onCopyOrder(order) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultOrderCard(
    index: Int,
    order: ExportOrder,
    onCopy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11161D)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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

            OutlinedButton(onClick = onCopy) {
                Text("Copy Parsed Order")
            }
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

private fun FrameWindowScope.pickOrderFiles(): List<File> {
    val dialog = FileDialog(window, "Choose Purchase Order Files", FileDialog.LOAD).apply {
        isMultipleMode = true
        file = "*.*"
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

private fun buildOrderDebugText(order: ExportOrder): String {
    return buildString {
        appendLine("File: ${order.sourceFilename}")
        appendLine("Customer: ${order.customer?.name ?: order.customerNameRaw.orEmpty()}")
        appendLine("Order #: ${order.orderNumber}")
        appendLine("Ship To: ${order.shipToCustomer.orEmpty()}")
        appendLine("Address 1: ${order.addressLine1.orEmpty()}")
        appendLine("Address 2: ${order.addressLine2.orEmpty()}")
        appendLine("City: ${order.city.orEmpty()}")
        appendLine("State: ${order.state.orEmpty()}")
        appendLine("Zip: ${order.zip.orEmpty()}")
        appendLine("Terms: ${order.termsResolved.orEmpty()}")
        appendLine("Lines: ${order.lines.size}")
        appendLine()

        order.lines.forEachIndexed { index, line ->
            appendLine("Line ${index + 1}")
            appendLine("  sku               = ${line.sku}")
            appendLine("  description       = ${line.description}")
            appendLine("  quantityRaw       = ${line.quantityRaw}")
            appendLine("  quantityForExport = ${line.quantityForExport}")
            appendLine("  unitPriceRef      = ${line.unitPriceReference}")
            appendLine("  unitPriceResolved = ${line.unitPriceResolved}")
            appendLine("  glAccount         = ${line.glAccount}")
            appendLine()
        }
    }
}