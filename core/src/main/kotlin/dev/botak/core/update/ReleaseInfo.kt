package dev.botak.core.update

/**
 * A GitHub release reduced to only what the updater needs.
 *
 * @property version Version parsed from the release `tag_name`.
 * @property changelog The release `body` (may be blank).
 * @property setupAssetUrl `browser_download_url` of the `*-Setup.exe` asset, or `null` if absent.
 * @property htmlUrl The release page URL, used as a fallback link.
 */
data class ReleaseInfo(
    val version: SemVer,
    val changelog: String,
    val setupAssetUrl: String?,
    val htmlUrl: String,
)
