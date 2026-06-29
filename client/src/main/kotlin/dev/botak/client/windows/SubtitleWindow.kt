package dev.botak.client.windows

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import dev.botak.client.AppState
import dev.botak.core.services.ConfigService
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point

/** Minimum usable subtitle window size, enforced on restore and on resize. */
private const val MIN_WIDTH = 200
private const val MIN_HEIGHT = 60

/**
 * Transparent OBS-friendly subtitle window that mirrors the text currently being spoken.
 *
 * Renders a transparent, undecorated, always-on-top window containing only the now-playing text
 * (white glyphs with a black halo, centered, wrapped to the window width). No `MaterialTheme` or
 * opaque surface is used so the window composites cleanly over any OBS scene. The window is moved
 * by dragging anywhere on it and resized via a small grip in the bottom-right corner; bounds are
 * restored from [ConfigService.userSettings] on first composition and persisted on move/resize end.
 *
 * Visibility is bound to [enabled] only — the "Show Subtitles" tray toggle — and is independent of
 * the app "Enabled" toggle that hides [AppMainWindow].
 *
 * @param enabled Whether the subtitle window is shown (the persisted "Show Subtitles" tray state).
 * @param appState Shared UI state providing the now-playing text.
 */
@Composable
fun SubtitleWindow(
    enabled: Boolean,
    appState: AppState,
) {
    val text by appState.nowPlayingText.collectAsState()

    Window(
        onCloseRequest = {},
        title = "Botak TTS Subtitles",
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        resizable = false,
        visible = enabled,
    ) {
        LaunchedEffect(Unit) {
            initSubtitleBounds(window)
        }
        SubtitleContent(window = window, text = text)
    }
}

/**
 * The subtitle content: a full-size transparent, draggable surface that centers the outlined text
 * and hosts the bottom-right resize grip.
 *
 * @param window The underlying [ComposeWindow], used for drag-to-move and resize.
 * @param text The current now-playing text (empty renders nothing).
 */
@Composable
private fun SubtitleContent(
    window: ComposeWindow,
    text: String,
) {
    var dragPoint by remember { mutableStateOf<Point?>(null) }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            val mouse = MouseInfo.getPointerInfo().location
                            dragPoint = Point(mouse.x - window.x, mouse.y - window.y)
                        },
                        onDragEnd = {
                            dragPoint = null
                            persistSubtitleBounds(window)
                        },
                        onDragCancel = { dragPoint = null },
                    ) { change, _ ->
                        change.consume()
                        val mouse = MouseInfo.getPointerInfo().location
                        dragPoint?.let { offset ->
                            window.setLocation(mouse.x - offset.x, mouse.y - offset.y)
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (text.isNotBlank()) {
            SubtitleText(text)
        }
        // Bottom-right resize grip. detectDragGestures consumes its own events, so dragging the
        // grip resizes instead of moving the window.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .background(Color.Gray.copy(alpha = 0.4f))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { persistSubtitleBounds(window) },
                        ) { change, dragAmount ->
                            change.consume()
                            val screen = window.graphicsConfiguration.bounds
                            val newWidth =
                                (window.width + dragAmount.x.toInt())
                                    .coerceIn(MIN_WIDTH, screen.width)
                            val newHeight =
                                (window.height + dragAmount.y.toInt())
                                    .coerceIn(MIN_HEIGHT, screen.height)
                            window.setSize(newWidth, newHeight)
                        }
                    },
        )
    }
}

/**
 * Renders the subtitle text as a white fill with a crisp black outline.
 *
 * The outline is built by stacking eight black copies at ±2dp offsets behind one white copy on
 * top, all `fillMaxWidth` so they wrap identically to the window width. No `MaterialTheme` is
 * required.
 *
 * @param text The text to render.
 */
@Composable
private fun SubtitleText(text: String) {
    val fillStyle =
        TextStyle(
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    val outlineStyle = fillStyle.copy(color = Color.Black)
    val outlineOffsets =
        listOf(
            -2 to -2,
            -2 to 0,
            -2 to 2,
            0 to -2,
            0 to 2,
            2 to -2,
            2 to 0,
            2 to 2,
        )
    outlineOffsets.forEach { (dx, dy) ->
        BasicText(
            text = text,
            style = outlineStyle,
            softWrap = true,
            modifier = Modifier.fillMaxWidth().offset(dx.dp, dy.dp),
        )
    }
    BasicText(
        text = text,
        style = fillStyle,
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
 * Persists the current window position and size to [ConfigService.userSettings] and saves.
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
