package dev.botak.client

import org.jnativehook.GlobalScreen
import org.jnativehook.NativeHookException
import org.jnativehook.keyboard.NativeKeyEvent
import org.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

private val LOGGER = org.slf4j.LoggerFactory.getLogger("dev.botak.client.GlobalHotkeyListener")

/**
 * Registers a global `Ctrl+Shift+H` hotkey via JNativeHook that invokes [onToggle] when pressed.
 *
 * Silences JNativeHook's own verbose logging and logs any registration failure rather than
 * throwing. Call [unregisterGlobalHotkey] to release the native hook on shutdown.
 *
 * @param onToggle Callback invoked each time the hotkey is pressed.
 */
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

/**
 * Unregisters the global native hook previously set up by [registerGlobalHotkey].
 * Failures are logged rather than thrown.
 */
fun unregisterGlobalHotkey() {
    try {
        GlobalScreen.unregisterNativeHook()
        LOGGER.debug("Unregistered global hotkey")
    } catch (e: NativeHookException) {
        LOGGER.error("Failed to unregister native hook: ${e.message}")
    }
}

/**
 * Native key listener that detects the `Ctrl+Shift+H` chord and fires [onToggle].
 *
 * Tracks the pressed state of the modifier keys so the chord is only recognised when both are
 * held at the time `H` is pressed.
 *
 * @param onToggle Callback invoked when the hotkey chord is detected.
 */
private class GlobalHotKeyListener(
    private val onToggle: () -> Unit,
) : NativeKeyListener {
    private var ctrlPressed = false
    private var shiftPressed = false

    /** Updates modifier state and fires [onToggle] when `H` is pressed with both modifiers held. */
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

    /** Clears modifier state when a modifier key is released. */
    override fun nativeKeyReleased(e: NativeKeyEvent?) {
        when (e?.keyCode) {
            NativeKeyEvent.VC_CONTROL -> ctrlPressed = false
            NativeKeyEvent.VC_SHIFT -> shiftPressed = false
        }
    }

    /** Not used; required by [NativeKeyListener]. */
    override fun nativeKeyTyped(e: NativeKeyEvent?) {
        // Not used
    }
}
