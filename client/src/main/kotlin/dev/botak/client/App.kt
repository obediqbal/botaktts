package dev.botak.client

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
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.NativeHookException
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.MenuItem
import java.awt.MouseInfo
import java.awt.Point
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.lang.Exception
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
                focusRequester = globalFocusRequester,
            )
        }

        if (showSettings) {
            SystemTrayWindow { showSettings = false }
        }

        LaunchedEffect(Unit) {
            if (SystemTray.isSupported()) {
                val tray = SystemTray.getSystemTray()
                val image = Toolkit.getDefaultToolkit().getImage("icon.png")

                val popup = PopupMenu()

                val settingsItem = MenuItem("Settings")
                settingsItem.addActionListener {
                    showSettings = true
                }
                popup.add(settingsItem)

                val exitItem = MenuItem("Exit")
                exitItem.addActionListener { exitApplication() }
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
    focusRequester: FocusRequester,
) {
    val density = LocalDensity.current
    // State management - this will trigger recomposition when changed
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var dragPoint by remember { mutableStateOf<Point?>(null) }
    var nowPlaying by remember { mutableStateOf("") }
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
                                    if (isPlaying) return@KeyboardActions
                                    if (inputText.isBlank()) return@KeyboardActions
                                    val input = inputText
                                    inputText = ""
                                    isPlaying = true
                                    play(
                                        input,
                                        scope,
                                        { isLoading = true },
                                        {
                                            nowPlaying = input
                                            isLoading = false
                                            isPlaying = true
                                        },
                                        {
                                            isPlaying = false
                                            nowPlaying = ""
                                        },
                                    )
                                }),
                            leadingIcon = {
                                IconButton(
                                    onClick = {
                                        if (isPlaying && !isLoading) {
                                            stop(scope)
                                            nowPlaying = ""
                                            isPlaying = false
                                        }
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
