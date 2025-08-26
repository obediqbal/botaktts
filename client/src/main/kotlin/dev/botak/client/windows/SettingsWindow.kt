package dev.botak.client.windows

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.windows.SettingsWindow")

@Composable
@Preview
fun SettingsWindow(
    visible: Boolean,
    onClose: () -> Unit,
) {
    Window(
        onCloseRequest = onClose,
        title = "Botak TTS Settings",
        visible = visible,
        enabled = visible,
    ) {
        LOGGER.debug("Composing Settings Window...")

        MaterialTheme(colors = dev.botak.client.darkColors) {
            Text("Settings window")
        }

        LOGGER.debug("Composed Settings Window")
    }
}
