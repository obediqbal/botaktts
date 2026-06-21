package dev.botak.core.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests for [SemVer] parsing and ordering. */
class SemVerTest {
    @Test
    fun `parse reads plain version`() {
        assertEquals(SemVer(1, 0, 10), SemVer.parse("1.0.10"))
    }

    @Test
    fun `parse accepts leading v`() {
        assertEquals(SemVer(1, 1, 0), SemVer.parse("v1.1.0"))
    }

    @Test
    fun `parse throws on malformed input`() {
        assertFailsWith<IllegalArgumentException> { SemVer.parse("1.0") }
        assertFailsWith<IllegalArgumentException> { SemVer.parse("not-a-version") }
        assertFailsWith<IllegalArgumentException> { SemVer.parse("1.0.x") }
    }

    @Test
    fun `parseOrNull returns null on malformed input`() {
        assertNull(SemVer.parseOrNull("1.0"))
        assertNull(SemVer.parseOrNull("garbage"))
    }

    @Test
    fun `compareTo orders numerically not lexically`() {
        assertTrue(SemVer.parse("1.0.10") > SemVer.parse("1.0.9"))
    }

    @Test
    fun `compareTo respects precedence`() {
        assertTrue(SemVer.parse("2.0.0") > SemVer.parse("1.9.9"))
        assertTrue(SemVer.parse("1.2.0") > SemVer.parse("1.1.9"))
        assertEquals(0, SemVer.parse("1.2.3").compareTo(SemVer.parse("1.2.3")))
    }

    @Test
    fun `toString round-trips`() {
        assertEquals("1.0.10", SemVer(1, 0, 10).toString())
    }
}
