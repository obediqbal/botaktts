package dev.botak.client

import androidx.compose.runtime.*
import androidx.compose.ui.window.application
import dev.botak.client.update.UpdateInstaller
import dev.botak.client.windows.AppMainWindow
import dev.botak.client.windows.SystemTrays
import dev.botak.client.windows.UpdateUiState
import dev.botak.client.windows.UpdateWindow
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import dev.botak.core.update.GitHubReleaseClient
import dev.botak.core.update.UpdateCheckResult
import dev.botak.core.update.UpdateService
import dev.botak.core.update.VersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.net.URI

/** Logger for the application bootstrap. */
private val LOGGER = LoggerFactory.getLogger("dev.botak.client.App")

/** Shared TTS service instance, lazily created on first use. */
private val ttsService by lazy { TTSService() }

/** Shared audio stream service instance, lazily created on first use. */
private val audioStreamService by lazy { AudioStreamService() }

/** Update orchestration service (core logic). */
private val updateService by lazy { UpdateService(VersionProvider(), GitHubReleaseClient()) }

/** Installer that downloads and launches the update (client side effects). */
private val updateInstaller by lazy { UpdateInstaller() }

/**
 * Launches the Compose Desktop application.
 *
 * Composes the main input window ([AppMainWindow]), the system tray ([SystemTrays]) and the
 * update dialog ([UpdateWindow]), wiring the tray's enable/disable toggles to [isAppEnabled] and
 * the tray's "Check for updates…" item to the update flow. The shared services are reused across
 * windows.
 */
fun start() =
    application {
        var isAppEnabled by remember { mutableStateOf(true) }
        var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Hidden) }
        val scope = rememberCoroutineScope()
        var updateJob by remember { mutableStateOf<Job?>(null) }

        AppMainWindow(
            ttsService = ttsService,
            audioStreamService = audioStreamService,
            exitApplication = ::exitApplication,
            enabled = isAppEnabled,
        )

        SystemTrays(
            exitApplication = ::exitApplication,
            onAppEnabled = { isAppEnabled = true },
            onAppDisabled = { isAppEnabled = false },
            onCheckForUpdates = {
                if (updateState !is UpdateUiState.Checking && updateState !is UpdateUiState.Downloading) {
                    updateJob?.cancel()
                    updateState = UpdateUiState.Checking
                    updateJob = scope.launch(Dispatchers.IO) {
                        val result = updateService.checkForUpdate()
                        withContext(Dispatchers.Main) {
                            updateState =
                                when (result) {
                                    is UpdateCheckResult.UpToDate ->
                                        UpdateUiState.UpToDate(result.current.toString())
                                    is UpdateCheckResult.UpdateAvailable ->
                                        UpdateUiState.Available(
                                            latest = result.latest.toString(),
                                            changelog = result.changelog,
                                            setupAssetUrl = result.setupAssetUrl,
                                            htmlUrl = result.htmlUrl,
                                        )
                                    is UpdateCheckResult.Failed -> {
                                        LOGGER.warn(result.reason, result.cause)
                                        UpdateUiState.Error("Couldn't check for updates.")
                                    }
                                }
                        }
                    }
                }
            },
            ttsService = ttsService,
            audioStreamService = audioStreamService,
        )

        UpdateWindow(
            state = updateState,
            onClose = {
                updateJob?.cancel()
                updateState = UpdateUiState.Hidden
            },
            onUpdate = { setupAssetUrl ->
                updateJob?.cancel()
                updateState = UpdateUiState.Downloading(null)
                updateJob = scope.launch(Dispatchers.IO) {
                    try {
                        updateInstaller.downloadAndRun(
                            setupAssetUrl = setupAssetUrl,
                            onProgress = { downloaded, total ->
                                scope.launch(Dispatchers.Main) {
                                    updateState =
                                        UpdateUiState.Downloading(
                                            if (total > 0) downloaded.toFloat() / total else null,
                                        )
                                }
                            },
                            onReadyToExit = {
                                scope.launch(Dispatchers.Main) {
                                    exitApplication()
                                }
                            },
                        )
                    } catch (e: CancellationException) {
                        // User cancelled via onClose; propagate cancellation, don't set Error state
                        throw e
                    } catch (e: Exception) {
                        LOGGER.warn("Update download failed", e)
                        withContext(Dispatchers.Main) {
                            updateState = UpdateUiState.Error("Couldn't download the update.")
                        }
                    }
                }
            },
            onOpenReleasePage = { htmlUrl ->
                try {
                    Desktop.getDesktop().browse(URI(htmlUrl))
                } catch (e: Exception) {
                    LOGGER.warn("Failed to open release page", e)
                }
                updateState = UpdateUiState.Hidden
            },
        )
    }
