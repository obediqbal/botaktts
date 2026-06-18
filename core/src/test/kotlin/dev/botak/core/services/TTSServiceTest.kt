package dev.botak.core.services

import com.google.api.gax.rpc.ApiException
import com.google.api.gax.rpc.StatusCode
import com.google.cloud.texttospeech.v1.AudioEncoding
import com.google.cloud.texttospeech.v1.Voice
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the pure companion-object helpers extracted from [TTSService].
 *
 * These tests exercise validation, audio-config construction, voice filtering, language-code
 * deduplication, voice lookup and pitch-retry classification without instantiating [TTSService]
 * (whose `init` requires live OAuth credentials and a network call to the Google Cloud TTS API).
 */
class TTSServiceTest {
    /**
     * Builds a [Voice] proto with the given [name] and language [codes].
     *
     * @param name the voice name.
     * @param codes the BCP-47 language codes the voice supports.
     * @return the constructed [Voice].
     */
    private fun voice(
        name: String,
        codes: List<String>,
    ): Voice =
        Voice
            .newBuilder()
            .setName(name)
            .addAllLanguageCodes(codes)
            .build()

    /**
     * Builds a minimal little-endian PCM WAV file around [pcmData].
     *
     * @param pcmData the raw PCM data chunk bytes.
     * @return a valid RIFF/WAVE byte stream containing [pcmData].
     */
    private fun wavBytes(pcmData: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        output.writeIntLE(36 + pcmData.size)
        output.write("WAVE".toByteArray(Charsets.US_ASCII))
        output.write("fmt ".toByteArray(Charsets.US_ASCII))
        output.writeIntLE(16)
        output.writeShortLE(1)
        output.writeShortLE(1)
        output.writeIntLE(24_000)
        output.writeIntLE(24_000 * 2)
        output.writeShortLE(2)
        output.writeShortLE(16)
        output.write("data".toByteArray(Charsets.US_ASCII))
        output.writeIntLE(pcmData.size)
        output.write(pcmData)
        return output.toByteArray()
    }

    /** Writes [value] as a little-endian 32-bit integer. */
    private fun ByteArrayOutputStream.writeIntLE(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    /** Writes [value] as a little-endian 16-bit integer. */
    private fun ByteArrayOutputStream.writeShortLE(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }

    @Test
    fun `decodeLinear16AudioContent strips WAV header and returns only PCM payload`() {
        val pcmData = byteArrayOf(0x34, 0x12, 0x78, 0x56, 0x00, 0x00)
        val wavData = wavBytes(pcmData)

        val decoded = TTSService.decodeLinear16AudioContent(wavData)

        assertEquals(pcmData.toList(), decoded.toList())
    }

    @Test
    fun `validatePitch accepts boundary and valid in-range values`() {
        // Should not throw.
        TTSService.validatePitch(-20.0)
        TTSService.validatePitch(20.0)
        TTSService.validatePitch(0.0)
        TTSService.validatePitch(10.5)
        TTSService.validatePitch(-10.5)
    }

    @Test
    fun `validatePitch rejects out-of-range and non-finite values`() {
        listOf(-20.1, 20.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            val ex =
                kotlin.test.assertFailsWith<IllegalArgumentException> {
                    TTSService.validatePitch(value)
                }
            assertTrue(
                ex.message?.contains("Pitch must be between") == true,
                "Expected pitch error for $value, got: ${ex.message}",
            )
        }
    }

    @Test
    fun `validateSpeed accepts boundary and valid in-range values`() {
        // Should not throw.
        TTSService.validateSpeed(0.25)
        TTSService.validateSpeed(4.0)
        TTSService.validateSpeed(1.0)
        TTSService.validateSpeed(2.0)
    }

    @Test
    fun `validateSpeed rejects out-of-range and non-finite values`() {
        listOf(0.24, 4.1, 0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            val ex =
                kotlin.test.assertFailsWith<IllegalArgumentException> {
                    TTSService.validateSpeed(value)
                }
            assertTrue(
                ex.message?.contains("Speed must be between") == true,
                "Expected speed error for $value, got: ${ex.message}",
            )
        }
    }

    @Test
    fun `buildAudioConfig sets LINEAR16 encoding, pitch, rate and default sample rate`() {
        val config = TTSService.buildAudioConfig(speed = 1.5, pitch = -3.0)

        assertEquals(AudioEncoding.LINEAR16, config.audioEncoding)
        assertEquals(-3.0, config.pitch)
        assertEquals(1.5, config.speakingRate)
        // The default sample rate is sourced from application.conf `defaults.sampleRate` (24000).
        assertEquals(ConfigService.getInt("defaults.sampleRate"), config.sampleRateHertz)
    }

    @Test
    fun `buildAudioConfig honors a custom sample rate of 48000`() {
        val config = TTSService.buildAudioConfig(speed = 2.0, pitch = 5.0, sampleRateHertz = 48000)

        assertEquals(AudioEncoding.LINEAR16, config.audioEncoding)
        assertEquals(5.0, config.pitch)
        assertEquals(2.0, config.speakingRate)
        assertEquals(48000, config.sampleRateHertz)
    }

    @Test
    fun `buildAudioConfig with 24000 sample rate produces the expected fields`() {
        val config = TTSService.buildAudioConfig(speed = 1.0, pitch = 0.0, sampleRateHertz = 24000)

        assertEquals(AudioEncoding.LINEAR16, config.audioEncoding)
        assertEquals(0.0, config.pitch)
        assertEquals(1.0, config.speakingRate)
        assertEquals(24000, config.sampleRateHertz)
    }

    @Test
    fun `filterVoicesByLanguage returns only voices supporting the given code`() {
        val usOnly = voice("us-only", listOf("en-US"))
        val gbOnly = voice("gb-only", listOf("en-GB"))
        val both = voice("both", listOf("en-US", "en-GB"))
        val voices = listOf(usOnly, gbOnly, both)

        val us = TTSService.filterVoicesByLanguage(voices, "en-US")
        assertEquals(listOf(usOnly, both), us)

        val gb = TTSService.filterVoicesByLanguage(voices, "en-GB")
        assertEquals(listOf(gbOnly, both), gb)
    }

    @Test
    fun `filterVoicesByLanguage returns empty when no voice matches`() {
        val voices =
            listOf(
                voice("a", listOf("en-US")),
                voice("b", listOf("en-GB")),
            )

        assertTrue(TTSService.filterVoicesByLanguage(voices, "id-ID").isEmpty())
    }

    @Test
    fun `distinctLanguageCodes returns each code once regardless of order`() {
        val voices =
            listOf(
                voice("a", listOf("en-US", "en-GB")),
                voice("b", listOf("en-US")),
                voice("c", listOf("id-ID")),
            )

        val codes = TTSService.distinctLanguageCodes(voices)

        assertEquals(3, codes.size)
        assertTrue(codes.toSet() == setOf("en-US", "en-GB", "id-ID"))
    }

    @Test
    fun `distinctLanguageCodes returns empty for an empty voice list`() {
        assertTrue(TTSService.distinctLanguageCodes(emptyList()).isEmpty())
    }

    @Test
    fun `findVoiceByName returns the matching voice`() {
        val a = voice("A", listOf("en-US"))
        val b = voice("B", listOf("en-US"))
        val c = voice("C", listOf("en-US"))

        assertEquals(b, TTSService.findVoiceByName(listOf(a, b, c), "B"))
    }

    @Test
    fun `findVoiceByName returns null when no voice matches`() {
        val voices =
            listOf(
                voice("A", listOf("en-US")),
                voice("B", listOf("en-US")),
                voice("C", listOf("en-US")),
            )

        assertNull(TTSService.findVoiceByName(voices, "Z"))
    }

    @Test
    fun `findVoiceByName returns the first match when duplicates exist`() {
        val first = voice("A", listOf("en-US"))
        val second = voice("A", listOf("en-GB"))
        val voices = listOf(first, second)

        val result = TTSService.findVoiceByName(voices, "A")
        assertEquals(first, result)
        // Identity check: the first occurrence is returned, not the second.
        assertTrue(result === first)
    }

    @Test
    fun `shouldRetryWithNeutralPitch returns true for the exact unsupported-pitch error`() {
        val e = mockk<ApiException>(relaxed = true)
        every { e.statusCode } returns
            mockk {
                every { code } returns StatusCode.Code.INVALID_ARGUMENT
            }
        every { e.message } returns
            "io.grpc.StatusRuntimeException: INVALID_ARGUMENT: This voice does not support pitch parameters at this time."

        assertTrue(TTSService.shouldRetryWithNeutralPitch(e))
    }

    @Test
    fun `shouldRetryWithNeutralPitch returns false for INVALID_ARGUMENT with a different message`() {
        val e = mockk<ApiException>(relaxed = true)
        every { e.statusCode } returns
            mockk {
                every { code } returns StatusCode.Code.INVALID_ARGUMENT
            }
        every { e.message } returns "Some other invalid argument error"

        assertTrue(!TTSService.shouldRetryWithNeutralPitch(e))
    }

    @Test
    fun `shouldRetryWithNeutralPitch returns false for a non-INVALID_ARGUMENT code`() {
        val e = mockk<ApiException>(relaxed = true)
        every { e.statusCode } returns
            mockk {
                every { code } returns StatusCode.Code.UNAVAILABLE
            }
        every { e.message } returns
            "io.grpc.StatusRuntimeException: INVALID_ARGUMENT: This voice does not support pitch parameters at this time."

        assertTrue(!TTSService.shouldRetryWithNeutralPitch(e))
    }
}
