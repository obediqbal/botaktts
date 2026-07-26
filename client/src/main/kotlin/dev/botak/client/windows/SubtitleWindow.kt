package dev.botak.client.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import dev.botak.client.AppState
import dev.botak.core.services.ConfigService
import java.awt.Component
import java.awt.Container
import java.awt.GraphicsEnvironment
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.Timer
import java.awt.Color as AwtColor

/** Minimum usable subtitle window outer size, enforced on restore. */
private const val MIN_WIDTH = 200
private const val MIN_HEIGHT = 60

/**
 * Debounce interval (ms) before persisting bounds after a native move/resize burst.
 * Avoids writing settings.json on every pixel of a drag.
 */
private const val BOUNDS_PERSIST_DEBOUNCE_MS = 300

/**
 * Opaque OBS Window Capture caption panel that mirrors the text currently being spoken.
 *
 * Renders a decorated, resizable, non-always-on-top window with a solid black client area and
 * plain white centered subtitle text while audio is playing. When idle the panel stays black and
 * empty so it remains findable on the desktop. Intended as a stable Window Capture source rather
 * than a transparent desktop overlay (layered transparent windows often capture as solid black
 * without usable alpha on Windows).
 *
 * Native AWT/Swing surfaces (frame, content pane, and descendants) are forced to black so OBS
 * Window Capture does not sample the default white window underlay under Compose's Skia layer.
 *
 * Visibility is bound to [enabled] only — the "Show Subtitles" tray toggle — and is independent of
 * the app "Enabled" toggle that hides [AppMainWindow]. Closing the window via the title-bar close
 * control calls [onDismissed] so the parent can persist off and uncheck the tray item.
 *
 * Bounds are restored from [ConfigService.userSettings] on first composition and debounced-persisted
 * after native move/resize. Saved width/height are outer window sizes (including title bar chrome).
 *
 * @param enabled Whether the subtitle window is shown (the persisted "Show Subtitles" tray state).
 * @param appState Shared UI state providing the now-playing text.
 * @param onDismissed Called when the user closes the window (X); parent must set enabled false and persist.
 */
@Composable
fun SubtitleWindow(
    enabled: Boolean,
    appState: AppState,
    onDismissed: () -> Unit,
) {
    val text by appState.nowPlayingText.collectAsState()

    Window(
        onCloseRequest = onDismissed,
        title = "Botak TTS Subtitles",
        transparent = false,
        undecorated = false,
        alwaysOnTop = false,
        resizable = true,
        visible = enabled,
    ) {
        // Re-apply after Compose rebuilds the AWT hierarchy so capture stays black, not default white.
        SideEffect {
            paintNativeSurfacesBlack(window)
        }
        LaunchedEffect(Unit) {
            paintNativeSurfacesBlack(window)
            initSubtitleBounds(window)
        }
        DisposableEffect(window) {
            val persistTimer =
                Timer(BOUNDS_PERSIST_DEBOUNCE_MS) {
                    persistSubtitleBounds(window)
                }.apply { isRepeats = false }

            val componentListener =
                object : ComponentAdapter() {
                    override fun componentMoved(e: ComponentEvent?) {
                        persistTimer.restart()
                    }

                    override fun componentResized(e: ComponentEvent?) {
                        persistTimer.restart()
                    }
                }

            window.addComponentListener(componentListener)
            onDispose {
                persistTimer.stop()
                window.removeComponentListener(componentListener)
            }
        }
        SubtitleContent(text = text)
    }
}

/**
 * Forces [window] and its Swing/AWT hierarchy to an opaque black background.
 *
 * Compose paints black on the Skia layer, but the default AWT frame/content pane is white. OBS
 * Window Capture on Windows often samples that native surface, which produced a white source and
 * unreadable white text. Setting the hierarchy black keeps desktop and capture aligned.
 *
 * Runs on the EDT when invoked off-thread so AWT property changes stay thread-safe.
 *
 * @param window The subtitle [ComposeWindow] to paint.
 */
private fun paintNativeSurfacesBlack(window: ComposeWindow) {
    val apply = {
        val black = AwtColor.BLACK
        window.background = black
        window.contentPane.background = black
        window.rootPane?.background = black
        window.layeredPane?.background = black
        paintComponentTreeBlack(window.contentPane, black)
        window.repaint()
    }
    if (SwingUtilities.isEventDispatchThread()) {
        apply()
    } else {
        SwingUtilities.invokeLater(apply)
    }
}

/**
 * Recursively sets background black and opaque on [root] and all descendants.
 *
 * @param root Root of the component subtree.
 * @param black The black [AwtColor] to apply.
 */
private fun paintComponentTreeBlack(
    root: Component,
    black: AwtColor,
) {
    root.background = black
    if (root is JComponent) {
        // Keep opaque so capture does not composite a clear/white underlay.
        root.isOpaque = true
    }
    if (root is Container) {
        for (i in 0 until root.componentCount) {
            paintComponentTreeBlack(root.getComponent(i), black)
        }
    }
}

/**
 * Full-size black caption surface. Renders centered white subtitle text when [text] is non-blank;
 * otherwise only the black background (idle / findable panel).
 *
 * @param text The current now-playing text.
 */
@Composable
private fun SubtitleContent(text: String) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotBlank()) {
            SubtitleText(text)
        }
    }
}

/**
 * Renders the subtitle as plain white bold centered text, soft-wrapped to the window width.
 *
 * @param text The text to render.
 */
@Composable
private fun SubtitleText(text: String) {
    BasicText(
        text = text,
        style =
            TextStyle(
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        softWrap = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Restores the subtitle window bounds from [ConfigService.userSettings], validating them against
 * the primary screen. Saved width/height are clamped to `>= [MIN_WIDTH]`/`[MIN_HEIGHT]` and the
 * screen size. If the saved position is `null` or places the window fully off-screen, a default
 * position (horizontally centered, near the bottom with a 40px margin) is computed using the
 * resolved width/height.
 *
 * Note: with native chrome, width/height are outer sizes (title bar included). Multi-monitor
 * positions fully off the primary screen still fall back to the default (pre-existing behavior).
 *
 * @param window The underlying [ComposeWindow] to position and size.
 */
private fun initSubtitleBounds(window: ComposeWindow) {
    val screen =
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice.defaultConfiguration.bounds
    val settings = ConfigService.userSettings
    val width = settings.subtitleWindowWidth.coerceIn(MIN_WIDTH, screen.width)
    val height = settings.subtitleWindowHeight.coerceIn(MIN_HEIGHT, screen.height)

    val savedX = settings.subtitleWindowX
    val savedY = settings.subtitleWindowY
    // Require at least 20px of the window to overlap the primary screen on every edge check.
    val onScreen =
        savedX != null && savedY != null &&
            savedX < screen.x + screen.width - 20 &&
            savedY < screen.y + screen.height - 20 &&
            savedX + width > screen.x + 20 &&
            savedY + height > screen.y + 20

    val x: Int
    val y: Int
    if (onScreen) {
        x = savedX!!
        y = savedY!!
    } else {
        val margin = 40
        x = screen.x + (screen.width - width) / 2
        y = screen.y + screen.height - height - margin
    }
    window.setBounds(x, y, width, height)
}

/**
 * Persists the current window outer position and size to [ConfigService.userSettings] and saves.
 *
 * @param window The underlying [ComposeWindow] whose bounds to persist.
 */
private fun persistSubtitleBounds(window: ComposeWindow) {
    ConfigService.userSettings.subtitleWindowX = window.x
    ConfigService.userSettings.subtitleWindowY = window.y
    ConfigService.userSettings.subtitleWindowWidth = window.width
    ConfigService.userSettings.subtitleWindowHeight = window.height
    ConfigService.saveUserSettings()
}
