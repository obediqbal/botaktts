package dev.botak.client

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import dev.botak.client.windows.AppWindow
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import java.awt.*
import javax.swing.SwingUtilities

private val ttsService by lazy { TTSService() }
private val audioStreamService by lazy { AudioStreamService() }
private val globalFocusRequester = FocusRequester()

fun start() =
    application {
        val windowState = remember { WindowState() }
        var isWindowVisible by remember { mutableStateOf(true) }
        val composeWindow = remember { mutableStateOf<ComposeWindow?>(null) }
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            try {
                GlobalScreen.registerNativeHook()
                val listener =
                    GlobalHotKeyListener {
                        isWindowVisible = !isWindowVisible
                        if (isWindowVisible) {
                            composeWindow.value?.let { win ->
                                SwingUtilities.invokeLater {
                                    win.toFront()
                                    win.requestFocus()
                                    win.requestFocusInWindow()
                                    globalFocusRequester.requestFocus()
                                }
                            }
                        }
                    }
                GlobalScreen.addNativeKeyListener(listener)
                println("Global hotkey registered")
            } catch (e: Exception) {
                println("Failed to register global hotkey listener: ${e.message}")
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                try {
                    GlobalScreen.unregisterNativeHook()
                } catch (e: NativeHookException) {
                    println("Failed to unregister native hook: ${e.message}")
                }
            }
        }

        Window(
            onCloseRequest = {
                GlobalScreen.unregisterNativeHook()
                exitApplication()
            },
            title = "Botak TTS",
            transparent = true,
            undecorated = true,
            alwaysOnTop = true,
            state = windowState,
            visible = isWindowVisible,
        ) {
            val defaultWidth = 500
            val fixedHeight = 120
            // Let Compose measure first, then pack window to fit content
            LaunchedEffect(Unit) {
//                window.pack()
                val size = window.size
                // lock vertical dimension
                window.minimumSize = Dimension(defaultWidth, fixedHeight)
                window.maximumSize = Dimension(Int.MAX_VALUE, fixedHeight)
                window.preferredSize = Dimension(defaultWidth, fixedHeight)
                window.size = Dimension(defaultWidth, fixedHeight)

                composeWindow.value = window
            }

            AppWindow(
                windowState = windowState,
                window = window,
                ttsService = ttsService,
                audioStreamService = audioStreamService,
                focusRequester = globalFocusRequester,
            )
        }

        if (showSettings) {
            SystemTrayWindow { showSettings = false }
        }

        LaunchedEffect(Unit) {
            if (SystemTray.isSupported()) {
                useSystemTray(onSettingsItem = { showSettings = true }, onExitItem = { exitApplication() })
            }
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
        e.printStackTrace()
    }
}
