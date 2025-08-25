package dev.botak.client

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Surface
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

fun main() =

    application {
        val ttsService = TTSService()
        val audioStreamService = AudioStreamService()

        val windowState = remember { WindowState() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Botak TTS",
            transparent = true,
            undecorated = true,
            state = windowState,
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
            }

            var job: Job? = null

            App(
                windowState = windowState,
                window = window,
                play = { text, scope, onLoad, onStart, onEnd ->
                    job =
                        scope.launch(Dispatchers.IO) {
                            onLoad()
                            val speech = ttsService.synthesizeSpeech(text)
                            onStart()
                            audioStreamService.streamToVirtualAudio(speech, ttsService.sampleRateHz.toFloat())
                            onEnd()
                        }
                },
                stop = { scope ->
                    scope.launch {
                        job?.cancelAndJoin()
                    }
                },
            )
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App(
    windowState: WindowState,
    window: ComposeWindow,
    play: (text: String, scope: CoroutineScope, onLoad: () -> Unit, onStart: () -> Unit, onEnd: () -> Unit) -> Unit,
    stop: (scope: CoroutineScope) -> Unit,
) {
    val density = LocalDensity.current
    // State management - this will trigger recomposition when changed
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragPoint by remember { mutableStateOf<Point?>(null) }
    val scope = rememberCoroutineScope()

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
                Column(modifier = Modifier.wrapContentHeight()) {
                    val scrollState = rememberScrollState()

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Enter text to synthesize") },
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(16.dp),
                        keyboardOptions =
                            KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done,
                            ),
                        keyboardActions =
                            KeyboardActions(onDone = {
                                if (inputText.isBlank()) return@KeyboardActions
                                val input = inputText
                                inputText = ""
                                isPlaying = true
                                play(
                                    input,
                                    scope,
                                    { isLoading = true },
                                    {
                                        isLoading = false
                                        isPlaying = true
                                    },
                                    { isPlaying = false },
                                )
                            }),
                        leadingIcon = {
                            IconButton(
                                onClick = {
                                    if (isPlaying) stop(scope)
                                    isPlaying = false
                                },
                                enabled = isPlaying,
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
                }
//                Column {
//                    Button(
//                        onClick = {
//                        },
//                        modifier = Modifier.align(Alignment.CenterHorizontally),
//                    ) {
//                        Text("Process Text")
//                    }
//                }
            }
        }
    }
}
