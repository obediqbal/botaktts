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
import androidx.compose.material.darkColors
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import dev.botak.client.GlobalHotKeyListener
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
import javax.swing.SwingUtilities

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.AppWindow")

@Composable
@Preview
fun AppMainWindow(
    ttsService: TTSService,
    audioStreamService: AudioStreamService,
    focusRequester: FocusRequester,
    exitApplication: () -> Unit,
    enabled: Boolean,
) {
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
        LOGGER.debug("Composing App Window...")

        LaunchedEffect(Unit) {
            registerHotkey {
                isWindowVisible = !isWindowVisible
                if (isWindowVisible) {
                    SwingUtilities.invokeLater {
                        window.toFront()
                        window.requestFocus()
                        window.requestFocusInWindow()
                        focusRequester.requestFocus()
                    }
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { unregisterHotkey() }
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
        )

        LOGGER.debug("Composed App Window")
    }
}

private fun registerHotkey(onToggle: () -> Unit) {
    try {
        GlobalScreen.registerNativeHook()
        val listener =
            GlobalHotKeyListener(onToggle)
        GlobalScreen.addNativeKeyListener(listener)
        LOGGER.debug("Global hotkey registered")
    } catch (e: Exception) {
        LOGGER.error("Failed to register global hotkey listener: ${e.message}")
    }
}

private fun unregisterHotkey() {
    try {
        GlobalScreen.unregisterNativeHook()
        LOGGER.debug("Unregistered global hotkey")
    } catch (e: NativeHookException) {
        LOGGER.error("Failed to unregister native hook: ${e.message}")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
private fun AppWindow(
    window: ComposeWindow,
    ttsService: TTSService,
    audioStreamService: AudioStreamService,
    focusRequester: FocusRequester,
) {
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragPoint by remember { mutableStateOf<Point?>(null) }
    var nowPlaying by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(window) {
        val listener =
            object : java.awt.event.WindowAdapter() {
                override fun windowActivated(e: java.awt.event.WindowEvent?) {
                    focusRequester.requestFocus()
                }
            }
        window.addWindowListener(listener)
        onDispose { window.removeWindowListener(listener) }
    }

    val darkColors =
        darkColors(
            primary = Color(0xFF90CAF9),
            primaryVariant = Color(0xFF1976D2),
            secondary = Color(0xFFCE93D8),
            background = Color(0x80000000), // Semi-transparent black
            surface = Color(0xB0121212), // Semi-transparent dark gray
            onBackground = Color.White,
            onSurface = Color.White,
        )

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
                isLoading = false
                isPlaying = true
                audioStreamService.streamToVirtualAudio(speech, ttsService.sampleRateHz.toFloat())

                // End
                isPlaying = false
                nowPlaying = ""
            }
    }

    fun stopTTS() {
        if (isPlaying && !isLoading) {
            scope.launch(Dispatchers.IO) {
                job?.cancelAndJoin()
            }
            nowPlaying = ""
            isPlaying = false
        }
    }

    MaterialTheme(colors = darkColors) {
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
                            label = { Text("Enter text to synthesize") },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
