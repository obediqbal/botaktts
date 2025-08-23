package dev.botak.client

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import org.jetbrains.skia.Surface
import java.awt.MouseInfo
import java.awt.Point

fun main() =
    application {
        val windowState = remember { WindowState() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Botak TTS",
            transparent = true,
            undecorated = true,
            state = windowState,
        ) {
            App(windowState, window)
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App(
    windowState: WindowState,
    window: ComposeWindow,
) {
    val density = LocalDensity.current
    // State management - this will trigger recomposition when changed
    var inputText by remember { mutableStateOf("") }
    var dragPoint by remember { mutableStateOf<Point?>(null) }

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
                    .fillMaxSize()
                    .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Text input section
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
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
                Column(modifier = Modifier.padding(16.dp)) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val scrollState = rememberScrollState()

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("Enter text to synthesize") },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("Process Text")
                    }
                }
            }
        }
    }
}
