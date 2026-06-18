package dev.botak.core.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.min

/**
 * Streams PCM audio data to an audio output line using the Java Sound API.
 *
 * Audio is played back in 16-bit signed little-endian PCM, chunked through a [SourceDataLine] and
 * volume-adjusted in place via [applyGain] before being written. The service supports streaming
 * either to the system default speakers or to a named virtual audio device (e.g. VB-Audio Virtual
 * Cable) so the output can be captured by voice chat applications as microphone input.
 *
 * Playback runs on [Dispatchers.IO] and is cancellable: streaming checks coroutine activity
 * between chunks, so cancelling the calling coroutine stops playback promptly.
 */
class AudioStreamService {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AudioStreamService::class.java)
        private val USER_SETTINGS = ConfigService.userSettings
    }

    /**
     * Linear volume gain applied to each audio chunk before playback.
     * Must be in the range `0.0..2.0` (`1.0` is the original volume).
     */
    var volumeFactor: Float = USER_SETTINGS.volume
        set(value) {
            require(value in 0.0..2.0) {
                "Volume factor must be between 0.0 and 2.0"
            }
            LOGGER.debug("Volume factor set to $value")
            field = value
        }

    /** Persists the current [volumeFactor] to user settings. */
    fun persistVolumeFactor() {
        USER_SETTINGS.volume = volumeFactor
        ConfigService.saveUserSettings()
    }

    /**
     * Builds a 16-bit signed PCM [AudioFormat] for the given [sampleRate] and channel count.
     *
     * @param sampleRate Sample rate in hertz.
     * @param channels Number of audio channels (1 for mono, 2 for stereo).
     * @return the configured [AudioFormat].
     */
    internal fun createAudioFormat(
        sampleRate: Float,
        channels: Int,
    ): AudioFormat =
        AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,
            channels,
            channels * 2,
            sampleRate,
            false,
        )

    /**
     * Streams the given [audioData] to the system default audio output (speakers).
     *
     * @param audioData Raw 16-bit signed little-endian PCM audio bytes.
     * @param sampleRate Sample rate of the audio in hertz.
     * @param channels Number of audio channels; defaults to mono (`1`).
     */
    suspend fun streamToSpeakers(
        audioData: ByteArray,
        sampleRate: Float,
        channels: Int = 1,
    ) = withContext(Dispatchers.IO) {
        val format = createAudioFormat(sampleRate, channels)

        // Get default system audio output line
        val lineInfo = DataLine.Info(SourceDataLine::class.java, format)
        val line = AudioSystem.getLine(lineInfo) as SourceDataLine
        line.open(format)

        streamAudio(line, audioData, "to speakers")
    }

    /**
     * Writes [audioData] to [sourceLine] in fixed-size chunks, applying the current
     * [volumeFactor] to each chunk and yielding to coroutine cancellation between writes.
     *
     * The line is stopped and closed in a `finally` block so resources are released even if the
     * coroutine is cancelled. On normal completion the line is drained so all buffered audio
     * finishes playing; on cancellation the buffer is flushed so playback stops promptly instead
     * of continuing until the Java Sound buffer empties.
     *
     * @param sourceLine The open line to write to.
     * @param audioData The PCM audio bytes to play.
     * @param logMessage Descriptor used in debug log messages.
     */
    private suspend fun streamAudio(
        sourceLine: SourceDataLine,
        audioData: ByteArray,
        logMessage: String,
    ) = withContext(Dispatchers.IO) {
        LOGGER.debug("Streaming audio data $logMessage")

        sourceLine.start()

        try {
            val bufferSize = 4096
            var offset = 0
            while (offset < audioData.size) {
                val length = min(bufferSize, audioData.size - offset)
                val chunk = audioData.copyOfRange(offset, offset + length)

                applyGain(chunk, volumeFactor)
                sourceLine.write(chunk, 0, chunk.size)

                offset += length
                ensureActive()
            }
        } finally {
            if (coroutineContext.isActive) {
                // Normal completion: wait for queued audio to finish playing.
                sourceLine.drain()
            } else {
                // Cancelled: discard buffered audio so playback stops promptly.
                sourceLine.flush()
            }
            sourceLine.stop()
            sourceLine.close()
            LOGGER.debug("Completed streaming audio data $logMessage")
        }
    }

    /**
     * Streams the given [audioData] to a virtual audio device so it can be picked up as microphone
     * input by voice chat applications.
     *
     * @param audioData Raw 16-bit signed little-endian PCM audio bytes.
     * @param sampleRate Sample rate of the audio in hertz.
     * @param virtualAudioName Substring used to match the target mixer by name; defaults to the
     *   VB-Audio Virtual Cable input.
     * @param channels Number of audio channels; defaults to mono (`1`).
     * @throws IllegalStateException if no mixer matching [virtualAudioName] is found.
     */
    suspend fun streamToVirtualAudio(
        audioData: ByteArray,
        sampleRate: Float,
        virtualAudioName: String = "CABLE Input (VB-Audio Virtual Cable)",
        channels: Int = 1,
    ) = withContext(Dispatchers.IO) {
        val format = createAudioFormat(sampleRate, channels)

        val mixerInfo =
            AudioSystem.getMixerInfo().firstOrNull { it.name.contains(virtualAudioName) }
                ?: throw IllegalStateException("Virtual Audio not found")

        val mixer = AudioSystem.getMixer(mixerInfo)
        val lineInfo = DataLine.Info(SourceDataLine::class.java, format)
        val line = mixer.getLine(lineInfo) as SourceDataLine
        line.open(format)

        streamAudio(line, audioData, "to $virtualAudioName")
    }

    /**
     * Applies a linear [gain] to [audioData] in place by decoding each 16-bit little-endian
     * sample, scaling it, clamping to the signed 16-bit range, and re-encoding it.
     *
     * Marked `internal` so unit tests can exercise the DSP logic directly without going through
     * the Java Sound line machinery. Behavior is unchanged.
     *
     * @param audioData The PCM byte buffer to mutate; length must be even.
     * @param gain The linear gain factor to apply.
     */
    internal fun applyGain(
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
