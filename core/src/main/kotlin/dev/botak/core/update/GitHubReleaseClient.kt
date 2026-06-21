package dev.botak.core.update

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads the latest published release from the GitHub Releases API.
 *
 * Uses `/releases/latest`, which returns the most recent published, non-draft, non-prerelease
 * release — exactly the stable build the updater wants. Unauthenticated (GitHub allows 60
 * requests/hour/IP, far above a manual button).
 *
 * @param client The okhttp client to issue requests with.
 * @param baseUrl API base URL; overridable so tests can target a mock server.
 * @param owner Repository owner.
 * @param repo Repository name.
 */
class GitHubReleaseClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.github.com",
    private val owner: String = "obediqbal",
    private val repo: String = "botaktts",
) {
    /** Jackson view of the release JSON; only fields the updater needs are mapped. */
    private data class ReleaseDto(
        val tagName: String? = null,
        val body: String? = null,
        val htmlUrl: String? = null,
        val assets: List<AssetDto> = emptyList(),
    )

    /** Jackson view of a single release asset. */
    private data class AssetDto(
        val name: String? = null,
        val browserDownloadUrl: String? = null,
    )

    private val mapper =
        jacksonObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /**
     * Fetches and parses the latest release.
     *
     * @return the parsed [ReleaseInfo].
     * @throws RuntimeException on a non-2xx response, an empty body, or an unparseable `tag_name`.
     */
    fun fetchLatest(): ReleaseInfo {
        val request =
            Request
                .Builder()
                .url("$baseUrl/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "BotakTTS-Updater")
                .get()
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("GitHub API returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty response body")
            val dto = mapper.readValue<ReleaseDto>(body)

            val tag = dto.tagName ?: throw RuntimeException("Release is missing tag_name")
            val version =
                SemVer.parseOrNull(tag)
                    ?: throw RuntimeException("Unparseable release tag: '$tag'")

            val setupUrl =
                dto.assets
                    .firstOrNull { it.name?.endsWith("-Setup.exe") == true }
                    ?.browserDownloadUrl

            val htmlUrl =
                dto.htmlUrl
                    ?: throw IllegalStateException("Release missing html_url (needed for fallback 'Open release page' action)")

            return ReleaseInfo(
                version = version,
                changelog = dto.body ?: "",
                setupAssetUrl = setupUrl,
                htmlUrl = htmlUrl,
            )
        }
    }
}
