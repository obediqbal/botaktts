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
import kotlin.math.min

class TTSService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TTSService::class.java)
        private val USER_SETTINGS = ConfigService.userSettings
        private val DEFAULT_SAMPLE_RATE = ConfigService.getInt("defaults.sampleRate")
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
    var languageCode: String = USER_SETTINGS.languageCode
        private set
    var voiceName: String = USER_SETTINGS.voiceName
        private set
    var pitch: Double = USER_SETTINGS.pitch
        set(value) {
            require(value in -20.0..20.0) {
                "Pitch must be between -20.0 and 20.0"
            }
            updateAudioConfig(newPitch = value)
            field = value
            USER_SETTINGS.pitch = value
            ConfigService.saveUserSettings()
            LOGGER.info("Set pitch to $field")
        }
    var speed: Double = USER_SETTINGS.speed
        set(value) {
            require(value in 0.25..4.0) {
                "Speed must be between 0.25 and 4.0"
            }
            updateAudioConfig(newSpeed = value)
            field = value
            USER_SETTINGS.speed = value
            ConfigService.saveUserSettings()
            LOGGER.info("Set speed to $speed")
        }
    private lateinit var audioConfig: AudioConfig
    private lateinit var voiceSelectionParams: VoiceSelectionParams
    var sampleRateHz: Int = DEFAULT_SAMPLE_RATE
        private set

    private var allVoicesCache: List<Voice>? = null

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

    fun getLanguages(): List<String> =
        getAllVoices()
            .map { it.languageCodesList }
            .flatten()
            .distinct()

    fun selectVoice(
        languageCode: String = this.languageCode,
        voiceName: String = this.voiceName,
    ) {
        LOGGER.debug("Attempting to select voice for language=$languageCode, voiceName=$voiceName")
        val listVoices = fetchListVoices(languageCode)
        val voice = listVoices.firstOrNull { it.name == voiceName }
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
            client!!.synthesizeSpeech(request).audioContent.toByteArray()
        } catch (e: ApiException) {
            if (e.statusCode.code != StatusCode.Code.INVALID_ARGUMENT) {
                throw e
            }
            when (e.message) {
                "io.grpc.StatusRuntimeException: INVALID_ARGUMENT: This voice does not support pitch parameters at this time." -> {
                    LOGGER.warn(e.message)
                    val request =
                        requestBuilder
                            .setAudioConfig(createAudioConfig(speed, 0.0))
                            .build()
                    client!!.synthesizeSpeech(request).audioContent.toByteArray()
                }
                else -> throw e
            }
        } finally {
            LOGGER.info("Synthesized speech for text=${text.take(min(15, text.length))}")
        }
    }

    fun fetchListVoiceNames(languageCode: String = USER_SETTINGS.languageCode): List<String> = fetchListVoices(languageCode).map { it.name }

    private fun updateAudioConfig(
        newSpeed: Double = speed,
        newPitch: Double = pitch,
    ) {
        audioConfig = createAudioConfig(newSpeed, newPitch)
        LOGGER.debug("Updated audio config, speed=$newSpeed, pitch=$newPitch")
    }

    private fun createAudioConfig(
        speed: Double,
        pitch: Double,
    ): AudioConfig =
        AudioConfig
            .newBuilder()
            .setAudioEncoding(AudioEncoding.LINEAR16)
            .setPitch(pitch)
            .setSpeakingRate(speed)
            .setSampleRateHertz(DEFAULT_SAMPLE_RATE)
            .build()

    fun fetchListVoices(languageCode: String = USER_SETTINGS.languageCode): List<Voice> =
        getAllVoices().filter {
            it.languageCodesList.contains(languageCode)
        }
}

fun main() {
    val ttsService = TTSService()
    println(ttsService.fetchListVoices())
}
