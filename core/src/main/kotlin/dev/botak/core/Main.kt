package dev.botak.core

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.texttospeech.v1.*
import com.typesafe.config.ConfigFactory
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.CredentialsService
import dev.botak.core.services.TTSService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.*

fun main() {
    val ttsService = TTSService()
    val response = ttsService.synthesizeSpeech("Oneeddd coba liat ini. Ada tulisan don't fall down")

    // 6. Save audio to file
    val output = File("output.wav")
    output.writeBytes(response)
    println("✅ Audio content written to file: ${output.absolutePath}")

    val audioStreamService = AudioStreamService()
    println(ttsService.sampleRateHz.toFloat())
    audioStreamService.streamToVirtualAudio(response, ttsService.sampleRateHz.toFloat())
}
