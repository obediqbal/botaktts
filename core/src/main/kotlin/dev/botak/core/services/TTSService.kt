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

class TTSService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(TTSService::class.java)
        private val DEFAULT_VOICE_NAME = ConfigService.getString("defaults.voiceName")
        private val DEFAULT_LANGUAGE_CODE = ConfigService.getString("defaults.languageCode")
        private val DEFAULT_PITCH = ConfigService.getDouble("defaults.pitch")
        private val DEFAULT_SPEED = ConfigService.getDouble("defaults.speed")
    }

    private val clientSettings: TextToSpeechSettings =
        TextToSpeechSettings
            .newBuilder()
            .setCredentialsProvider(
                FixedCredentialsProvider.create(CredentialsService().obtainCredentials()),
            ).build()
    private val client: TextToSpeechClient = TextToSpeechClient.create(clientSettings)
    var languageCode: String = DEFAULT_LANGUAGE_CODE
        private set
    var voiceName: String = DEFAULT_VOICE_NAME
        private set
    var pitch: Double = DEFAULT_PITCH
        set(value) {
            updateAudioConfig(newPitch = value)
            field = value
        }
    var speed: Double = DEFAULT_SPEED
        set(value) {
            updateAudioConfig(newSpeed = value)
            field = value
        }
    private lateinit var audioConfig: AudioConfig
    private lateinit var voiceSelectionParams: VoiceSelectionParams

    init {
        selectVoice(languageCode, voiceName)
        updateAudioConfig(speed, pitch)
    }

    fun selectVoice(
        languageCode: String = DEFAULT_LANGUAGE_CODE,
        voiceName: String = DEFAULT_VOICE_NAME,
    ) {
        require(isVoiceExists(languageCode, voiceName)) { "Can't find $voiceName in $languageCode" }

        voiceSelectionParams =
            VoiceSelectionParams
                .newBuilder()
                .setLanguageCode(languageCode)
                .setName(voiceName)
                .build()
    }

    fun synthesizeSpeech(text: String): ByteArray {
        val input = SynthesisInput.newBuilder().setText(text).build()
        val request =
            SynthesizeSpeechRequest
                .newBuilder()
                .setInput(input)
                .setVoice(voiceSelectionParams)
                .setAudioConfig(audioConfig)
                .build()
        return client.synthesizeSpeech(request).audioContent.toByteArray()
    }

    fun fetchListVoiceNames(languageCode: String = DEFAULT_LANGUAGE_CODE): List<String> = fetchListVoices(languageCode).map { it.name }

    private fun updateAudioConfig(
        newSpeed: Double = speed,
        newPitch: Double = pitch,
    ) {
        audioConfig =
            AudioConfig
                .newBuilder()
                .setAudioEncoding(AudioEncoding.MP3)
                .setPitch(newPitch)
                .setSpeakingRate(newSpeed)
                .build()
    }

    private fun isVoiceExists(
        languageCode: String,
        voiceName: String,
    ): Boolean = fetchListVoices(languageCode).any { it.name == voiceName }

    private fun fetchListVoices(languageCode: String = DEFAULT_LANGUAGE_CODE): List<Voice> = client.listVoices(languageCode).voicesList
}
