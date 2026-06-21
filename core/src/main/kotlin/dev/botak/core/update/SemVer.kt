package dev.botak.core.update

/**
 * A semantic version (`MAJOR.MINOR.PATCH`) with strictly numeric ordering, so `1.0.10` sorts
 * after `1.0.9`.
 *
 * Pre-release and build-metadata suffixes are out of scope; release tags are clean `vX.Y.Z`.
 *
 * @property major Major version component.
 * @property minor Minor version component.
 * @property patch Patch version component.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    /** Compares by [major], then [minor], then [patch]. */
    override fun compareTo(other: SemVer): Int = compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch)

    /** Renders as `"MAJOR.MINOR.PATCH"` (e.g. `"1.0.10"`). */
    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        /**
         * Parses `"1.0.10"` or `"v1.0.10"` (an optional leading `v` is allowed).
         *
         * @throws IllegalArgumentException if [raw] is not a well-formed `MAJOR.MINOR.PATCH`.
         */
        fun parse(raw: String): SemVer {
            val match =
                PATTERN.matchEntire(raw.trim())
                    ?: throw IllegalArgumentException("Malformed semantic version: '$raw'")
            val (major, minor, patch) = match.destructured
            return SemVer(major.toInt(), minor.toInt(), patch.toInt())
        }

        /** Like [parse] but returns `null` instead of throwing on malformed input. */
        fun parseOrNull(raw: String): SemVer? =
            try {
                parse(raw)
            } catch (e: IllegalArgumentException) {
                null
            }
    }
}
