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
import dev.botak.core.services.TTSService
import org.slf4j.LoggerFactory
import java.awt.CheckboxMenuItem
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.windows.SystemTrays")

@Composable
@Preview
fun SystemTrays(
    onAppEnabled: () -> Unit,
    onAppDisabled: () -> Unit,
    exitApplication: () -> Unit,
    ttsService: TTSService,
) {
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (SystemTray.isSupported()) {
            LOGGER.debug("System tray supported. Use system tray")
            useSystemTray(onSettingsItem = {
                showSettings = true
            }, onExitItem = { exitApplication() }, onAppEnabled = onAppEnabled, onAppDisabled = onAppDisabled)
        }
    }

    SettingsWindow(
        ttsService = ttsService,
        visible = showSettings,
        onClose = { showSettings = false },
    )
}

private fun useSystemTray(
    onSettingsItem: () -> Unit,
    onExitItem: () -> Unit,
    onAppEnabled: () -> Unit,
    onAppDisabled: () -> Unit,
) {
    val tray = SystemTray.getSystemTray()
    val image = Toolkit.getDefaultToolkit().getImage("icon.png")

    val popup = PopupMenu()

    val settingsItem = MenuItem("Settings")
    settingsItem.addActionListener { onSettingsItem() }
    popup.add(settingsItem)

    val enabledItem = CheckboxMenuItem("Enabled", true)
    enabledItem.addItemListener {
        LOGGER.debug("Enabled toggle: ${enabledItem.state}")
        if (enabledItem.state) {
            onAppEnabled()
        } else {
            onAppDisabled()
        }
    }
    popup.add(enabledItem)

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
