package dev.botak.client

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.botak.client.windows.AppMainWindow
import dev.botak.client.windows.SettingsWindow
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import org.slf4j.LoggerFactory
import java.awt.*

private val ttsService by lazy { TTSService() }
private val audioStreamService by lazy { AudioStreamService() }
private val globalFocusRequester = FocusRequester()
private val LOGGER = LoggerFactory.getLogger("dev.botak.client.App")

fun start() =
    application {
        AppMainWindow(
            ttsService = ttsService,
            audioStreamService = audioStreamService,
            focusRequester = globalFocusRequester,
            exitApplication = ::exitApplication,
        )

        SettingsWindow(
            exitApplication = ::exitApplication,
        )
    }
