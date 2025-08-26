package dev.botak.client

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.application
import dev.botak.client.windows.AppMainWindow
import dev.botak.client.windows.SystemTrays
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService

private val ttsService by lazy { TTSService() }
private val audioStreamService by lazy { AudioStreamService() }
private val globalFocusRequester = FocusRequester()

fun start() =
    application {
        var isAppEnabled by remember { mutableStateOf(true) }

        AppMainWindow(
            ttsService = ttsService,
            audioStreamService = audioStreamService,
            focusRequester = globalFocusRequester,
            exitApplication = ::exitApplication,
            enabled = isAppEnabled,
        )

        SystemTrays(
            exitApplication = ::exitApplication,
            onAppEnabled = { isAppEnabled = true },
            onAppDisabled = { isAppEnabled = false },
        )
    }
