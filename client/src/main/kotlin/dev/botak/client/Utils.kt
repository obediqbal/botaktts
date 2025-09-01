package dev.botak.client

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import org.slf4j.LoggerFactory
import java.awt.Window

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.Utils")

fun forceFocusToWindow(window: Window) {
    try {
        // First, bring the window to the front
        window.toFront()

        // Then use JNA to force Windows focus
        val hwnd = WinDef.HWND(Native.getComponentPointer(window))
        val user32 = User32.INSTANCE

        // Get the current foreground window
        val foregroundHWnd = user32.GetForegroundWindow()

        // Get the thread IDs
        val kernel32 = Kernel32.INSTANCE
        val currentThreadId = WinDef.DWORD(kernel32.GetCurrentThreadId().toLong())
        val foregroundThreadId = WinDef.DWORD(user32.GetWindowThreadProcessId(foregroundHWnd, null).toLong())

        // Attach to the foreground thread
        user32.AttachThreadInput(foregroundThreadId, currentThreadId, true)

        // Set the window position and focus
        user32.SetWindowPos(
            hwnd,
            WinDef.HWND(Pointer.createConstant(-1)),
            0,
            0,
            0,
            0,
            WinUser.SWP_NOSIZE or WinUser.SWP_NOMOVE or WinUser.SWP_SHOWWINDOW,
        )
        user32.SetWindowPos(
            hwnd,
            WinDef.HWND(Pointer.createConstant(-2)),
            0,
            0,
            0,
            0,
            WinUser.SWP_NOSIZE or WinUser.SWP_NOMOVE or WinUser.SWP_SHOWWINDOW,
        )

        user32.ShowWindow(hwnd, WinUser.SW_RESTORE) // in case minimized
        user32.SetForegroundWindow(hwnd)
        user32.BringWindowToTop(hwnd)
        user32.SetFocus(hwnd)
        // Detach from the foreground thread
        user32.AttachThreadInput(foregroundThreadId, currentThreadId, false)

        // Ensure the window is activated
        window.isVisible = true
        window.toFront()

        LOGGER.debug("Window forced to foreground")
    } catch (e: Exception) {
        LOGGER.error("Failed to force window focus: ${e.message}")
        // Fallback to standard methods
        window.toFront()
    }
}
