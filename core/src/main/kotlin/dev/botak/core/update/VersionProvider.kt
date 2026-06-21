package dev.botak.core.update

import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Resolves the running application's own version from a bundled `version.properties` resource
 * (written into the client jar at build time; see `client/build.gradle.kts`).
 *
 * When the resource is absent or unparseable — e.g. running from `:core:run` or `:client:run`
 * in dev where it may not be generated — a sentinel [SemVer] of `0.0.0` is returned and a warning
 * logged. A dev build then treats every remote release as "newer", which is harmless for the
 * manual-only check.
 *
 * @param resourcePath Classpath location of the properties file. Overridable for tests.
 */
class VersionProvider(
    private val resourcePath: String = "/version.properties",
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VersionProvider::class.java)

        /** Returned when no usable version can be read. */
        private val UNKNOWN = SemVer(0, 0, 0)
    }

    /**
     * Reads `version=<X.Y.Z>` from [resourcePath] and parses it to a [SemVer].
     *
     * @return the parsed version, or `0.0.0` if the resource is missing or malformed.
     */
    fun currentVersion(): SemVer {
        val stream =
            javaClass.getResourceAsStream(resourcePath) ?: run {
                LOGGER.warn("version resource '$resourcePath' not found; reporting $UNKNOWN")
                return UNKNOWN
            }
        val raw =
            stream.use { input ->
                Properties().apply { load(input) }.getProperty("version")
            }
        val parsed = raw?.let { SemVer.parseOrNull(it) }
        if (parsed == null) {
            LOGGER.warn("version resource '$resourcePath' had no usable version ('$raw'); reporting $UNKNOWN")
            return UNKNOWN
        }
        return parsed
    }
}
