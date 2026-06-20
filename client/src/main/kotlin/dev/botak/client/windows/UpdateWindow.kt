package dev.botak.client.windows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window

/** UI state for the update dialog. */
sealed interface UpdateUiState {
    /** The dialog is not shown. */
    object Hidden : UpdateUiState

    /** A check is in progress. */
    object Checking : UpdateUiState

    /**
     * The running version is the latest.
     *
     * @property version The current version string (e.g. `"1.1.0"`).
     */
    data class UpToDate(
        val version: String,
    ) : UpdateUiState

    /**
     * A newer release is available.
     *
     * @property latest The newer version string.
     * @property changelog Release notes (may be blank).
     * @property setupAssetUrl Installer download URL, or `null` if absent.
     * @property htmlUrl Release page URL (fallback link).
     */
    data class Available(
        val latest: String,
        val changelog: String,
        val setupAssetUrl: String?,
        val htmlUrl: String,
    ) : UpdateUiState

    /**
     * The installer is downloading.
     *
     * @property progress Fraction in `0.0..1.0`, or `null` if total size is unknown.
     */
    data class Downloading(
        val progress: Float?,
    ) : UpdateUiState

    /**
     * The check or download failed.
     *
     * @property message A friendly, non-technical summary.
     */
    data class Error(
        val message: String,
    ) : UpdateUiState
}

/**
 * Renders the update dialog for the current [state]. Shows nothing when [state] is
 * [UpdateUiState.Hidden].
 *
 * @param state The current UI state.
 * @param onClose Called when the user dismisses the dialog.
 * @param onUpdate Called with the installer URL when the user chooses to update.
 * @param onOpenReleasePage Called with the release page URL when no installer asset is available.
 */
@Composable
fun UpdateWindow(
    state: UpdateUiState,
    onClose: () -> Unit,
    onUpdate: (setupAssetUrl: String) -> Unit,
    onOpenReleasePage: (htmlUrl: String) -> Unit,
) {
    if (state is UpdateUiState.Hidden) return

    Window(
        onCloseRequest = onClose,
        title = "BotakTTS Updates",
        resizable = false,
    ) {
        MaterialTheme {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    is UpdateUiState.Hidden -> Unit

                    is UpdateUiState.Checking -> {
                        Text("Checking for updates…", style = MaterialTheme.typography.h6)
                        CircularProgressIndicator()
                    }

                    is UpdateUiState.UpToDate -> {
                        Text("You're up to date", style = MaterialTheme.typography.h6)
                        Text("You're on the latest version (v${state.version}).")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onClose) { Text("Close") }
                        }
                    }

                    is UpdateUiState.Available -> {
                        Text("Update available", style = MaterialTheme.typography.h6)
                        Text("v${state.latest} is available.")
                        if (state.changelog.isNotBlank()) {
                            Text(
                                state.changelog,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .verticalScroll(rememberScrollState()),
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onClose) { Text("Later") }
                            if (state.setupAssetUrl != null) {
                                Button(onClick = { onUpdate(state.setupAssetUrl) }) {
                                    Text("Update & Restart")
                                }
                            } else {
                                Button(onClick = { onOpenReleasePage(state.htmlUrl) }) {
                                    Text("Open release page")
                                }
                            }
                        }
                    }

                    is UpdateUiState.Downloading -> {
                        Text("Downloading update…", style = MaterialTheme.typography.h6)
                        if (state.progress != null) {
                            LinearProgressIndicator(
                                progress = state.progress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    is UpdateUiState.Error -> {
                        Text("Couldn't check for updates", style = MaterialTheme.typography.h6)
                        Text(state.message)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onClick = onClose) { Text("Close") }
                        }
                    }
                }
            }
        }
    }
}
