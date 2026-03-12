package com.jay.parser

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jay.parser.ui.MainScreen

fun main() = application {
    val windowState = rememberWindowState(
        width = 1400.dp,
        height = 800.dp,
        position = WindowPosition.Aligned(Alignment.Center)
    )

    Window(
        onCloseRequest = ::exitApplication,
        title = "PO Parser",
        state = windowState
    ) {
        MainScreen()
    }
}