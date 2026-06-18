package dev.botak.client

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.application
import dev.botak.client.windows.AppMainWindow
import dev.botak.client.windows.SystemTrays
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService

/** Shared TTS service instance, lazily created on first use. */
private val ttsService by lazy { TTSService() }

/** Shared audio stream service instance, lazily created on first use. */
private val audioStreamService by lazy { AudioStreamService() }

/**
 * Launches the Compose Desktop application.
 *
 * Composes the main input window ([AppMainWindow]) and the system tray ([SystemTrays]), wiring
 * the tray's enable/disable toggles to the [isAppEnabled] state that controls window visibility.
 * The shared [ttsService] and [audioStreamService] instances are reused by both.
 */
fun start() =
    application {
        var isAppEnabled by remember { mutableStateOf(true) }

        AppMainWindow(
            ttsService = ttsService,
            audioStreamService = audioStreamService,
            exitApplication = ::exitApplication,
            enabled = isAppEnabled,
        )

        SystemTrays(
            exitApplication = ::exitApplication,
            onAppEnabled = { isAppEnabled = true },
            onAppDisabled = { isAppEnabled = false },
            ttsService = ttsService,
            audioStreamService = audioStreamService,
        )
    }
