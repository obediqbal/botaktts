package dev.botak.core.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths

/**
 * Central configuration provider for the BotakTTS application.
 *
 * Responsibilities:
 * - Resolves the platform-specific application data directory where user settings are stored.
 * - Loads and persists user-editable settings ([userSettings]) as JSON.
 * - Exposes typed accessors ([getString], [getDouble], [getInt]) over the bundled
 *   `application.conf` reference configuration.
 *
 * The application data directory is created on first access:
 * - Windows: `%LOCALAPPDATA%\Botak TTS\`
 * - macOS: `~/Library/Application Support/Botak TTS/`
 * - Linux: `~/.config/botak-tts/`
 */
object ConfigService {
    private val LOGGER by lazy { LoggerFactory.getLogger(ConfigService::class.java) }
    private val objectMapper = jacksonObjectMapper()

    // App data directory management
    private val appDataDir: String by lazy {
        val os = System.getProperty("os.name").lowercase()
        val homeDir = System.getProperty("user.home")
        val appDataPath =
            when {
                os.contains("win") -> Paths.get(homeDir, "AppData", "Local", "Botak TTS").toString()
                os.contains("mac") -> Paths.get(homeDir, "Library", "Application Support", "Botak TTS").toString()
                else -> Paths.get(homeDir, ".config", "botak-tts").toString()
            }

        val dir = File(appDataPath)
        if (!dir.exists()) {
            dir.mkdirs()
            LOGGER.info("Created app data directory: $appDataPath")
        }
        appDataPath
    }
    private val settingsFile = File(appDataDir, "settings.json")

    /** The loaded Typesafe (HOCON) reference configuration from `application.conf`. */
    val config = ConfigFactory.load()

    /** The currently active user settings, loaded from disk or defaults on startup. */
    val userSettings = loadUserSettings()

    /**
     * User-editable TTS settings persisted across application restarts.
     *
     * @property languageCode BCP-47 language code used for voice selection (e.g. `"en-US"`).
     * @property voiceName The Google Cloud TTS voice name (e.g. `"en-US-Wavenet-D"`).
     * @property pitch Voice pitch adjustment, in the range `-20.0..20.0`.
     * @property speed Speaking rate multiplier, in the range `0.25..4.0`.
     * @property volume Linear volume gain applied during playback, in the range `0.0..2.0`.
     */
    data class UserSettings(
        var languageCode: String,
        var voiceName: String,
        var pitch: Double,
        var speed: Double,
        var volume: Float,
    )

    /**
     * Loads the user settings from the settings file, falling back to the defaults defined in
     * `application.conf` when the file is missing or cannot be parsed.
     *
     * @return the loaded [UserSettings], or a default instance on any failure.
     */
    // Load user settings from file or return defaults
    private fun loadUserSettings(): UserSettings =
        try {
            if (settingsFile.exists()) {
                val settings = objectMapper.readValue(settingsFile, UserSettings::class.java)
                LOGGER.info("Loaded user settings from ${settingsFile.absolutePath}")
                settings
            } else {
                val defaults =
                    UserSettings(
                        languageCode = getString("defaults.languageCode"),
                        voiceName = getString("defaults.voiceName"),
                        pitch = getDouble("defaults.pitch"),
                        speed = getDouble("defaults.speed"),
                        volume = getDouble("defaults.volume").toFloat(),
                    )
                LOGGER.info("Using default settings")
                defaults
            }
        } catch (e: Exception) {
            LOGGER.error("Failed to load user settings, using defaults: ${e.message}")
            UserSettings(
                languageCode = getString("defaults.languageCode"),
                voiceName = getString("defaults.voiceName"),
                pitch = getDouble("defaults.pitch"),
                speed = getDouble("defaults.speed"),
                volume = getDouble("defaults.volume").toFloat(),
            )
        }

    /**
     * Persists the current [userSettings] to the settings file as pretty-printed JSON.
     * Failures are logged but not rethrown.
     */
    // Save user settings to file
    fun saveUserSettings() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile, userSettings)
            LOGGER.info("Saved user settings to ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            LOGGER.error("Failed to save user settings: ${e.message}")
        }
    }

    /** Returns the string value at [key] from the reference configuration. */
    fun getString(key: String): String = config.getString(key)

    /** Returns the double value at [key] from the reference configuration. */
    fun getDouble(key: String): Double = config.getDouble(key)

    /** Returns the integer value at [key] from the reference configuration. */
    fun getInt(key: String): Int = config.getInt(key)
}
