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
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application

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
            App(windowState)
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Preview
fun App(windowState: WindowState) {
    val density = LocalDensity.current
    // State management - this will trigger recomposition when changed
    var inputText by remember { mutableStateOf("") }

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
                            detectDragGestures { delta ->
                                val deltaXDp = with(density) { delta.x.toDp() }
                                val deltaYDp = with(density) { delta.y.toDp() }

                                println("DeltaX: $deltaXDp, DeltaY: $deltaYDp")
                                val currentPosition = windowState.position
                                println("Current position: $currentPosition")
                                if (currentPosition.isSpecified) {
                                    val newPosition =
                                        WindowPosition(
                                            x = currentPosition.x + deltaXDp,
                                            y = currentPosition.y + deltaYDp,
                                        )
                                    println("New position: $newPosition")
                                    windowState.position = newPosition
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
                        modifier = Modifier.fillMaxWidth().height(120.dp).verticalScroll(scrollState),
                        maxLines = Int.MAX_VALUE,
                        singleLine = false,
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
