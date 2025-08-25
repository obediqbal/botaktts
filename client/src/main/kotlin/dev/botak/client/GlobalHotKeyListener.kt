package dev.botak.client

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener

class GlobalHotKeyListener(
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
