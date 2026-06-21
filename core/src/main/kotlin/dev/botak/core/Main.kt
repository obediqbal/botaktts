package dev.botak.core

import com.google.cloud.texttospeech.v1.*
import dev.botak.core.services.AudioStreamService
import dev.botak.core.services.TTSService
import java.io.File
import java.util.*

/**
 * Ad-hoc entry point used for manual testing of speech synthesis.
 *
 * Synthesizes a fixed sample sentence, writes the resulting audio to `output.wav`, and prints the
 * configured sample rate. Not intended for production use.
 */
fun main() {
    val ttsService = TTSService()
    val response = ttsService.synthesizeSpeech("Oneeddd coba liat ini. Ada tulisan don't fall down")

    // 6. Save audio to file
    val output = File("output.wav")
    output.writeBytes(response)
    println("✅ Audio content written to file: ${output.absolutePath}")

    val audioStreamService = AudioStreamService()
    println(ttsService.sampleRateHz.toFloat())
//    audioStreamService.streamToVirtualAudio(response, ttsService.sampleRateHz.toFloat())
}
