package dev.botak.client

import org.jnativehook.GlobalScreen
import org.jnativehook.NativeHookException
import org.jnativehook.keyboard.NativeKeyEvent
import org.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

private val LOGGER = org.slf4j.LoggerFactory.getLogger("dev.botak.client.GlobalHotkeyListener")

fun registerGlobalHotkey(onToggle: () -> Unit) {
    try {
        Logger.getLogger(GlobalScreen::class.java.packageName).level = Level.OFF
        GlobalScreen.registerNativeHook()
        val listener =
            GlobalHotKeyListener(onToggle)
        GlobalScreen.addNativeKeyListener(listener)
        LOGGER.debug("Global hotkey registered")
    } catch (e: Exception) {
        LOGGER.error("Failed to register global hotkey listener: ${e.message}")
    }
}

fun unregisterGlobalHotkey() {
    try {
        GlobalScreen.unregisterNativeHook()
        LOGGER.debug("Unregistered global hotkey")
    } catch (e: NativeHookException) {
        LOGGER.error("Failed to unregister native hook: ${e.message}")
    }
}

private class GlobalHotKeyListener(
    private val onToggle: () -> Unit,
) : NativeKeyListener {
    private var ctrlPressed = false
    private var shiftPressed = false

    override fun nativeKeyPressed(e: NativeKeyEvent?) {
        when (e?.keyCode) {
            NativeKeyEvent.VC_CONTROL -> ctrlPressed = true
            NativeKeyEvent.VC_SHIFT -> shiftPressed = true
            NativeKeyEvent.VC_H -> {
                if (ctrlPressed && shiftPressed) {
                    onToggle()
                }
            }
        }
    }

    override fun nativeKeyReleased(e: NativeKeyEvent?) {
        when (e?.keyCode) {
            NativeKeyEvent.VC_CONTROL -> ctrlPressed = false
            NativeKeyEvent.VC_SHIFT -> shiftPressed = false
        }
    }

    override fun nativeKeyTyped(e: NativeKeyEvent?) {
        // Not used
    }
}
