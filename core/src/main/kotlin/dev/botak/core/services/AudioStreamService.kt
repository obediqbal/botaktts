package dev.botak.core.services

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.min

class AudioStreamService {
    fun streamToVirtualAudio(
        audioData: ByteArray,
        sampleRate: Float,
        virtualAudioName: String = "CABLE Input (VB-Audio Virtual Cable)",
        channels: Int = 1,
    ) {
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

        val bufferSize = 4096
        var offset = 0
        while (offset < audioData.size) {
            val length = min(bufferSize, audioData.size - offset)
            line.write(audioData, offset, length)
            offset += length
        }

        line.drain()
        line.stop()
        line.close()
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
