package dev.botak.client

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit

@Composable
@Preview
fun SystemTrayWindow(onClose: () -> Unit) {
    Window(onCloseRequest = onClose) {
        Text("Settings window")
    }
}
