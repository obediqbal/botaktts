package dev.botak.core.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.min

class AudioStreamService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AudioStreamService::class.java)
    }

    var volumeFactor: Float = 1.0f

    suspend fun streamToVirtualAudio(
        audioData: ByteArray,
        sampleRate: Float,
        virtualAudioName: String = "CABLE Input (VB-Audio Virtual Cable)",
        channels: Int = 1,
    ) = withContext(Dispatchers.IO) {
        LOGGER.debug("Streaming audio data to $virtualAudioName...")
        val format =
            AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false,
            )

        val mixerInfo =
            AudioSystem.getMixerInfo().firstOrNull { it.name.contains(virtualAudioName) }
                ?: throw IllegalStateException("Virtual Audio not found")

        val mixer = AudioSystem.getMixer(mixerInfo)
        val lineInfo = DataLine.Info(SourceDataLine::class.java, format)
        val line = mixer.getLine(lineInfo) as SourceDataLine

        line.open(format)
        line.start()

        try {
            val bufferSize = 4096
            var offset = 0
            while (offset < audioData.size) {
                val length = min(bufferSize, audioData.size - offset)
                val chunk = audioData.copyOfRange(offset, offset + length)

                applyGain(chunk, volumeFactor)
                line.write(chunk, 0, chunk.size)

                offset += length
                ensureActive()
            }
        } finally {
            line.drain()
            line.stop()
            line.close()
            LOGGER.debug("Completed streaming audio data")
        }
    }

    private fun applyGain(
        audioData: ByteArray,
        gain: Float,
    ) {
        var i = 0
        while (i < audioData.size - 1) {
            // Convert 2 bytes (little endian) -> 16-bit sample
            val low = audioData[i].toInt() and 0xFF
            val high = audioData[i + 1].toInt()
            var sample = (high shl 8) or low

            // Scale
            sample = (sample * gain).toInt()

            // Clamp to 16-bit signed range
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE.toInt()
            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE.toInt()

            // Back to bytes
            audioData[i] = (sample and 0xFF).toByte()
            audioData[i + 1] = ((sample shr 8) and 0xFF).toByte()

            i += 2
        }
    }
}

fun main() {
    val mixerInfo = AudioSystem.getMixerInfo()
    for (info in mixerInfo) {
        val mixer = AudioSystem.getMixer(info)
        val lineInfo = mixer.getSourceLineInfo()
        for (line in lineInfo) {
            if (line is DataLine.Info) {
                val dataLineInfo = line
                val formats = dataLineInfo.getFormats()
                for (format in formats) {
                    println("Mixer: " + info.getName() + ", Format: " + format + " Sample rate: ")
                }
            }
        }
    }
}
