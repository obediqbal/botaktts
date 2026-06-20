package dev.botak.core.update

import org.slf4j.LoggerFactory

/**
 * Orchestrates an update check: compares the running version to the latest GitHub release.
 *
 * @param versionProvider Supplies the running application version.
 * @param releaseClient Fetches the latest published release.
 */
class UpdateService(
    private val versionProvider: VersionProvider,
    private val releaseClient: GitHubReleaseClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(UpdateService::class.java)
    }

    /**
     * Performs the check. Never throws: any error is returned as [UpdateCheckResult.Failed].
     *
     * @return [UpdateCheckResult.UpdateAvailable] when the latest release is newer than the
     *   running version, [UpdateCheckResult.UpToDate] when it is not, or
     *   [UpdateCheckResult.Failed] when the check could not complete.
     */
    fun checkForUpdate(): UpdateCheckResult =
        try {
            val current = versionProvider.currentVersion()
            val release = releaseClient.fetchLatest()
            if (release.version > current) {
                UpdateCheckResult.UpdateAvailable(
                    current = current,
                    latest = release.version,
                    changelog = release.changelog,
                    setupAssetUrl = release.setupAssetUrl,
                    htmlUrl = release.htmlUrl,
                )
            } else {
                UpdateCheckResult.UpToDate(current)
            }
        } catch (e: Exception) {
            LOGGER.warn("Update check failed", e)
            UpdateCheckResult.Failed(e.message ?: "Unknown error", e)
        }
}
