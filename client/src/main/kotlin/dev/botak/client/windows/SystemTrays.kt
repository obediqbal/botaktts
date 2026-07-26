package dev.botak.client.windows

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.ConfigService
import dev.botak.core.services.TTSService
import org.slf4j.LoggerFactory
import java.awt.CheckboxMenuItem
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.windows.SystemTrays")

/**
 * Installs the BotakTTS system tray icon and renders the settings window on demand.
 *
 * On first composition, if the platform supports a system tray, a tray icon is created with a
 * popup menu providing access to Settings, Check for updates, an Enabled toggle, Show Subtitles,
 * and Exit. The settings window is shown whenever the user selects the Settings menu item.
 *
 * The "Show Subtitles" checkbox tracks [subtitleWindowEnabled] even when the subtitle window is
 * closed from its title bar, so the tray stays in sync with Compose state.
 *
 * @param onAppEnabled Called when the tray's Enabled checkbox is checked.
 * @param onAppDisabled Called when the tray's Enabled checkbox is unchecked.
 * @param subtitleWindowEnabled Checked state of the "Show Subtitles" toggle (from Compose / config).
 * @param onSubtitleWindowToggled Called with the new checked state when the "Show Subtitles" toggle changes.
 * @param onCheckForUpdates Called when the user selects "Check for updates…".
 * @param exitApplication Called when the user selects Exit.
 * @param ttsService Used to populate and drive the settings window.
 * @param audioStreamService Used to populate and drive the settings window.
 */
@Composable
@Preview
fun SystemTrays(
    onAppEnabled: () -> Unit,
    onAppDisabled: () -> Unit,
    subtitleWindowEnabled: Boolean,
    onSubtitleWindowToggled: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    exitApplication: () -> Unit,
    ttsService: TTSService,
    audioStreamService: AudioStreamService,
) {
    var showSettings by remember { mutableStateOf(false) }
    // Held so LaunchedEffect can sync checkbox when the subtitle window is closed via X.
    val subtitleMenuItem = remember { arrayOfNulls<CheckboxMenuItem>(1) }

    LaunchedEffect(Unit) {
        if (SystemTray.isSupported()) {
            LOGGER.debug("System tray supported. Use system tray")
            useSystemTray(
                onSettingsItem = { showSettings = true },
                onCheckForUpdatesItem = onCheckForUpdates,
                onExitItem = { exitApplication() },
                onAppEnabled = onAppEnabled,
                onAppDisabled = onAppDisabled,
                subtitleWindowEnabled = subtitleWindowEnabled,
                onSubtitleWindowToggled = onSubtitleWindowToggled,
                subtitleMenuItemOut = subtitleMenuItem,
            )
        }
    }

    // Keep AWT checkbox aligned with Compose (tray click or window close).
    LaunchedEffect(subtitleWindowEnabled) {
        val item = subtitleMenuItem[0] ?: return@LaunchedEffect
        if (item.state != subtitleWindowEnabled) {
            item.state = subtitleWindowEnabled
        }
    }

    SettingsWindow(
        ttsService = ttsService,
        audioStreamService = audioStreamService,
        visible = showSettings,
        onClose = { showSettings = false },
    )
}

/**
 * Creates and adds the system tray icon with its popup menu.
 *
 * @param onSettingsItem Called when the Settings menu item is selected.
 * @param onCheckForUpdatesItem Called when the "Check for updates…" item is selected.
 * @param onExitItem Called when the Exit menu item is selected.
 * @param onAppEnabled Called when the Enabled checkbox is checked.
 * @param onAppDisabled Called when the Enabled checkbox is unchecked.
 * @param subtitleWindowEnabled Initial checked state of the "Show Subtitles" checkbox.
 * @param onSubtitleWindowToggled Called with the new checked state when "Show Subtitles" is toggled.
 * @param subtitleMenuItemOut Output slot set to the live "Show Subtitles" checkbox so Compose can sync its state.
 */
private fun useSystemTray(
    onSettingsItem: () -> Unit,
    onCheckForUpdatesItem: () -> Unit,
    onExitItem: () -> Unit,
    onAppEnabled: () -> Unit,
    onAppDisabled: () -> Unit,
    subtitleWindowEnabled: Boolean,
    onSubtitleWindowToggled: (Boolean) -> Unit,
    subtitleMenuItemOut: Array<CheckboxMenuItem?>,
) {
    val tray = SystemTray.getSystemTray()
    val image = Toolkit.getDefaultToolkit().getImage("icon.png")

    val popup = PopupMenu()

    val settingsItem = MenuItem("Settings")
    settingsItem.addActionListener { onSettingsItem() }
    popup.add(settingsItem)

    val updatesItem = MenuItem("Check for updates…")
    updatesItem.addActionListener { onCheckForUpdatesItem() }
    popup.add(updatesItem)

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

    val subtitleItem = CheckboxMenuItem("Show Subtitles", subtitleWindowEnabled)
    subtitleItem.addItemListener {
        val checked = subtitleItem.state
        LOGGER.debug("Show Subtitles toggle: $checked")
        ConfigService.userSettings.subtitleWindowEnabled = checked
        ConfigService.saveUserSettings()
        onSubtitleWindowToggled(checked)
    }
    popup.add(subtitleItem)
    subtitleMenuItemOut[0] = subtitleItem

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
