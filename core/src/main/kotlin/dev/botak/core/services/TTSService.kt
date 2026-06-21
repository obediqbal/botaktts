package dev.botak.core.services

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode
import com.google.cloud.texttospeech.v1.AudioConfig
import com.google.cloud.texttospeech.v1.AudioEncoding
import com.google.cloud.texttospeech.v1.SynthesisInput
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest
import com.google.cloud.texttospeech.v1.TextToSpeechClient
import com.google.cloud.texttospeech.v1.TextToSpeechSettings
import com.google.cloud.texttospeech.v1.Voice
import com.google.cloud.texttospeech.v1.VoiceSelectionParams
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.min

/**
 * Provides text-to-speech synthesis through the Google Cloud Text-to-Speech API.
 *
 * The service owns a lazily created [TextToSpeechClient] that is rebuilt whenever the cached OAuth
 * credentials expire. Voice selection, pitch, speed and sample rate are configurable; pitch and
 * speed changes are validated, applied to the audio config, and persisted to user settings.
 *
 * Voice listings are cached after the first fetch from the API to avoid repeated network calls.
 */
class TTSService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TTSService::class.java)
        private val USER_SETTINGS = ConfigService.userSettings
        private val DEFAULT_SAMPLE_RATE = ConfigService.getInt("defaults.sampleRate")

        /**
         * Validates that [value] is within the supported pitch range.
         *
         * @param value the pitch to validate.
         * @throws IllegalArgumentException if [value] is outside `-20.0..20.0` or not a finite number.
         */
        internal fun validatePitch(value: Double) {
            require(value in -20.0..20.0) {
                "Pitch must be between -20.0 and 20.0"
            }
        }

        /**
         * Validates that [value] is within the supported speaking-rate range.
         *
         * @param value the speed to validate.
         * @throws IllegalArgumentException if [value] is outside `0.25..4.0` or not a finite number.
         */
        internal fun validateSpeed(value: Double) {
            require(value in 0.25..4.0) {
                "Speed must be between 0.25 and 4.0"
            }
        }

        /**
         * Builds an [AudioConfig] for LINEAR16 audio at the given sample rate.
         *
         * Google Cloud returns LINEAR16 audio content as a WAV stream, so callers that write to a
         * raw PCM line must decode the response before playback.
         *
         * @param speed Speaking rate multiplier.
         * @param pitch Pitch adjustment.
         * @param sampleRateHertz Sample rate in hertz; defaults to [DEFAULT_SAMPLE_RATE].
         * @return the constructed [AudioConfig].
         */
        internal fun buildAudioConfig(
            speed: Double,
            pitch: Double,
            sampleRateHertz: Int = DEFAULT_SAMPLE_RATE,
        ): AudioConfig =
            AudioConfig
                .newBuilder()
                .setAudioEncoding(AudioEncoding.LINEAR16)
                .setPitch(pitch)
                .setSpeakingRate(speed)
                .setSampleRateHertz(sampleRateHertz)
                .build()

        /**
         * Decodes a Google LINEAR16 response into raw PCM bytes.
         *
         * Google Cloud Text-to-Speech wraps LINEAR16 response audio in a WAV container. The Java
         * Sound reader consumes that container and exposes only the PCM frame data, preventing the
         * RIFF/WAVE header bytes from being sent to a [javax.sound.sampled.SourceDataLine].
         *
         * @param audioContent the WAV-wrapped LINEAR16 response bytes.
         * @return the raw PCM payload bytes from the WAV data chunk.
         * @throws javax.sound.sampled.UnsupportedAudioFileException if [audioContent] is not a
         *   supported WAV stream.
         */
        internal fun decodeLinear16AudioContent(audioContent: ByteArray): ByteArray =
            AudioSystem.getAudioInputStream(ByteArrayInputStream(audioContent)).use { audioInputStream ->
                audioInputStream.readBytes()
            }

        /**
         * Returns the subset of [voices] that support the given [languageCode].
         *
         * @param voices the full list of available [Voice]s.
         * @param languageCode the BCP-47 language code to match against each voice's language codes.
         * @return the voices whose language codes contain [languageCode].
         */
        internal fun filterVoicesByLanguage(
            voices: List<Voice>,
            languageCode: String,
        ): List<Voice> = voices.filter { it.languageCodesList.contains(languageCode) }

        /**
         * Returns the distinct set of BCP-47 language codes supported by [voices].
         *
         * @param voices the full list of available [Voice]s.
         * @return a deduplicated list of language codes.
         */
        internal fun distinctLanguageCodes(voices: List<Voice>): List<String> =
            voices
                .map { it.languageCodesList }
                .flatten()
                .distinct()

        /**
         * Finds the first voice in [voices] whose name equals [name].
         *
         * @param voices the list of [Voice]s to search.
         * @param name the voice name to match.
         * @return the first matching [Voice], or `null` when none match.
         */
        internal fun findVoiceByName(
            voices: List<Voice>,
            name: String,
        ): Voice? = voices.firstOrNull { it.name == name }

        /**
         * Determines whether an [ApiException] indicates the selected voice does not support pitch
         * parameters, in which case synthesis should be retried with a neutral pitch.
         *
         * @param e the exception raised by the synthesize call.
         * @return `true` when the error is the specific unsupported-pitch `INVALID_ARGUMENT` failure.
         */
        internal fun shouldRetryWithNeutralPitch(e: ApiException): Boolean =
            e.statusCode.code == StatusCode.Code.INVALID_ARGUMENT &&
                e.message == "io.grpc.StatusRuntimeException: INVALID_ARGUMENT: This voice does not support pitch parameters at this time."
    }

    private val credentialsService = CredentialsService()

    private var client: TextToSpeechClient? = null
        get() {
            if (field == null || !credentialsService.isCredentialsValid()) {
                field?.close()
                val clientSettings =
                    TextToSpeechSettings
                        .newBuilder()
                        .setCredentialsProvider(
                            FixedCredentialsProvider.create(credentialsService.obtainCredentials()),
                        ).build()
                field = TextToSpeechClient.create(clientSettings)
            }
            return field
        }

    /** The BCP-47 language code of the currently selected voice. */
    var languageCode: String = USER_SETTINGS.languageCode
        private set

    /** The name of the currently selected Google Cloud TTS voice. */
    var voiceName: String = USER_SETTINGS.voiceName
        private set

    /**
     * Voice pitch. Must be in the range `-20.0..20.0`. Assigning a new value updates the audio
     * config, persists the setting, and logs the change.
     */
    var pitch: Double = USER_SETTINGS.pitch
        set(value) {
            validatePitch(value)
            updateAudioConfig(newPitch = value)
            field = value
            USER_SETTINGS.pitch = value
            ConfigService.saveUserSettings()
            LOGGER.info("Set pitch to $field")
        }

    /**
     * Speaking rate multiplier. Must be in the range `0.25..4.0` (`1.0` is normal speed).
     * Assigning a new value updates the audio config, persists the setting, and logs the change.
     */
    var speed: Double = USER_SETTINGS.speed
        set(value) {
            validateSpeed(value)
            updateAudioConfig(newSpeed = value)
            field = value
            USER_SETTINGS.speed = value
            ConfigService.saveUserSettings()
            LOGGER.info("Set speed to $speed")
        }
    private lateinit var audioConfig: AudioConfig
    private lateinit var voiceSelectionParams: VoiceSelectionParams

    /** The sample rate (in hertz) used for synthesized audio, e.g. `24000`. */
    var sampleRateHz: Int = DEFAULT_SAMPLE_RATE
        private set

    private var allVoicesCache: List<Voice>? = null

    /**
     * Returns every voice supported by the API, fetching and caching the list on first access.
     *
     * @return the full list of available [Voice]s.
     */
    private fun getAllVoices(): List<Voice> {
        if (allVoicesCache == null) {
            LOGGER.debug("Fetching all voices...")
            allVoicesCache = client!!.listVoices("").voicesList
        } else {
            LOGGER.debug("Using cached all voices")
        }
        return allVoicesCache!!
    }

    init {
        selectVoice(languageCode, voiceName)
        updateAudioConfig(speed, pitch)
    }

    /**
     * Returns the distinct list of BCP-47 language codes supported by the available voices.
     *
     * @return a deduplicated list of language codes.
     */
    fun getLanguages(): List<String> = distinctLanguageCodes(getAllVoices())

    /**
     * Selects the voice to use for synthesis.
     *
     * The voice must exist for the given [languageCode]; otherwise an [IllegalArgumentException]
     * is thrown. The selection is persisted to user settings.
     *
     * @param languageCode BCP-47 language code; defaults to the currently selected one.
     * @param voiceName Name of the voice; defaults to the currently selected one.
     * @throws IllegalArgumentException if [voiceName] is not available for [languageCode].
     */
    fun selectVoice(
        languageCode: String = this.languageCode,
        voiceName: String = this.voiceName,
    ) {
        LOGGER.debug("Attempting to select voice for language=$languageCode, voiceName=$voiceName")
        val listVoices = fetchListVoices(languageCode)
        val voice = findVoiceByName(listVoices, voiceName)
        require(voice != null) { "Can't find $voiceName in $languageCode" }

        voiceSelectionParams =
            VoiceSelectionParams
                .newBuilder()
                .setLanguageCode(languageCode)
                .setName(voiceName)
                .build()

        this.languageCode = languageCode
        this.voiceName = voiceName

        // Save the updated settings
        USER_SETTINGS.languageCode = languageCode
        USER_SETTINGS.voiceName = voiceName
        ConfigService.saveUserSettings()

        LOGGER.info("Selected voice $voiceName")
    }

    /**
     * Synthesizes the given [text] into speech using the selected voice and audio config.
     *
     * Returns raw LINEAR16 PCM audio bytes. Google returns LINEAR16 responses as WAV streams, so
     * this method decodes the response and strips the WAV container before returning data for
     * playback. If the selected voice does not support the pitch parameter, synthesis is retried
     * with a neutral pitch (`0.0`).
     *
     * @param text The text to synthesize.
     * @return the synthesized audio content as a byte array.
     * @throws ApiException if the API call fails with a non-recoverable error.
     */
    // TODO: Add feedback that the voice doesn't support Pitch parameter
    fun synthesizeSpeech(text: String): ByteArray {
        LOGGER.debug("Synthesizing speech... text=$text")
        val input = SynthesisInput.newBuilder().setText(text).build()
        val requestBuilder =
            SynthesizeSpeechRequest
                .newBuilder()
                .setInput(input)
                .setVoice(voiceSelectionParams)
        return try {
            val request =
                requestBuilder
                    .setAudioConfig(audioConfig)
                    .build()
            decodeLinear16AudioContent(client!!.synthesizeSpeech(request).audioContent.toByteArray())
        } catch (e: ApiException) {
            if (shouldRetryWithNeutralPitch(e)) {
                LOGGER.warn(e.message)
                val request =
                    requestBuilder
                        .setAudioConfig(createAudioConfig(speed, 0.0))
                        .build()
                decodeLinear16AudioContent(client!!.synthesizeSpeech(request).audioContent.toByteArray())
            } else {
                throw e
            }
        } finally {
            LOGGER.info("Synthesized speech for text=${text.take(min(15, text.length))}")
        }
    }

    /**
     * Returns the names of the voices available for the given [languageCode].
     *
     * @param languageCode BCP-47 language code; defaults to the user's selected language.
     * @return a list of voice names.
     */
    fun fetchListVoiceNames(languageCode: String = USER_SETTINGS.languageCode): List<String> = fetchListVoices(languageCode).map { it.name }

    /**
     * Rebuilds the [audioConfig] with the supplied [newSpeed] and [newPitch].
     *
     * @param newSpeed Speaking rate to apply; defaults to the current [speed].
     * @param newPitch Pitch to apply; defaults to the current [pitch].
     */
    private fun updateAudioConfig(
        newSpeed: Double = speed,
        newPitch: Double = pitch,
    ) {
        audioConfig = createAudioConfig(newSpeed, newPitch)
        LOGGER.debug("Updated audio config, speed=$newSpeed, pitch=$newPitch")
    }

    /**
     * Builds an [AudioConfig] for LINEAR16 audio at the default sample rate.
     *
     * @param speed Speaking rate multiplier.
     * @param pitch Pitch adjustment.
     * @return the constructed [AudioConfig].
     */
    private fun createAudioConfig(
        speed: Double,
        pitch: Double,
    ): AudioConfig = buildAudioConfig(speed, pitch, DEFAULT_SAMPLE_RATE)

    /**
     * Returns the voices that support the given [languageCode], filtered from the cached voice list.
     *
     * @param languageCode BCP-47 language code; defaults to the user's selected language.
     * @return the list of matching [Voice]s.
     */
    fun fetchListVoices(languageCode: String = USER_SETTINGS.languageCode): List<Voice> =
        filterVoicesByLanguage(getAllVoices(), languageCode)
}

fun main() {
    val ttsService = TTSService()
    println(ttsService.fetchListVoices())
}
