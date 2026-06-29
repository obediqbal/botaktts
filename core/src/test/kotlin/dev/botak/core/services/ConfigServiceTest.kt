package dev.botak.core.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [ConfigService].
 *
 * Covers:
 * - The [ConfigService.UserSettings] data contract (equality, copy, components).
 * - Typed read-only accessors ([ConfigService.getString], [ConfigService.getDouble],
 *   [ConfigService.getInt]) over the bundled `application.conf`.
 * - The save/load round-trip for persisted user settings, including pretty-printed JSON on disk.
 * - Default fallbacks when the settings file is missing.
 * - Default fallbacks (no throw) when the settings file contains invalid JSON.
 * - Fault tolerance of [ConfigService.saveUserSettings] when the target path is unwritable.
 *
 * To avoid corrupting the real production `settings.json`, each test redirects
 * [ConfigService.settingsFile] to a temporary file in [beforeTest] and restores the
 * original reference in [afterTest] (guarded by try/finally).
 *
 * Note on lifecycle: the shared `subprojects` build only places `kotlin("test")` on the test
 * compile classpath, so JUnit Jupiter's `@BeforeEach`/`@AfterEach`/`@TempDir` are not resolvable.
 * `kotlin.test.BeforeTest`/`AfterTest` (framework-agnostic, runs on the configured JUnit
 * Platform) are used instead, and the temporary directory is created/deleted manually.
 */
class ConfigServiceTest {
    /** Per-test temporary directory, created in [beforeTest] and deleted in [afterTest]. */
    private lateinit var tempDir: Path

    /** Snapshot of the real production settings file, restored in [afterTest]. */
    private lateinit var originalSettingsFile: File

    /** Shared mapper used to validate JSON written to disk. */
    private val objectMapper = jacksonObjectMapper()

    /**
     * Saves the production [ConfigService.settingsFile] reference, creates a fresh temporary
     * directory, and redirects [ConfigService.settingsFile] to a temp file inside it so tests
     * never touch real user data.
     */
    @BeforeTest
    fun beforeTest() {
        originalSettingsFile = ConfigService.settingsFile
        tempDir = Files.createTempDirectory("config-service-test").toAbsolutePath()
        val tempFile = Files.createTempFile(tempDir, "settings", ".json").toFile()
        // start from a clean slate: no leftover bytes from createTempFile
        tempFile.delete()
        ConfigService.settingsFile = tempFile
    }

    /**
     * Restores the original production [ConfigService.settingsFile] reference and deletes the
     * temporary directory, even if a test failed mid-flight, so production state is never
     * corrupted.
     */
    @AfterTest
    fun afterTest() {
        try {
            ConfigService.settingsFile = originalSettingsFile
        } finally {
            // best-effort cleanup of the manual temp directory.
            tempDir.toFile().deleteRecursively()
        }
    }

    // ------------------------------------------------------------------
    // UserSettings data class contract
    // ------------------------------------------------------------------

    /**
     * Two [ConfigService.UserSettings] instances built from identical field values should
     * be equal and share the same hash code.
     */
    @Test
    fun `UserSettings equal instances are equal`() {
        val a =
            ConfigService.UserSettings(
                languageCode = "id-ID",
                voiceName = "id-ID-Chirp3-HD-Kore",
                pitch = 0.0,
                speed = 1.0,
                volume = 1.0f,
            )
        val b =
            ConfigService.UserSettings(
                languageCode = "id-ID",
                voiceName = "id-ID-Chirp3-HD-Kore",
                pitch = 0.0,
                speed = 1.0,
                volume = 1.0f,
            )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    /**
     * Differing any single field should make two [ConfigService.UserSettings] instances
     * unequal.
     */
    @Test
    fun `UserSettings instances differ when a field differs`() {
        val base =
            ConfigService.UserSettings(
                languageCode = "id-ID",
                voiceName = "id-ID-Chirp3-HD-Kore",
                pitch = 0.0,
                speed = 1.0,
                volume = 1.0f,
            )
        assertFalse(
            base ==
                base.copy(languageCode = "en-US"),
            "differing languageCode should break equality",
        )
        assertFalse(
            base ==
                base.copy(voiceName = "en-US-Wavenet-D"),
            "differing voiceName should break equality",
        )
        assertFalse(
            base == base.copy(pitch = 5.0),
            "differing pitch should break equality",
        )
        assertFalse(
            base == base.copy(speed = 2.0),
            "differing speed should break equality",
        )
        assertFalse(
            base == base.copy(volume = 0.5f),
            "differing volume should break equality",
        )
    }

    /**
     * [ConfigService.UserSettings.copy] should produce an instance equal to the original.
     */
    @Test
    fun `UserSettings copy produces equal instance`() {
        val original =
            ConfigService.UserSettings(
                languageCode = "id-ID",
                voiceName = "id-ID-Chirp3-HD-Kore",
                pitch = 0.0,
                speed = 1.0,
                volume = 1.0f,
            )
        val copied = original.copy()
        assertEquals(original, copied)
    }

    /**
     * The data-class component functions should expose the constructor field values
     * positionally.
     */
    @Test
    fun `UserSettings componentN values match constructor args`() {
        val settings =
            ConfigService.UserSettings(
                languageCode = "en-US",
                voiceName = "en-US-Wavenet-D",
                pitch = -4.0,
                speed = 1.25,
                volume = 0.75f,
            )
        assertEquals("en-US", settings.component1())
        assertEquals("en-US-Wavenet-D", settings.component2())
        assertEquals(-4.0, settings.component3())
        assertEquals(1.25, settings.component4())
        assertEquals(0.75f, settings.component5())
    }

    // ------------------------------------------------------------------
    // Typed accessors over application.conf
    // ------------------------------------------------------------------

    /** The token issuer URL should match the value declared in `application.conf`. */
    @Test
    fun `getString returns token issuer url`() {
        assertEquals(
            "https://us-central1-tts-botak.cloudfunctions.net/token-issuer-function",
            ConfigService.getString("tokenIssuerUrl"),
        )
    }

    /** The default voice name should match the value declared in `application.conf`. */
    @Test
    fun `getString returns default voice name`() {
        assertEquals("id-ID-Chirp3-HD-Kore", ConfigService.getString("defaults.voiceName"))
    }

    /** The default language code should match the value declared in `application.conf`. */
    @Test
    fun `getString returns default language code`() {
        assertEquals("id-ID", ConfigService.getString("defaults.languageCode"))
    }

    /** The default pitch should be `0.0`. */
    @Test
    fun `getDouble returns default pitch`() {
        assertEquals(0.0, ConfigService.getDouble("defaults.pitch"))
    }

    /** The default speed should be `1.0`. */
    @Test
    fun `getDouble returns default speed`() {
        assertEquals(1.0, ConfigService.getDouble("defaults.speed"))
    }

    /** The default volume should be `1.0`. */
    @Test
    fun `getDouble returns default volume`() {
        assertEquals(1.0, ConfigService.getDouble("defaults.volume"))
    }

    /** The default sample rate should be `24000`. */
    @Test
    fun `getInt returns default sample rate`() {
        assertEquals(24000, ConfigService.getInt("defaults.sampleRate"))
    }

    /** Requesting a key that does not exist in the config should throw. */
    @Test
    fun `getString throws for missing key`() {
        assertFailsWith<Exception> {
            ConfigService.getString("nonexistent.key")
        }
    }

    // ------------------------------------------------------------------
    // saveUserSettings / loadUserSettings round-trip
    // ------------------------------------------------------------------

    /**
     * Saving then loading should reproduce the persisted [ConfigService.UserSettings]
     * exactly, and the on-disk file should exist and contain the field values as JSON.
     */
    @Test
    fun `saveUserSettings then loadUserSettings round-trips values`() {
        val expected =
            ConfigService.UserSettings(
                languageCode = "en-US",
                voiceName = "en-US-Wavenet-D",
                pitch = -4.0,
                speed = 1.25,
                volume = 0.75f,
            )
        // mutate the live object, then persist
        ConfigService.userSettings.languageCode = expected.languageCode
        ConfigService.userSettings.voiceName = expected.voiceName
        ConfigService.userSettings.pitch = expected.pitch
        ConfigService.userSettings.speed = expected.speed
        ConfigService.userSettings.volume = expected.volume

        // reset subtitle fields so the round-trip equality holds regardless of prior singleton state
        ConfigService.userSettings.subtitleWindowEnabled = false
        ConfigService.userSettings.subtitleWindowX = null
        ConfigService.userSettings.subtitleWindowY = null
        ConfigService.userSettings.subtitleWindowWidth = 600
        ConfigService.userSettings.subtitleWindowHeight = 200

        ConfigService.saveUserSettings()

        val file = ConfigService.settingsFile
        assertTrue(file.exists(), "settings file should have been created by saveUserSettings")

        // the on-disk JSON should be valid and contain the expected field values
        val rawJson = file.readText()

        @Suppress("UNCHECKED_CAST")
        val parsed: Map<String, Any?> = objectMapper.readValue(rawJson, Map::class.java) as Map<String, Any?>
        assertEquals(expected.languageCode, parsed["languageCode"])
        assertEquals(expected.voiceName, parsed["voiceName"])
        assertEquals(expected.pitch, (parsed["pitch"] as Number).toDouble())
        assertEquals(expected.speed, (parsed["speed"] as Number).toDouble())
        assertEquals(expected.volume, (parsed["volume"] as Number).toFloat())

        // loading should reconstruct an equal UserSettings
        val reloaded = ConfigService.loadUserSettings()
        assertEquals(expected, reloaded)
    }

    // ------------------------------------------------------------------
    // loadUserSettings default fallbacks
    // ------------------------------------------------------------------

    /**
     * When the settings file does not exist, [ConfigService.loadUserSettings] should
     * return the defaults declared in `application.conf`.
     */
    @Test
    fun `loadUserSettings returns defaults when file is missing`() {
        val missingFile = tempDir.resolve("does-not-exist.json").toFile()
        assertFalse(missingFile.exists())
        ConfigService.settingsFile = missingFile

        val loaded = ConfigService.loadUserSettings()

        val expected =
            ConfigService.UserSettings(
                languageCode = ConfigService.getString("defaults.languageCode"),
                voiceName = ConfigService.getString("defaults.voiceName"),
                pitch = ConfigService.getDouble("defaults.pitch"),
                speed = ConfigService.getDouble("defaults.speed"),
                volume = ConfigService.getDouble("defaults.volume").toFloat(),
            )
        assertEquals(expected, loaded)
    }

    /**
     * When the settings file contains invalid JSON, [ConfigService.loadUserSettings]
     * should swallow the parse error and return the `application.conf` defaults rather
     * than throwing.
     */
    @Test
    fun `loadUserSettings returns defaults when file has invalid json`() {
        val garbageFile = Files.createTempFile(tempDir, "garbage", ".json").toFile()
        garbageFile.writeText("{ this is :: not valid json !!!")
        ConfigService.settingsFile = garbageFile

        val loaded = ConfigService.loadUserSettings()

        val expected =
            ConfigService.UserSettings(
                languageCode = ConfigService.getString("defaults.languageCode"),
                voiceName = ConfigService.getString("defaults.voiceName"),
                pitch = ConfigService.getDouble("defaults.pitch"),
                speed = ConfigService.getDouble("defaults.speed"),
                volume = ConfigService.getDouble("defaults.volume").toFloat(),
            )
        assertEquals(expected, loaded)
    }

    // ------------------------------------------------------------------
    // saveUserSettings fault tolerance
    // ------------------------------------------------------------------

    /**
     * [ConfigService.saveUserSettings] should not throw when the target path's parent
     * directory does not exist (Jackson would fail to create the file, and the catch
     * block should swallow the error).
     */
    @Test
    fun `saveUserSettings does not throw when parent dir does not exist`() {
        val unreachable = tempDir.resolve("no-such-dir").resolve("settings.json").toFile()
        assertFalse(unreachable.parentFile.exists())
        ConfigService.settingsFile = unreachable

        // must not throw — fault is logged and swallowed internally
        ConfigService.saveUserSettings()

        // the file should not have been created since the parent is missing
        assertFalse(unreachable.exists())
    }

    // ------------------------------------------------------------------
    // Subtitle window settings
    // ------------------------------------------------------------------

    /** The subtitle window fields default to false / null / 600 / 200 when the file is missing. */
    @Test
    fun `loadUserSettings defaults subtitle fields when file is missing`() {
        // beforeTest already points settingsFile at a non-existent temp file
        val loaded = ConfigService.loadUserSettings()
        assertEquals(false, loaded.subtitleWindowEnabled)
        assertEquals(null, loaded.subtitleWindowX)
        assertEquals(null, loaded.subtitleWindowY)
        assertEquals(600, loaded.subtitleWindowWidth)
        assertEquals(200, loaded.subtitleWindowHeight)
    }

    /** Subtitle bounds (enabled flag, position, size) survive a save/load round-trip. */
    @Test
    fun `subtitle bounds round-trip through save and load`() {
        ConfigService.userSettings.apply {
            subtitleWindowEnabled = true
            subtitleWindowX = 100
            subtitleWindowY = 200
            subtitleWindowWidth = 800
            subtitleWindowHeight = 250
        }
        ConfigService.saveUserSettings()

        val reloaded = ConfigService.loadUserSettings()
        assertEquals(true, reloaded.subtitleWindowEnabled)
        assertEquals(100, reloaded.subtitleWindowX)
        assertEquals(200, reloaded.subtitleWindowY)
        assertEquals(800, reloaded.subtitleWindowWidth)
        assertEquals(250, reloaded.subtitleWindowHeight)
    }

    /** A null subtitle position persists as null through save/load. */
    @Test
    fun `null subtitle position persists as null`() {
        ConfigService.userSettings.apply {
            subtitleWindowEnabled = true
            subtitleWindowX = null
            subtitleWindowY = null
        }
        ConfigService.saveUserSettings()

        val reloaded = ConfigService.loadUserSettings()
        assertEquals(null, reloaded.subtitleWindowX)
        assertEquals(null, reloaded.subtitleWindowY)
    }

    /** A settings JSON written by an older app version (no subtitle fields) deserializes with the Kotlin defaults. */
    @Test
    fun `settings json missing subtitle fields deserializes with defaults`() {
        ConfigService.settingsFile.writeText(
            """{"languageCode":"en-US","voiceName":"en-US-Wavenet-D","pitch":0.0,"speed":1.0,"volume":1.0}""",
        )
        val loaded = ConfigService.loadUserSettings()
        assertEquals(false, loaded.subtitleWindowEnabled)
        assertEquals(null, loaded.subtitleWindowX)
        assertEquals(null, loaded.subtitleWindowY)
        assertEquals(600, loaded.subtitleWindowWidth)
        assertEquals(200, loaded.subtitleWindowHeight)
    }
}
