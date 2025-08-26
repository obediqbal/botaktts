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
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import dev.botak.core.services.TTSService
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger("dev.botak.client.windows.SettingsWindow")

@OptIn(ExperimentalMaterialApi::class)
@Composable
@Preview
fun SettingsWindow(
    ttsService: TTSService,
    visible: Boolean,
    onClose: () -> Unit,
) {
    // Local states for selection
    var selectedLanguage by remember { mutableStateOf(ttsService.languageCode) }
    var selectedVoiceName by remember { mutableStateOf(ttsService.voiceName) }

    // Fetch voices dynamically
    val voiceNames =
        remember(selectedLanguage) {
            ttsService.fetchListVoiceNames(selectedLanguage)
        }

    // Dropdown states
    var langExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }

    if (visible) {
        Window(
            onCloseRequest = onClose,
            title = "Botak TTS Settings",
            visible = true,
            enabled = true,
        ) {
            LOGGER.debug("Composing Settings Window...")

            MaterialTheme {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
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
                            value = selectedLanguage,
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
                            DropdownMenuItem(
                                onClick = {
                                    selectedLanguage = "id-ID"
                                    langExpanded = false
                                },
                            ) {
                                Text(selectedLanguage)
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
                                    },
                                ) {
                                    Text(voice)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // --- Buttons ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onClose) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                ttsService.selectVoice(selectedLanguage, selectedVoiceName)
                                onClose()
                            },
                            enabled = selectedVoiceName.isNotBlank(),
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }

            LOGGER.debug("Composed Settings Window")
        }
    }
}
