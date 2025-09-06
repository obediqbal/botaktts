package dev.botak.core.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths

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
    val userSettings = loadUserSettings()
    val config = ConfigFactory.load()

    data class UserSettings(
        var languageCode: String,
        var voiceName: String,
        var pitch: Double,
        var speed: Double,
        var volume: Float,
    )

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

    // Save user settings to file
    fun saveUserSettings() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsFile, userSettings)
            LOGGER.info("Saved user settings to ${settingsFile.absolutePath}")
        } catch (e: Exception) {
            LOGGER.error("Failed to save user settings: ${e.message}")
        }
    }

    fun getString(key: String): String = config.getString(key)

    fun getDouble(key: String): Double = config.getDouble(key)

    fun getInt(key: String): Int = config.getInt(key)
}
