package dev.botak.core.services

import com.google.api.gax.core.FixedCredentialsProvider
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
        private val DEFAULT_VOICE_NAME = ConfigService.getString("defaults.voiceName")
        private val DEFAULT_LANGUAGE_CODE = ConfigService.getString("defaults.languageCode")
        private val DEFAULT_PITCH = ConfigService.getDouble("defaults.pitch")
        private val DEFAULT_SPEED = ConfigService.getDouble("defaults.speed")
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
    var languageCode: String = DEFAULT_LANGUAGE_CODE
        private set
    var voiceName: String = DEFAULT_VOICE_NAME
        private set
    var pitch: Double = DEFAULT_PITCH
        set(value) {
            updateAudioConfig(newPitch = value)
            field = value
            LOGGER.info("Set pitch to $field")
        }
    var speed: Double = DEFAULT_SPEED
        set(value) {
            updateAudioConfig(newSpeed = value)
            field = value
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

        LOGGER.info("Selected voice $voiceName")
    }

    fun synthesizeSpeech(text: String): ByteArray {
        LOGGER.debug("Synthesizing speech... text=$text")
        val input = SynthesisInput.newBuilder().setText(text).build()
        val request =
            SynthesizeSpeechRequest
                .newBuilder()
                .setInput(input)
                .setVoice(voiceSelectionParams)
                .setAudioConfig(audioConfig)
                .build()
        return client!!.synthesizeSpeech(request).audioContent.toByteArray().also {
            LOGGER.info("Synthesized speech for text=${text.take(min(15, text.length))}")
        }
    }

    fun fetchListVoiceNames(languageCode: String = DEFAULT_LANGUAGE_CODE): List<String> = fetchListVoices(languageCode).map { it.name }

    private fun updateAudioConfig(
        newSpeed: Double = speed,
        newPitch: Double = pitch,
    ) {
        audioConfig =
            AudioConfig
                .newBuilder()
                .setAudioEncoding(AudioEncoding.LINEAR16)
                .setPitch(newPitch)
                .setSpeakingRate(newSpeed)
                .setSampleRateHertz(DEFAULT_SAMPLE_RATE)
                .build()
        LOGGER.debug("Updated audio config, speed=$newSpeed, pitch=$newPitch")
    }

    fun fetchListVoices(languageCode: String = DEFAULT_LANGUAGE_CODE): List<Voice> = getAllVoices().filter { it.languageCodesList.contains(languageCode) }
}

fun main() {
    val ttsService = TTSService()
    println(ttsService.fetchListVoices())
}
