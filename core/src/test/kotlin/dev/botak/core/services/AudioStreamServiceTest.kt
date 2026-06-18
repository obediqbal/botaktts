package dev.botak.core.services

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Unit tests for [AudioStreamService].
 *
 * Covers the volume-factor validation, the pure DSP logic in [AudioStreamService.applyGain],
 * the [AudioStreamService.createAudioFormat] factory, the settings persistence path via
 * [ConfigService], and the virtual-audio-not-found error path in
 * [AudioStreamService.streamToVirtualAudio].
 *
 * No real audio hardware or network is touched: [AudioSystem] statics are mocked where needed,
 * and [ConfigService] is mocked via `mockkObject` so no real `settings.json` file is written.
 */
class AudioStreamServiceTest {
    /** Service under test, created fresh for each test to isolate the [AudioStreamService.volumeFactor] state. */
    private val service = AudioStreamService()

    /**
     * Asserts no mocking leaks across tests: [ConfigService] may be mocked by individual tests
     * via `mockkObject`, so we unmock it here to keep the global state clean.
     */
    @AfterTest
    fun tearDown() {
        unmockkObject(ConfigService)
    }

    // ---------- volumeFactor validation ----------

    /** The lower bound `0.0` is accepted (inclusive). */
    @Test
    fun `volumeFactor accepts 0_0 lower bound inclusive`() {
        service.volumeFactor = 0.0f
        assertEquals(0.0f, service.volumeFactor)
    }

    /** The upper bound `2.0` is accepted (inclusive). */
    @Test
    fun `volumeFactor accepts 2_0 upper bound inclusive`() {
        service.volumeFactor = 2.0f
        assertEquals(2.0f, service.volumeFactor)
    }

    /** The neutral gain `1.0` is accepted. */
    @Test
    fun `volumeFactor accepts 1_0`() {
        service.volumeFactor = 1.0f
        assertEquals(1.0f, service.volumeFactor)
    }

    /** A value just below the lower bound (`-0.1`) is rejected with `IllegalArgumentException`. */
    @Test
    fun `volumeFactor rejects -0_1`() {
        assertFailsWith<IllegalArgumentException> {
            service.volumeFactor = -0.1f
        }
    }

    /** A value just above the upper bound (`2.1`) is rejected with `IllegalArgumentException`. */
    @Test
    fun `volumeFactor rejects 2_1`() {
        assertFailsWith<IllegalArgumentException> {
            service.volumeFactor = 2.1f
        }
    }

    /** `NaN` is rejected with `IllegalArgumentException` (NaN is not in the closed range). */
    @Test
    fun `volumeFactor rejects NaN`() {
        assertFailsWith<IllegalArgumentException> {
            service.volumeFactor = Float.NaN
        }
    }

    /** `PositiveInfinity` is rejected with `IllegalArgumentException`. */
    @Test
    fun `volumeFactor rejects PositiveInfinity`() {
        assertFailsWith<IllegalArgumentException> {
            service.volumeFactor = Float.POSITIVE_INFINITY
        }
    }

    // ---------- applyGain DSP ----------

    /**
     * Encodes a list of signed 16-bit samples into a little-endian [ByteArray], two bytes per
     * sample (low byte first, high byte second).
     *
     * @param samples The 16-bit PCM samples to encode.
     * @return the little-endian byte buffer containing the encoded samples.
     */
    private fun encodeSamples(samples: List<Short>): ByteArray {
        val buffer = ByteArray(samples.size * 2)
        samples.forEachIndexed { index, sample ->
            val asInt = sample.toInt()
            buffer[index * 2] = (asInt and 0xFF).toByte()
            buffer[index * 2 + 1] = ((asInt shr 8) and 0xFF).toByte()
        }
        return buffer
    }

    /**
     * Decodes a little-endian [ByteArray] back into a list of signed 16-bit samples.
     *
     * @param buffer The PCM byte buffer; length is assumed even.
     * @return the decoded samples.
     */
    private fun decodeSamples(buffer: ByteArray): List<Short> {
        val samples = ArrayList<Short>(buffer.size / 2)
        var i = 0
        while (i < buffer.size - 1) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            samples.add(((high shl 8) or low).toShort())
            i += 2
        }
        return samples
    }

    /** A gain of `1.0` leaves every sample of a known buffer unchanged (identity). */
    @Test
    fun `applyGain with gain 1_0 is identity`() {
        val input = encodeSamples(listOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE, 12345))
        service.applyGain(input, 1.0f)
        assertEquals(listOf(0, 1000, -1000, Short.MAX_VALUE, Short.MIN_VALUE, 12345), decodeSamples(input))
    }

    /** A gain of `0.0` zeroes every sample in the buffer. */
    @Test
    fun `applyGain with gain 0_0 zeroes every sample`() {
        val input = encodeSamples(listOf(1000, -1000, 12345, -12345))
        service.applyGain(input, 0.0f)
        assertEquals(listOf<Short>(0, 0, 0, 0), decodeSamples(input))
    }

    /** A gain of `2.0` doubles small samples (1000 -> 2000) with correct little-endian encoding. */
    @Test
    fun `applyGain with gain 2_0 doubles small samples`() {
        val input = encodeSamples(listOf(1000, -1000, 500))
        service.applyGain(input, 2.0f)
        assertEquals(listOf<Short>(2000, -2000, 1000), decodeSamples(input))
        // Explicitly verify the little-endian byte encoding of 2000 = 0x07D0 (low=0xD0, high=0x07),
        // which lives in the first sample at byte indices 0 (low) and 1 (high).
        assertEquals(0xD0.toByte(), input[0])
        assertEquals(0x07.toByte(), input[1])
    }

    /** A positive sample that overflows when scaled (30000 * 2 = 60000) clamps to [Short.MAX_VALUE]. */
    @Test
    fun `applyGain clamps positive overflow to Short MAX_VALUE`() {
        val input = encodeSamples(listOf(30000))
        service.applyGain(input, 2.0f)
        assertEquals(listOf(Short.MAX_VALUE), decodeSamples(input))
    }

    /** A negative sample that overflows when scaled (-30000 * 2 = -60000) clamps to [Short.MIN_VALUE]. */
    @Test
    fun `applyGain clamps negative overflow to Short MIN_VALUE`() {
        val input = encodeSamples(listOf(-30000))
        service.applyGain(input, 2.0f)
        assertEquals(listOf(Short.MIN_VALUE), decodeSamples(input))
    }

    /**
     * Little-endian decoding/encoding is correct: a buffer built from explicit (low, high) bytes
     * for a known sample value is decoded, scaled, and re-encoded to the expected bytes.
     */
    @Test
    fun `applyGain preserves little-endian encoding for a known sample`() {
        // Sample 0x0102 = 258: low=0x02, high=0x01. After gain 1.0 it must be unchanged.
        val input = byteArrayOf(0x02, 0x01)
        service.applyGain(input, 1.0f)
        assertEquals(0x02.toByte(), input[0])
        assertEquals(0x01.toByte(), input[1])
        assertEquals(listOf<Short>(258), decodeSamples(input))
    }

    /** On an odd-length buffer the final unpaired byte is left untouched. */
    @Test
    fun `applyGain on odd-length buffer leaves the final byte unchanged`() {
        // 2 bytes for the 1000 sample + 1 trailing odd byte = 3 bytes total (odd length).
        val input = encodeSamples(listOf(1000)) + byteArrayOf(0x77.toByte())
        service.applyGain(input, 2.0f)
        assertEquals(listOf<Short>(2000), decodeSamples(input))
        // The trailing odd byte is unpaired and must be untouched.
        assertEquals(0x77.toByte(), input[input.size - 1])
        assertEquals(3, input.size)
    }

    /** An empty buffer is a no-op: [AudioStreamService.applyGain] must not throw. */
    @Test
    fun `applyGain on empty buffer does not throw`() {
        val input = ByteArray(0)
        service.applyGain(input, 1.0f)
        assertEquals(0, input.size)
    }

    /** Negative samples keep their sign under a gain of `1.0`. */
    @Test
    fun `applyGain preserves negative sign with gain 1_0`() {
        val input = encodeSamples(listOf(-1, -32767, -100, -256))
        service.applyGain(input, 1.0f)
        assertEquals(listOf<Short>(-1, -32767, -100, -256), decodeSamples(input))
    }

    // ---------- createAudioFormat ----------

    /** The format built for 24 kHz mono matches every documented field of the service's PCM layout. */
    @Test
    fun `createAudioFormat produces correct 24kHz mono format`() {
        val format = service.createAudioFormat(24000f, 1)
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.encoding)
        assertEquals(24000f, format.sampleRate)
        assertEquals(16, format.sampleSizeInBits)
        assertEquals(1, format.channels)
        assertEquals(2, format.frameSize)
        assertEquals(24000f, format.frameRate)
        assertFalse(format.isBigEndian)
    }

    /** The format built for 44.1 kHz stereo has the right rate, channels and doubled frame size. */
    @Test
    fun `createAudioFormat produces correct 44_1kHz stereo format`() {
        val format = service.createAudioFormat(44100f, 2)
        assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.encoding)
        assertEquals(44100f, format.sampleRate)
        assertEquals(16, format.sampleSizeInBits)
        assertEquals(2, format.channels)
        assertEquals(4, format.frameSize)
        assertEquals(44100f, format.frameRate)
        assertFalse(format.isBigEndian)
    }

    /** `frameSize == channels * 2` and `frameRate == sampleRate` hold for an arbitrary channel count. */
    @Test
    fun `createAudioFormat frameSize and frameRate follow channels and sampleRate`() {
        val format = service.createAudioFormat(16000f, 2)
        assertEquals(format.channels * 2, format.frameSize)
        assertEquals(format.sampleRate, format.frameRate)
    }

    // ---------- persistVolumeFactor ----------

    /**
     * `persistVolumeFactor()` writes the current factor into the persisted user settings and
     * calls [ConfigService.saveUserSettings].
     *
     * [ConfigService] is mocked via `mockkObject` so `saveUserSettings()` is a no-op and no real
     * `settings.json` file is written to disk. The service captured its `USER_SETTINGS` reference
     * at class-load time from the real [ConfigService.userSettings]; we capture that same instance
     * before mocking and stub the mocked getter to return it, so the mutation performed by
     * `persistVolumeFactor()` is observable through the stubbed getter.
     */
    @Test
    fun `persistVolumeFactor writes volume to userSettings and saves`() {
        // Capture the real shared UserSettings instance before the object mock takes over the getter.
        val sharedSettings = ConfigService.userSettings
        mockkObject(ConfigService)
        every { ConfigService.userSettings } returns sharedSettings
        every { ConfigService.saveUserSettings() } just Runs

        service.volumeFactor = 1.5f
        service.persistVolumeFactor()

        assertEquals(1.5f, ConfigService.userSettings.volume)
        verify { ConfigService.saveUserSettings() }
    }

    /**
     * `persistVolumeFactor()` persists the lower-bound volume `0.0` correctly and calls
     * [ConfigService.saveUserSettings] exactly once.
     */
    @Test
    fun `persistVolumeFactor persists 0_0`() {
        val sharedSettings = ConfigService.userSettings
        mockkObject(ConfigService)
        every { ConfigService.userSettings } returns sharedSettings
        every { ConfigService.saveUserSettings() } just Runs

        service.volumeFactor = 0.0f
        service.persistVolumeFactor()

        assertEquals(0.0f, ConfigService.userSettings.volume)
        verify(exactly = 1) { ConfigService.saveUserSettings() }
    }

    // ---------- streaming error path ----------

    /**
     * When [AudioSystem] reports no mixers at all, [AudioStreamService.streamToVirtualAudio]
     * throws `IllegalStateException("Virtual Audio not found")` rather than returning a line.
     *
     * `AudioSystem` is mocked statically to return an empty mixer-info array; the coroutine body
     * runs inside `runTest` so the suspend call resolves synchronously.
     */
    @Test
    fun `streamToVirtualAudio throws when no mixer matches`() = runTest {
        mockkStatic(AudioSystem::class)
        every { AudioSystem.getMixerInfo() } returns emptyArray()

        val exception =
            assertFailsWith<IllegalStateException> {
                service.streamToVirtualAudio(ByteArray(0), 24000f)
            }
        assertEquals("Virtual Audio not found", exception.message)

        unmockkStatic(AudioSystem::class)
    }

    // ---------- streamAudio resource cleanup (intentionally not covered) ----------
    //
    // `streamAudio`'s line lifecycle (open/start/write/drain/stop/close) is entangled with a
    // real `javax.sound.sampled.SourceDataLine` and the `Dispatchers.IO` thread pool. Mocking a
    // `SourceDataLine` end-to-end while keeping `withContext(Dispatchers.IO)` cooperative inside
    // `runTest` is fragile: `SourceDataLine.write` is a blocking call that `runTest`'s virtual
    // time does not control, and partial mocks of a final JDK class are brittle across JVMs.
    // The `IllegalStateException` error path above already covers the only non-DSP branch that
    // does not depend on a live line, so this resource-cleanup path is intentionally left
    // uncovered rather than guarded by a flaky disabled test (JUnit Jupiter's `@Disabled` is not
    // on the test classpath, so a disabled test could not be expressed portably here).
}