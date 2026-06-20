package dev.botak.core.update

import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit tests for [VersionProvider]. */
class VersionProviderTest {
    @Test
    fun `currentVersion reads bundled version properties`() {
        val provider = VersionProvider("/version.properties")
        assertEquals(SemVer(2, 5, 7), provider.currentVersion())
    }

    @Test
    fun `currentVersion falls back to zero when resource missing`() {
        val provider = VersionProvider("/does-not-exist.properties")
        assertEquals(SemVer(0, 0, 0), provider.currentVersion())
    }
}