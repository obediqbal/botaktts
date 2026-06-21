package dev.botak.client.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Downloads the NSIS installer for a release and launches it silently, then signals the caller
 * to quit the app so the installer can overwrite the running `.exe` and relaunch it.
 *
 * @param client The okhttp client used to stream the download.
 */
class UpdateInstaller(
    private val client: OkHttpClient = OkHttpClient(),
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(UpdateInstaller::class.java)
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Streams [setupAssetUrl] to `%TEMP%\BotakTTS-update\`, launches it silently
     * (`Setup.exe /S` — no installer wizard; Windows still shows one UAC prompt), then invokes
     * [onReadyToExit] so the caller can quit the app and let the installer replace files and
     * relaunch.
     *
     * Runs the download on [Dispatchers.IO]. Checks [ensureActive] in the download loop so
     * cancelling the parent coroutine stops the download.
     *
     * @param setupAssetUrl `browser_download_url` of the `*-Setup.exe` asset.
     * @param onProgress Reports download progress; `totalBytes` is `-1` if unknown. Throttled to
     *   ~100ms intervals to avoid flooding the caller with updates.
     * @param onReadyToExit Called after the installer process has started.
     * @throws RuntimeException if the download fails (non-2xx or empty body).
     */
    suspend fun downloadAndRun(
        setupAssetUrl: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        onReadyToExit: () -> Unit,
    ) = withContext(Dispatchers.IO) {
        val dir = File(System.getProperty("java.io.tmpdir"), "BotakTTS-update")
        // Clear stale partial/old downloads so each update starts from a clean directory.
        if (dir.exists()) {
            if (!dir.deleteRecursively()) {
                throw RuntimeException("Failed to clean temp directory: ${dir.absolutePath} (locked or permission denied)")
            }
        }
        if (!dir.mkdirs() && !dir.exists()) {
            throw RuntimeException("Failed to create temp directory: ${dir.absolutePath}")
        }
        val fileName = setupAssetUrl.substringAfterLast('/').ifBlank { "BotakTTS-Setup.exe" }
        val target = File(dir, fileName)

        val request = Request.Builder().url(setupAssetUrl).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: HTTP ${response.code}")
            }
            val responseBody = response.body ?: throw RuntimeException("Empty download body")
            val total = responseBody.contentLength()
            responseBody.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    var lastProgressTime = System.currentTimeMillis()
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 100) {
                            onProgress(downloaded, total)
                            lastProgressTime = now
                        }
                    }
                    // Final progress update at 100%
                    onProgress(downloaded, total)
                }
            }
        }

        ensureActive()
        LOGGER.info("Launching silent installer: ${target.absolutePath}")
        ProcessBuilder(target.absolutePath, "/S").start()
        onReadyToExit()
    }
}
