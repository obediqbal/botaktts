package dev.botak.client.windows

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import dev.botak.client.AppState
import dev.botak.client.forceFocusToWindow
import dev.botak.client.registerGlobalHotkey
import dev.botak.client.unregisterGlobalHotkey
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import javax.swing.SwingUtilities

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.AppWindow")

/**
 * The main floating input window for the BotakTTS application.
 *
 * Renders a transparent, undecorated, always-on-top window containing the text input that drives
 * TTS playback. The window is hidden/shown by the global `Ctrl+Shift+H` hotkey and when it gains
 * focus the text field is focused automatically so the user can type immediately. Its visibility
 * is also bound to the [enabled] flag, allowing the system tray to toggle the app on and off.
 *
 * On disposal the global hotkey is unregistered and the focus listener is removed.
 *
 * @param ttsService Service used to synthesize speech.
 * @param audioStreamService Service used to stream audio to the virtual microphone.
 * @param exitApplication Called when the user closes the window.
 * @param enabled Whether the app is currently enabled; controls window visibility.
 * @param appState Shared UI state updated with the now-playing text for the subtitle window.
 */
@Composable
@Preview
fun AppMainWindow(
    ttsService: TTSService,
    audioStreamService: AudioStreamService,
    exitApplication: () -> Unit,
    enabled: Boolean,
    appState: AppState,
) {
    val focusRequester = remember { FocusRequester() }
    var isWindowVisible by remember { mutableStateOf(true) }

    LaunchedEffect(enabled) {
        isWindowVisible = enabled
        LOGGER.debug("${if (enabled) "Enabling" else "Disabling"} AppWindow")
    }

    Window(
        onCloseRequest = exitApplication,
        title = "Botak TTS",
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        visible = isWindowVisible && enabled,
    ) {
        val focusManager = LocalFocusManager.current

        LOGGER.debug("Composing App Window...")

        DisposableEffect(Unit) {
            registerGlobalHotkey {
                isWindowVisible = !isWindowVisible
                if (isWindowVisible) {
                    SwingUtilities.invokeLater {
                        LOGGER.debug("Requesting focus")
                        forceFocusToWindow(window)
                    }
                }
            }

            val focusListener =
                object : WindowFocusListener {
                    override fun windowGainedFocus(e: WindowEvent) {
                        LOGGER.debug("AppMainWindow gained focus")
                        SwingUtilities.invokeLater {
                            focusRequester.requestFocus()
                        }
                    }

                    override fun windowLostFocus(e: WindowEvent) {
                        LOGGER.debug("AppMainWindow lost focus")
                        focusManager.clearFocus()
                    }
                }
            window.addWindowFocusListener(focusListener)

            onDispose {
                unregisterGlobalHotkey()
                window.removeWindowFocusListener(focusListener)
            }
        }

        LaunchedEffect(Unit) {
            val defaultWidth = 500
            val fixedHeight = 120
            window.minimumSize = Dimension(defaultWidth, fixedHeight)
            window.maximumSize = Dimension(Int.MAX_VALUE, fixedHeight)
            window.preferredSize = Dimension(defaultWidth, fixedHeight)
            window.size = Dimension(defaultWidth, fixedHeight)
        }

        AppWindow(
            window = window,
            ttsService = ttsService,
            audioStreamService = audioStreamService,
            focusRequester = focusRequester,
            appState = appState,
        )

        LOGGER.debug("Composed App Window")
    }
}

/**
 * The draggable text-input card shown inside the main window.
 *
 * Holds the text field where the user types text to synthesize, a speaker/stop button, and a
 * "now playing" status line. The card can be dragged anywhere on screen by holding and moving the
 * pointer over it. Pressing the IME "done" key (Enter) triggers playback via [startTTS].
 *
 * @param window The underlying [ComposeWindow], used for drag-to-move positioning.
 * @param ttsService Service used to synthesize speech.
 * @param audioStreamService Service used to stream audio to the virtual microphone.
 * @param focusRequester Requester used to focus the text field when the window gains focus.
 * @param appState Shared UI state updated with the now-playing text for the subtitle window.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
private fun AppWindow(
    window: ComposeWindow,
    ttsService: TTSService,
    audioStreamService: AudioStreamService,
    focusRequester: FocusRequester,
    appState: AppState,
) {
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragPoint by remember { mutableStateOf<Point?>(null) }
    var nowPlaying by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    /**
     * Synthesizes and plays back the current input text.
     *
     * No-ops if playback is already in progress or the input is blank. The input is cleared on
     * submission and synthesis + streaming run on a background coroutine; the running job is
     * stored so it can be cancelled by [stopTTS].
     */
    fun startTTS() {
        if (isPlaying) return
        if (inputText.isBlank()) return
        val text = inputText
        inputText = ""
        job =
            scope.launch(Dispatchers.IO) {
                // Load
                isPlaying = true
                isLoading = true
                val speech = ttsService.synthesizeSpeech(text)

                // Start
                nowPlaying = text
                appState.updateNowPlaying(text)
                isLoading = false
                isPlaying = true
                try {
                    audioStreamService.streamToVirtualAudio(speech, ttsService.sampleRateHz.toFloat())
                } finally {
                    // End — runs on normal completion and on cancellation mid-stream.
                    isPlaying = false
                    nowPlaying = ""
                    appState.clearNowPlaying()
                }
            }
    }

    /**
     * Cancels any in-progress playback.
     *
     * No-ops if nothing is playing or synthesis is still loading. Otherwise the playback coroutine
     * is cancelled and joined, and the status state is reset.
     */
    fun stopTTS() {
        if (isPlaying && !isLoading) {
            scope.launch(Dispatchers.IO) {
                job?.cancelAndJoin()
            }
            nowPlaying = ""
            appState.clearNowPlaying()
            isPlaying = false
        }
    }

    MaterialTheme(colors = dev.botak.client.darkColors) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Text input section
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    val mouse = MouseInfo.getPointerInfo().location
                                    dragPoint = Point(mouse.x - window.x, mouse.y - window.y)
                                },
                                onDragEnd = { dragPoint = null },
                                onDragCancel = { dragPoint = null },
                            ) { change, dragAmount ->
                                change.consume()
                                val mouse = MouseInfo.getPointerInfo().location
                                dragPoint?.let { offset ->
                                    window.setLocation(mouse.x - offset.x, mouse.y - offset.y)
                                }
                            }
                        },
                elevation = 4.dp,
            ) {
                Column(modifier = Modifier.wrapContentHeight().wrapContentWidth()) {
                    Column(modifier = Modifier.fillMaxHeight().padding(16.dp)) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("What should I say?") },
                            modifier =
                                Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { focusState ->
                                    LOGGER.debug("Text field focus changed ${focusState.isFocused}")
                                },
                            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = if (isPlaying) Color.Gray else Color.White),
                            keyboardOptions =
                                KeyboardOptions.Default.copy(
                                    imeAction = ImeAction.Done,
                                ),
                            keyboardActions =
                                KeyboardActions(onDone = {
                                    startTTS()
                                }),
                            leadingIcon = {
                                IconButton(
                                    onClick = {
                                        stopTTS()
                                    },
                                    enabled = isPlaying && !isLoading,
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp), color = Color.Gray)
                                    } else {
                                        Icon(
                                            painter = painterResource("speaker.svg"),
                                            contentDescription = "Speaker",
                                            modifier = Modifier.size(24.dp),
                                            tint = if (isPlaying) Color.Green else Color.Gray,
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                        )

                        Text(
                            text = "Now playing: ${nowPlaying.ifBlank { "Nothing" }}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            overflow = TextOverflow.MiddleEllipsis,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
