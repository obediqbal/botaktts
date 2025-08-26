package dev.botak.client.windows

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import org.slf4j.LoggerFactory
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.windows.SettingsWindow")

@Composable
@Preview
fun SettingsWindow(exitApplication: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = {
            showSettings = false
        },
        title = "Botak TTS Settings",
        visible = showSettings,
        enabled = showSettings,
    ) {
        LOGGER.debug("Composing Settings Window...")

        LaunchedEffect(Unit) {
            if (SystemTray.isSupported()) {
                LOGGER.debug("System tray supported. Use system tray")
                useSystemTray(onSettingsItem = { showSettings = true }, onExitItem = { exitApplication() })
            }
        }

        Text("Settings window")

        LOGGER.debug("Composed Settings Window")
    }
}

private fun useSystemTray(
    onSettingsItem: () -> Unit,
    onExitItem: () -> Unit,
) {
    val tray = SystemTray.getSystemTray()
    val image = Toolkit.getDefaultToolkit().getImage("icon.png")

    val popup = PopupMenu()

    val settingsItem = MenuItem("Settings")
    settingsItem.addActionListener { onSettingsItem() }
    popup.add(settingsItem)

    val exitItem = MenuItem("Exit")
    exitItem.addActionListener { onExitItem() }
    popup.add(exitItem)

    val trayIcon =
        TrayIcon(image, "Botak TTS", popup).apply {
            isImageAutoSize = true
        }

    try {
        tray.add(trayIcon)
    } catch (e: Exception) {
        LOGGER.error("Error while adding system trays: ${e.message}")
    }
}
