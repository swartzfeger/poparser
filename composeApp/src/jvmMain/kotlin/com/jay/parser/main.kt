package com.jay.parser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.jay.parser.ui.MainScreen

private const val APP_NAME = "PO Parser"
private const val APP_VERSION = "1.0.3"
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

        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen()

                    Text(
                        text = "About…",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clickable { showAbout = true },
                        color = MaterialTheme.colorScheme.primary
                    )

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
                }
            }
        }
    }
}