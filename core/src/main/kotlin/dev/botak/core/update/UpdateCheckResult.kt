package dev.botak.core.update

/** The outcome of an update check. */
sealed interface UpdateCheckResult {
    /**
     * The running version is greater than or equal to the latest release.
     *
     * @property current The running version.
     */
    data class UpToDate(
        val current: SemVer,
    ) : UpdateCheckResult

    /**
     * A newer release exists.
     *
     * @property current The running version.
     * @property latest The newer release version.
     * @property changelog The release notes (may be blank).
     * @property setupAssetUrl Download URL of the `*-Setup.exe` asset, or `null` if absent.
     * @property htmlUrl The release page URL (fallback link).
     */
    data class UpdateAvailable(
        val current: SemVer,
        val latest: SemVer,
        val changelog: String,
        val setupAssetUrl: String?,
        val htmlUrl: String,
    ) : UpdateCheckResult

    /**
     * The check could not be completed (offline, rate-limited, parse error, etc.).
     *
     * @property reason A human-readable summary, safe to log.
     * @property cause The underlying error, if any.
     */
    data class Failed(
        val reason: String,
        val cause: Throwable? = null,
    ) : UpdateCheckResult
}
