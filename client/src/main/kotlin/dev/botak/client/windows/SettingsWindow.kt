package dev.botak.client.windows

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.util.Locale

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.windows.SettingsWindow")

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun SettingsWindow(
    ttsService: TTSService,
    audioStreamService: AudioStreamService,
    visible: Boolean,
    onClose: () -> Unit,
) {
    // Local states for selection
    var selectedLanguage by remember { mutableStateOf(ttsService.languageCode) }
    var selectedVoiceName by remember { mutableStateOf(ttsService.voiceName) }
    var currentPitch by remember { mutableStateOf(ttsService.pitch.toFloat()) }
    var currentSpeed by remember { mutableStateOf(ttsService.speed.toFloat()) }
    var currentVolume by remember { mutableStateOf(audioStreamService.volumeFactor) }

    // Preview section states
    var previewText by remember {
        mutableStateOf(
            "Movies, oh my gosh, I just just absolutely love them. They're like time machines taking you to different worlds and landscapes, and um, and I just can't get enough of it.",
        )
    }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    // Fetch languages and voices dynamically
    val languages = ttsService.getLanguages()
    val voiceNames =
        remember(selectedLanguage) {
            ttsService.fetchListVoiceNames(selectedLanguage)
        }

    // Dropdown states
    var langExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }

    // Scroll state for the window content
    val scrollState = rememberScrollState()

    // Coroutine scope for preview functionality
    val scope = rememberCoroutineScope()

    // Preview functionality
    fun playPreview() {
        if (isPreviewPlaying) return
        if (previewText.isBlank()) return

        scope.launch(Dispatchers.IO) {
            try {
                isPreviewPlaying = true
                isPreviewLoading = true
                val audio = ttsService.synthesizeSpeech(previewText)
                isPreviewLoading = false
                audioStreamService.streamToSpeakers(audio, ttsService.sampleRateHz.toFloat())
            } finally {
                isPreviewPlaying = false
            }
        }
    }

    if (visible) {
        Window(
            onCloseRequest = onClose,
            title = "Botak TTS Settings",
            visible = true,
            enabled = true,
            resizable = false,
        ) {
            // Ensure window can resize to content
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(100, 750)
                window.preferredSize = Dimension(100, 750)
            }
            LOGGER.debug("Composing Settings Window...")

            MaterialTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Settings", style = MaterialTheme.typography.h6)

                    // --- Language Dropdown ---
                    ExposedDropdownMenuBox(
                        expanded = langExpanded,
                        onExpandedChange = { langExpanded = !langExpanded },
                    ) {
                        OutlinedTextField(
                            value = Locale.forLanguageTag(selectedLanguage).displayName,
                            onValueChange = {},
                            label = { Text("Language") },
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        ExposedDropdownMenu(
                            expanded = langExpanded,
                            onDismissRequest = { langExpanded = false },
                        ) {
                            languages.forEach { language ->
                                DropdownMenuItem(
                                    onClick = {
                                        selectedLanguage = language
                                        val newVoices = ttsService.fetchListVoiceNames(language)
                                        if (newVoices.isNotEmpty()) {
                                            selectedVoiceName = newVoices.first()
                                        }
                                        langExpanded = false
                                        ttsService.selectVoice(selectedLanguage, selectedVoiceName)
                                    },
                                ) {
                                    Text(Locale.forLanguageTag(language).displayName)
                                }
                            }
                        }
                    }

                    // --- Voice Dropdown ---
                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = !voiceExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedVoiceName,
                            onValueChange = {},
                            label = { Text("Voice") },
                            readOnly = true,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        ExposedDropdownMenu(
                            expanded = voiceExpanded,
                            onDismissRequest = { voiceExpanded = false },
                        ) {
                            voiceNames.forEach { voice ->
                                DropdownMenuItem(
                                    onClick = {
                                        selectedVoiceName = voice
                                        voiceExpanded = false
                                        ttsService.selectVoice(selectedLanguage, selectedVoiceName)
                                    },
                                ) {
                                    Text(voice)
                                }
                            }
                        }
                    }

                    // --- Speed Slider ---
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Speed: %.2f".format(currentSpeed))
                        Slider(
                            value = currentSpeed,
                            onValueChange = { currentSpeed = it },
                            onValueChangeFinished = { ttsService.speed = currentSpeed.toDouble() },
                            valueRange = 0.25f..4.0f,
                            steps = 15,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // --- Pitch Slider ---
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Pitch: %.2f".format(currentPitch))
                        Slider(
                            value = currentPitch,
                            onValueChange = { currentPitch = it },
                            onValueChangeFinished = { ttsService.pitch = currentPitch.toDouble() },
                            valueRange = -20f..20f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // --- Volume Slider ---
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Volume: ${(currentVolume * 100).toInt()}%")
                        Slider(
                            value = currentVolume,
                            onValueChange = {
                                currentVolume = it
                                audioStreamService.volumeFactor = currentVolume
                            },
                            onValueChangeFinished = {
                                audioStreamService.persistVolumeFactor()
                            },
                            valueRange = 0f..2f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // --- Preview Section ---
                    Card(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Preview", style = MaterialTheme.typography.h6)

                            OutlinedTextField(
                                value = previewText,
                                onValueChange = { previewText = it },
                                label = { Text("Preview Text") },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                colors =
                                    TextFieldDefaults.outlinedTextFieldColors(
                                        textColor = MaterialTheme.colors.onSurface,
                                        backgroundColor = MaterialTheme.colors.surface,
                                    ),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                IconButton(
                                    onClick = { playPreview() },
                                    enabled = !isPreviewPlaying && previewText.isNotBlank(),
                                ) {
                                    if (isPreviewLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.width(24.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource("speaker.svg"),
                                            contentDescription = "Preview Audio",
                                            modifier = Modifier.width(24.dp),
                                            tint = if (isPreviewPlaying) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            LOGGER.debug("Composed Settings Window")
        }
    }
}
